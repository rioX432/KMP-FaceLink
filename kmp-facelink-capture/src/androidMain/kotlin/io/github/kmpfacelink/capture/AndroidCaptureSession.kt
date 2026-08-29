package io.github.kmpfacelink.capture

import android.content.Context
import android.os.SystemClock
import android.util.Size
import androidx.camera.core.AspectRatio
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.core.resolutionselector.AspectRatioStrategy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.lifecycle.LifecycleOwner
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.vision.facelandmarker.FaceLandmarkerResult
import io.github.kmpfacelink.model.InferenceRunningMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger

/**
 * Drives camera into MediaPipe into two files, on Android.
 *
 * A participant-facing native [Preview] may be bound alongside the one [ImageAnalysis] use case.
 * It is render-only: no preview frame is read, transformed, or persisted by this class. Analysis
 * remains the only source of pixels for inference and payload storage.
 *
 * The three things this exists to keep straight:
 *
 * 1. **What was fed to inference is what is stored.** The bytes handed to MediaPipe are the bytes
 *    written to the payload, taken from the same bitmap in the same call, so re-inference later is
 *    over the same pixels rather than over a re-encoded approximation.
 * 2. **Results are joined to frames by sequence, not by position.** In LIVE_STREAM the engine
 *    declines to run some admitted frames, so the two streams are different lengths.
 * 3. **The condition travels with the take.** Delegate, running mode, mirroring, native resolution
 *    and model hash are recorded from what actually happened, not from what was requested.
 */
@Suppress(
    // The camera, the model, the running mode, the delegate and the mirroring are the condition
    // being varied, so they are constructor parameters rather than hidden defaults.
    "LongParameterList",
    // A recorder that lets a throw escape the analyzer thread loses the take silently. Every catch
    // here records the failure and stops the take instead.
    "TooGenericExceptionCaught",
    "InstanceOfCheckForException",
    // Dispatchers are used directly: this is a device-only measurement harness with no host tests
    // that need to substitute them.
    "InjectDispatcher",
    "TooManyFunctions",
)
public class AndroidCaptureSession(
    private val context: Context,
    private val lifecycleOwner: LifecycleOwner,
    private val provenance: SourceProvenance,
    private val modelAssetPath: String = DEFAULT_MODEL_ASSET,
    private val requestedAnalysisSize: Size = Size(ANALYSIS_WIDTH, ANALYSIS_HEIGHT),
    private val preferGpu: Boolean = true,
    private val runningMode: InferenceRunningMode = InferenceRunningMode.LIVE_STREAM,
    private val mirror: Boolean = true,
    private val captureWorkflow: CaptureWorkflow = CaptureWorkflow.GUIDED_GATE_AUTOSTART_V1,
) : CaptureSession {

    private val store = CaptureStore(context, modelAssetPath)
    private val transformer = FrameTransformer()
    private val landmarker = StudyLandmarker(context, modelAssetPath)

    /** One thread: frames must reach the payload in submission order. */
    private val analysisExecutor: ExecutorService =
        Executors.newSingleThreadExecutor { runnable -> Thread(runnable, "CaptureAnalyzer") }

    private val _uiState = MutableStateFlow(CaptureUiState())
    override val uiState: StateFlow<CaptureUiState> = _uiState.asStateFlow()
    internal val studyTakeRecords = MutableStateFlow(readAndroidStudyTakeRecords(store.takesRoot))

    private val frameSeq = AtomicInteger(0)
    private val runGuard = CaptureRunGuard()

    /** Frames in flight, by the timestamp they were submitted under. */
    private val inFlight = ConcurrentHashMap<Long, PendingFrame>()

    private var cameraProvider: ProcessCameraProvider? = null

    /** The one display surface for the existing front-camera binding, never a second camera. */
    private val previewSurfaceBinding = PreviewSurfaceBinding()

    /**
     * Bound with [ImageAnalysis] for the lifetime of a live camera session. Changing a Compose
     * surface must never rebind the camera: FaceGuide legitimately enters/leaves composition while
     * a take is starting, and rebinding then could discard or perturb analysis frames.
     */
    private var preview: Preview? = null

    @Volatile private var released = false

    @Volatile private var take: ActiveTake? = null

    @Volatile private var luma = 0f

    @Volatile private var nativeResolution: String? = null

    @Volatile private var aspect = 1f

    @Volatile private var stopRequested = false

    private class PendingFrame(
        val seq: Int,
        val captureTimestampNs: Long,
        val phase: CapturePhase,
        val spanIndex: Int,
    )

    /** One take's mutable state. Guarded by [lock] because results arrive on another thread. */
    @Suppress("LongParameterList") // one take's whole identity, deliberately in one place
    private class ActiveTake(
        val id: String,
        val dir: File,
        val scenario: CaptureScenario,
        val attempt: Int,
        val script: TakeScript,
        val startedAtMs: Long,
        val startedAtElapsedNs: Long,
        val frames: RawFrameSink?,
        val results: InferenceRecordSink,
    ) {
        val lock = Any()
        val evaluator = LiveEvaluator(scenario.softValidation)
        val iods = ArrayList<Float>()
        val spanFirstSeq = HashMap<Int, Int>()
        val spanLastSeq = HashMap<Int, Int>()
        var admitted = 0
        var stored = 0
        var noFace = 0
        var inferenceFailed = 0
        var inferenceErrors = 0
        var blendshapeNames: List<String>? = null
        var recording = true
        var failure: Throwable? = null
    }

    /**
     * Opens the camera and the landmarker.
     *
     * Call once before the first take: opening them per take would put the landmarker's first-frame
     * cost inside the measurement.
     */
    public suspend fun start() {
        check(!released) { "capture session is released" }
        withContext(Dispatchers.IO) {
            landmarker.onError = { message -> countInferenceError(message) }
            landmarker.listener = StudyLandmarker.Listener { result, timestampMs ->
                onResult(result, timestampMs)
            }
            landmarker.open(preferGpu, runningMode)
        }
        val provider = withContext(Dispatchers.IO) {
            ProcessCameraProvider.getInstance(context).get()
        }
        cameraProvider = provider
        withContext(Dispatchers.Main) { bind(provider) }
    }

    /**
     * Stops the live camera and landmarker so they cannot contaminate an offline performance run.
     * The same session can be restored with [resumeAfterOfflineBenchmark].
     */
    public suspend fun pauseForOfflineBenchmark() {
        check(take == null) { "cannot benchmark while a take is recording" }
        withContext(Dispatchers.Main) { cameraProvider?.unbindAll() }
        withContext(Dispatchers.IO) {
            analysisExecutor.submit { landmarker.close() }.get()
        }
        inFlight.clear()
    }

    /** Restores the exact live condition configured for this capture session. */
    public suspend fun resumeAfterOfflineBenchmark() {
        check(take == null) { "cannot resume camera while a take is recording" }
        withContext(Dispatchers.IO) {
            analysisExecutor.submit {
                landmarker.open(preferGpu, runningMode)
            }.get()
        }
        val provider = checkNotNull(cameraProvider) { "camera provider is unavailable" }
        withContext(Dispatchers.Main) { bind(provider) }
    }

    /** Renders the already-bound front-camera stream without changing analysis or its lifecycle. */
    public fun attachPreview(surfaceProvider: Preview.SurfaceProvider) {
        check(!released) { "capture session is released" }
        previewSurfaceBinding.attach(surfaceProvider) { provider -> preview?.setSurfaceProvider(provider) }
    }

    /** Detaches only the render surface; it is safe while a take is recording. */
    public fun detachPreview(surfaceProvider: Preview.SurfaceProvider) {
        if (released) return
        previewSurfaceBinding.detach(surfaceProvider) { provider -> preview?.setSurfaceProvider(provider) }
    }

    private fun bind(provider: ProcessCameraProvider) {
        provider.unbindAll()
        val analysis = ImageAnalysis.Builder()
            .setResolutionSelector(
                ResolutionSelector.Builder()
                    .setAspectRatioStrategy(
                        AspectRatioStrategy(AspectRatio.RATIO_4_3, AspectRatioStrategy.FALLBACK_RULE_AUTO),
                    )
                    .setResolutionStrategy(
                        ResolutionStrategy(
                            requestedAnalysisSize,
                            ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER,
                        ),
                    )
                    .build(),
            )
            // Keeping only the latest frame is what the product does, and the frames it drops are
            // part of what is being measured. Queueing them here would measure a pipeline nobody
            // ships.
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
            .build()
        analysis.setAnalyzer(analysisExecutor, ::onFrame)
        // Front camera only: the product tracks faces with the front camera, and an earlier study
        // was recorded on the rear one and could not be compared to it.
        val boundPreview = Preview.Builder().build().also { preview ->
            preview.setSurfaceProvider(previewSurfaceBinding.current)
        }
        preview = boundPreview
        provider.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_FRONT_CAMERA, analysis, boundPreview)
    }

    override suspend fun record(scenario: CaptureScenario, saveFrames: Boolean): TakeOutcome {
        check(runGuard.tryEnter()) { "a take is already running or waiting for the hard gate" }
        try {
            return recordGuarded(scenario, saveFrames)
        } finally {
            _uiState.value = _uiState.value.copy(
                recording = false,
                progress = LiveProgress.None,
                scriptElapsedMs = null,
            )
            runGuard.leave()
        }
    }

    private suspend fun recordGuarded(scenario: CaptureScenario, saveFrames: Boolean): TakeOutcome {
        check(take == null) { "a take is already running" }
        stopRequested = false
        if (!awaitGate()) return cancelledOutcome(scenario)
        val attempt = nextAttempt(scenario)
        val startedAtMs = System.currentTimeMillis()
        val id = store.newTakeId(scenario, attempt, startedAtMs)
        val dir = store.createTmpDir(id)
        val geometry = requireNotNull(nativeResolution) { "no frame has arrived yet" }
        val (width, height) = geometry.split("x").map { it.toInt() }
        val active = ActiveTake(
            id = id,
            dir = dir,
            scenario = scenario,
            attempt = attempt,
            script = TakeScript(scenario),
            startedAtMs = startedAtMs,
            startedAtElapsedNs = SystemClock.elapsedRealtimeNanos(),
            frames = if (saveFrames) {
                openRawFrameSink(File(dir, FRAMES_DIR).apply { mkdirs() }.absolutePath, width, height)
            } else {
                null
            },
            results = openInferenceRecordSink(dir.absolutePath),
        )
        take = active
        _uiState.value = _uiState.value.copy(
            recording = true,
            progress = active.evaluator.progress(),
            scriptElapsedMs = 0L,
        )
        val end = runScript(active)
        _uiState.value = _uiState.value.copy(recording = false, scriptElapsedMs = null)
        return withContext(Dispatchers.IO) { finish(active, end) }
    }

    /** Steps through the script without ever moving a registered boundary. */
    private suspend fun runScript(active: ActiveTake): ScriptEnd {
        while (true) {
            val elapsed = elapsedMs(active)
            _uiState.value = _uiState.value.copy(scriptElapsedMs = elapsed)
            if (elapsed >= active.script.totalMs) return ScriptEnd.COMPLETED
            if (stopRequested) return ScriptEnd.STOPPED
            if (active.scenario.goalDriven && synchronized(active.lock) { active.evaluator.isGoalReached() }) {
                return ScriptEnd.COMPLETED
            }
            synchronized(active.lock) { active.failure }?.let { return ScriptEnd.FAILED }
            delay(TICK_MS)
        }
    }

    private suspend fun awaitGate(): Boolean {
        val deadline = SystemClock.elapsedRealtime() + GATE_TIMEOUT_MS
        while (!_uiState.value.gate.passed) {
            if (stopRequested) return false
            check(SystemClock.elapsedRealtime() < deadline) {
                "the hard gate never passed: ${_uiState.value.gate}. Distance, framing and light " +
                    "have to be in band before a take starts, or the takes are not comparable."
            }
            delay(TICK_MS)
        }
        return !stopRequested
    }

    private fun cancelledOutcome(scenario: CaptureScenario): TakeOutcome = TakeOutcome(
        scenario = scenario,
        recordCount = 0,
        softValidationPassed = false,
        reason = "take_cancelled",
    )

    private fun elapsedMs(active: ActiveTake): Long =
        (SystemClock.elapsedRealtimeNanos() - active.startedAtElapsedNs) / NS_PER_MS

    @Suppress("CyclomaticComplexMethod") // one pass over a frame: admit, store, submit
    private fun onFrame(proxy: ImageProxy) {
        val captureNs = proxy.imageInfo.timestamp
        val seq = frameSeq.getAndIncrement()
        val active = take
        val elapsed = active?.let { elapsedMs(it) }
        // The span is decided here, on the frame's own clock reading. Looking it up later, when the
        // result comes back, attributes the frame to whatever span the take had moved on to.
        val spanIndex = if (active != null && elapsed != null) {
            active.script.spans.indexOfFirst { elapsed < it.endMs }
        } else {
            -1
        }
        val phase = if (spanIndex >= 0 && active != null) active.script.spans[spanIndex].phase else null
        val recording = active != null && phase != null && synchronized(active.lock) { active.recording }

        val out = try {
            transformer.transform(proxy, mirror)
        } catch (t: Throwable) {
            if (t is Error) throw t
            active?.let { fail(it, t) }
            runCatching { proxy.close() }
            return
        }
        luma = out.luma
        nativeResolution = "${out.width}x${out.height}"
        aspect = if (out.width == 0) 1f else out.height.toFloat() / out.width

        // MediaPipe wants a monotonic millisecond stamp, and it is also the only handle a
        // LIVE_STREAM result comes back with, so it doubles as the lookup key.
        val inferenceMs = captureNs / NS_PER_MS

        if (recording) {
            checkNotNull(active)
            checkNotNull(phase)
            val admittedSeq = synchronized(active.lock) {
                if (!active.recording) return@synchronized -1
                active.admitted++
                seq
            }
            if (admittedSeq >= 0) {
                storeFrame(active, out, admittedSeq, captureNs, inferenceMs, phase)
                inFlight[inferenceMs] = PendingFrame(admittedSeq, captureNs, phase, spanIndex)
            }
        }

        try {
            val image = BitmapImageBuilder(out.bitmap).build()
            val direct = landmarker.submit(image, inferenceMs)
            // VIDEO and IMAGE answer inline; LIVE_STREAM comes back through the listener.
            direct?.let { onResult(it, inferenceMs) }
        } catch (t: Throwable) {
            if (t is Error) throw t
            active?.let { fail(it, t) }
        }
    }

    @Suppress("LongParameterList") // the frame's identity travels with its pixels
    private fun storeFrame(
        active: ActiveTake,
        out: FrameTransformer.Output,
        seq: Int,
        captureNs: Long,
        inferenceMs: Long,
        phase: CapturePhase,
    ) {
        val sink = active.frames ?: return
        try {
            sink.write(
                RawFrame(
                    width = out.width,
                    height = out.height,
                    analysisFrameSeq = seq,
                    captureTimestampNs = captureNs,
                    inferenceTimestampMs = inferenceMs,
                    phase = phase,
                    pixels = transformer.rgbaOf(out.bitmap),
                ),
            )
            synchronized(active.lock) { active.stored++ }
        } catch (t: Throwable) {
            if (t is Error) throw t
            // A frame that could not be stored means the payload no longer covers every admitted
            // frame, which is the one thing the recording promises.
            fail(active, t)
        }
    }

    @Suppress("CyclomaticComplexMethod") // one pass over a result: classify, join, record
    private fun onResult(result: FaceLandmarkerResult, inferenceTimestampMs: Long) {
        val resultElapsedNs = SystemClock.elapsedRealtimeNanos()
        val faceDetected = RawResult.hasFace(result)
        val landmarks = RawResult.landmarks(result)
        val hasFace = landmarks != null
        // A face was found but the point count was wrong: the estimator failed, the subject did not.
        val malformed = faceDetected && landmarks == null
        val iod = landmarks?.let { FaceMetrics.iod(it, aspect) } ?: 0f

        _uiState.value = _uiState.value.let { current ->
            current.copy(
                gate = HardGate.evaluate(landmarks?.let { HardGate.boundingBox(it) }, luma, iod),
                distanceHint = HardGate.distanceHint(iod, hasFace),
                landmarks = landmarks,
                aspect = aspect,
                resultsObserved = current.resultsObserved + 1,
            )
        }

        val pending = inFlight.remove(inferenceTimestampMs)
        // Frames the engine declined to run stay in flight forever otherwise. They are still in the
        // payload, which is the point: they shaped the scheduling that decided which frames ran.
        inFlight.keys.removeAll { it < inferenceTimestampMs }
        val active = take ?: return
        if (pending == null) return

        synchronized(active.lock) {
            if (!active.recording) return
            active.blendshapeNames = active.blendshapeNames ?: RawResult.blendshapeNames(result)
            try {
                active.results.write(
                    InferenceRecord(
                        analysisFrameSeq = pending.seq,
                        captureTimestampNs = pending.captureTimestampNs,
                        resultElapsedNs = resultElapsedNs,
                        phase = pending.phase,
                        hasFace = hasFace,
                        inferenceFailed = malformed,
                        landmarks = landmarks,
                        blendshapes = if (hasFace) RawResult.blendshapes(result) else null,
                        transform = if (hasFace) RawResult.transform(result) else null,
                    ),
                )
            } catch (t: Throwable) {
                if (t is Error) throw t
                failLocked(active, t)
                return
            }
            if (!hasFace) active.noFace++
            if (malformed) active.inferenceFailed++
            recordSpan(active, pending)
            if (landmarks != null) collect(active, pending, landmarks, result)
        }
    }

    /** First and last sequence per span, so the analysis does not infer the windows from clocks. */
    private fun recordSpan(active: ActiveTake, pending: PendingFrame) {
        if (pending.spanIndex < 0) return
        active.spanFirstSeq.putIfAbsent(pending.spanIndex, pending.seq)
        active.spanLastSeq[pending.spanIndex] = pending.seq
    }

    private fun collect(
        active: ActiveTake,
        pending: PendingFrame,
        landmarks: FloatArray,
        result: FaceLandmarkerResult,
    ) {
        val transform = RawResult.transform(result)
        active.iods.add(FaceMetrics.iod(landmarks, aspect))
        active.evaluator.onFrame(
            ear = FaceMetrics.earAverage(landmarks, aspect),
            mouthRatio = FaceMetrics.mouthWidthOverIod(landmarks, aspect),
            frontal = HeadPose.isFrontal(transform),
            neutralPhase = pending.phase == CapturePhase.NEUTRAL,
            yawSin = HeadPose.yawSin(transform),
            pitchSin = HeadPose.pitchSin(transform),
            captureTimestampNs = pending.captureTimestampNs,
        )
        _uiState.value = _uiState.value.copy(progress = active.evaluator.progress())
    }

    private fun countInferenceError(message: String) {
        val active = take ?: return
        synchronized(active.lock) { if (active.recording) active.inferenceErrors++ }
        _uiState.value = _uiState.value.copy(lastError = message)
    }

    private fun fail(active: ActiveTake, cause: Throwable) =
        synchronized(active.lock) { failLocked(active, cause) }

    private fun failLocked(active: ActiveTake, cause: Throwable) {
        if (active.failure == null) active.failure = cause
        active.recording = false
    }

    /**
     * Closes both sinks, writes the manifest, and publishes only if everything held.
     *
     * A take that lost a frame or a record is left in `tmp/` without a manifest. That is the whole
     * point of the two-step: nothing that lists the takes directory can find a partial one.
     */
    private fun finish(active: ActiveTake, end: ScriptEnd): TakeOutcome {
        synchronized(active.lock) { active.recording = false }
        // Give the frames still inside MediaPipe a moment to come back before the sinks close.
        SystemClock.sleep(DRAIN_MS)
        take = null
        inFlight.clear()

        val validation = synchronized(active.lock) { active.evaluator.outcome() }
        val closeFailure = runCatching { active.results.close() }.exceptionOrNull()
        val frameFailure = active.frames?.let { runCatching { it.close() }.exceptionOrNull() }
        val failure = synchronized(active.lock) { active.failure } ?: closeFailure ?: frameFailure

        if (!shouldPublishTake(end, failure != null)) {
            store.discard(active.dir)
            return TakeOutcome(
                scenario = active.scenario,
                recordCount = active.results.recordCount,
                softValidationPassed = false,
                reason = if (end == ScriptEnd.STOPPED) "take_cancelled" else "take_failed",
                error = failure?.let { it.message ?: it::class.simpleName },
            )
        }

        val manifest = manifestFor(active, validation)
        File(active.dir, InferenceRecordFormat.TAKE_MANIFEST_FILE)
            .writeText(rawFrameJson.encodeToString(manifest))
        store.publish(active.dir, active.id)
        studyTakeRecords.value = withPublishedStudyTake(
            studyTakeRecords.value,
            StudyTakeRecord(active.id, active.scenario, active.attempt),
        )

        return TakeOutcome(
            scenario = active.scenario,
            recordCount = manifest.inferenceResults,
            softValidationPassed = validation.passed,
            reason = validation.reason,
        )
    }

    /** What the take says about itself, read under the lock the results were counted under. */
    private fun manifestFor(active: ActiveTake, validation: LiveEvaluator.Outcome): TakeManifest {
        val iods = synchronized(active.lock) { active.iods.toList() }
        return synchronized(active.lock) {
            TakeManifest(
                schemaVersion = TakeManifest.SCHEMA_VERSION,
                takeId = active.id,
                scenarioId = active.scenario.id,
                attempt = active.attempt,
                startedAtIso = isoTimestamp(active.startedAtMs),
                condition = condition(),
                analysisFramesAdmitted = active.admitted,
                inferenceResults = active.results.recordCount,
                noFaceResults = active.noFace,
                inferenceFailedResults = active.inferenceFailed,
                inferenceErrors = active.inferenceErrors,
                framesStored = active.stored,
                softValidationPassed = validation.passed,
                softValidationReason = validation.reason,
                medianIod = median(iods),
                outOfDistanceBandFraction = outOfBand(iods),
                spans = spans(active),
                blendshapeNames = active.blendshapeNames ?: emptyList(),
                resultIndexSha256 = "",
                landmarksSha256 = "",
                blendshapesSha256 = "",
                transformSha256 = "",
                captureWorkflow = captureWorkflow,
            )
        }.withHashes(active.results.hashes())
    }

    private fun TakeManifest.withHashes(hashes: List<String>): TakeManifest = copy(
        resultIndexSha256 = hashes[0],
        landmarksSha256 = hashes[1],
        blendshapesSha256 = hashes[2],
        transformSha256 = hashes[3],
    )

    private fun spans(active: ActiveTake): List<TakeSpan> =
        active.script.spans.mapIndexed { index, span ->
            TakeSpan(
                cue = span.cue.name,
                phaseCode = span.phase.code,
                startMs = span.startMs,
                endMs = span.endMs,
                firstFrameSeq = active.spanFirstSeq[index] ?: -1,
                lastFrameSeq = active.spanLastSeq[index] ?: -1,
            )
        }

    private fun condition(): CaptureCondition = CaptureCondition(
        // What the camera delivered, not what was asked for: a take recorded at 240x320 when
        // 320x240 was requested is the normal case, and recording the request is misleading.
        nativeResolution = nativeResolution ?: "unknown",
        runningMode = runningMode.name,
        delegate = landmarker.delegate,
        numFaces = StudyLandmarker.NUM_FACES,
        minDetectionConfidence = StudyLandmarker.MIN_DETECTION_CONFIDENCE,
        minTrackingConfidence = StudyLandmarker.MIN_TRACKING_CONFIDENCE,
        minPresenceConfidence = StudyLandmarker.MIN_PRESENCE_CONFIDENCE,
        mediapipeVersion = MEDIAPIPE_VERSION,
        modelSha256 = store.modelSha256,
        mirrored = mirror,
        participantPreview = ParticipantPreview.NATIVE_CAMERA_WITH_GUIDE,
        layer = MeasurementLayer.DEVICE_REALTIME,
        provenance = provenance,
    )

    private fun nextAttempt(scenario: CaptureScenario): Int =
        nextStudyAttempt(studyTakeRecords.value, scenario)

    private fun median(values: List<Float>): Float =
        if (values.isEmpty()) 0f else values.sorted()[values.size / 2]

    private fun outOfBand(values: List<Float>): Float =
        if (values.isEmpty()) 0f else values.count { !HardGate.inBand(it) }.toFloat() / values.size

    override fun stop() {
        stopRequested = true
    }

    override fun release() {
        released = true
        take?.let { active ->
            synchronized(active.lock) { active.recording = false }
            runCatching { active.results.close() }
            runCatching { active.frames?.close() }
            store.discard(active.dir)
            take = null
        }
        cameraProvider?.unbindAll()
        previewSurfaceBinding.clear()
        preview = null
        analysisExecutor.execute { landmarker.close() }
        analysisExecutor.shutdown()
    }

    /** Where published takes are, for the verification step and for pulling them off the device. */
    public val takesDirectory: File get() = store.takesRoot

    internal fun removeStudyTake(takeId: String) {
        studyTakeRecords.value = withoutStudyTake(studyTakeRecords.value, takeId)
    }

    public companion object {
        public const val DEFAULT_MODEL_ASSET: String = "models/face_landmarker_v2_with_blendshapes.task"
        public const val FRAMES_DIR: String = "frames"

        // The product's analysis request. Recorded per take as actual-versus-requested.
        private const val ANALYSIS_WIDTH = 320
        private const val ANALYSIS_HEIGHT = 240
        private const val MEDIAPIPE_VERSION = "0.10.35"

        private const val NS_PER_MS = 1_000_000L
        private const val TICK_MS = 33L
        private const val GATE_TIMEOUT_MS = 60_000L

        // Long enough for results already inside MediaPipe to come back, so the last frames of a
        // take are not reported as dropped by the shutdown rather than by the engine.
        private const val DRAIN_MS = 400L
    }
}
