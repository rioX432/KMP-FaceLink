@file:OptIn(ExperimentalForeignApi::class)

package io.github.kmpfacelink.capture

import io.github.kmpfacelink.model.LiveLinkBlendShapes
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.FloatVar
import kotlinx.cinterop.ObjCSignatureOverride
import kotlinx.cinterop.get
import kotlinx.cinterop.ptr
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.useContents
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import platform.ARKit.ARFaceAnchor
import platform.ARKit.ARFaceTrackingConfiguration
import platform.ARKit.ARFrame
import platform.ARKit.ARSession
import platform.ARKit.ARSessionDelegateProtocol
import platform.Foundation.NSError
import platform.Foundation.NSLock
import platform.Foundation.NSNumber
import platform.Foundation.NSProcessInfo
import platform.darwin.NSObject
import platform.darwin.dispatch_queue_create
import platform.darwin.dispatch_sync
import kotlin.math.absoluteValue

private const val ARKIT_PROVIDER_SHA256 = "2222770b59580b1925bec9cafae56c306ee8cc989867841ffbb8aeeb83a82ca4"

/**
 * Creates a capture session through a Swift-visible throwing boundary.
 *
 * [IosCaptureSession] prepares its private storage during construction. Kotlin exceptions from a
 * constructor cannot be represented as Swift errors, so Swift hosts should use this factory and
 * present any setup failure instead of allowing it to terminate the process.
 */
public object IosCaptureSessionFactory {
    @Throws(Exception::class)
    public fun create(
        rootDirectory: String,
        provenance: SourceProvenance,
        captureWorkflow: CaptureWorkflow = CaptureWorkflow.GUIDED_GATE_AUTOSTART_V1,
    ): IosCaptureSession = IosCaptureSession(rootDirectory, provenance, captureWorkflow)
}

/** Records ARKit camera frames and raw face coefficients into the shared take format. */
@Suppress(
    "TooManyFunctions",
    "TooGenericExceptionCaught",
    "InstanceOfCheckForException",
    "InjectDispatcher",
)
public class IosCaptureSession(
    rootDirectory: String,
    private val provenance: SourceProvenance,
    private val captureWorkflow: CaptureWorkflow = CaptureWorkflow.GUIDED_GATE_AUTOSTART_V1,
) : CaptureSession {
    private val store = IosCaptureStore(rootDirectory)
    private val converter = IosFrameConverter()
    private val arSession = ARSession()
    private val delegate = SessionDelegate()
    private val delegateQueue = dispatch_queue_create("io.github.kmpfacelink.capture.arkit", null)
    private val _uiState = MutableStateFlow(CaptureUiState())
    override val uiState: StateFlow<CaptureUiState> = _uiState.asStateFlow()
    internal val studyTakeRecords = MutableStateFlow(readIosStudyTakeRecords(store.takesRoot))

    private var take: ActiveTake? = null
    private var frameSequence = 0
    private var nativeWidth = 0
    private var nativeHeight = 0
    private var stopRequested = false
    private var released = false
    private var arSessionLease: IosArSessionLease? = null
    private val runGuard = CaptureRunGuard()

    /**
     * The one ARSession owned by this capture session for a display-only participant preview.
     * Callers may attach it to an ARSCNView but must not run, pause, delegate, or otherwise own it.
     */
    internal fun previewArSession(): ARSession = arSession

    @Suppress("LongParameterList")
    private class ActiveTake(
        val id: String,
        val directory: String,
        val scenario: CaptureScenario,
        val attempt: Int,
        val script: TakeScript,
        val startedAtMs: Long,
        val startedAtNs: Long,
        val frames: RawFrameSink?,
        val results: InferenceRecordSink,
    ) {
        val lock = NSLock()
        val evaluator = LiveEvaluator(scenario.softValidation)
        val iods = ArrayList<Float>()
        val spanFirstSeq = mutableMapOf<Int, Int>()
        val spanLastSeq = mutableMapOf<Int, Int>()
        var admitted = 0
        var stored = 0
        var noFace = 0
        var recording = true
        var failure: Throwable? = null
    }

    /** Opens one ARKit session and keeps warm-up outside every measured take. */
    @Throws(IllegalStateException::class)
    public fun start() {
        check(!released) { "capture session is released" }
        if (arSessionLease != null) return
        check(ARFaceTrackingConfiguration.isSupported) {
            "ARKit face tracking is not supported on this device"
        }
        val lease = IosArSessionLeaseRegistry.acquire("guided capture")
        try {
            arSession.delegate = delegate
            arSession.delegateQueue = delegateQueue
            val configuration = ARFaceTrackingConfiguration().apply {
                lightEstimationEnabled = true
                maximumNumberOfTrackedFaces = 1L
            }
            arSession.runWithConfiguration(configuration)
            arSessionLease = lease
        } catch (failure: Throwable) {
            lease.release()
            throw failure
        }
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
        check(!released) { "capture session is released" }
        stopRequested = false
        if (!awaitGate()) return cancelledOutcome(scenario)
        val attempt = nextStudyAttempt(studyTakeRecords.value, scenario)
        val startedAtMs = captureCurrentTimeMillis()
        val id = store.newTakeId(scenario, attempt, startedAtMs)
        val directory = store.createTmpDir(id)
        check(nativeWidth > 0 && nativeHeight > 0) { "no ARKit camera frame has arrived yet" }
        val active = ActiveTake(
            id = id,
            directory = directory,
            scenario = scenario,
            attempt = attempt,
            script = TakeScript(scenario),
            startedAtMs = startedAtMs,
            startedAtNs = monotonicNanos(),
            frames = if (saveFrames) {
                openRawFrameSink(
                    ensureCaptureDirectory(joinCapturePath(directory, FRAMES_DIR)),
                    nativeWidth,
                    nativeHeight,
                )
            } else {
                null
            },
            results = openInferenceRecordSink(directory),
        )
        take = active
        _uiState.value = _uiState.value.copy(
            recording = true,
            progress = active.evaluator.progress(),
            scriptElapsedMs = 0L,
        )
        val end = runScript(active)
        _uiState.value = _uiState.value.copy(recording = false, scriptElapsedMs = null)
        return withContext(Dispatchers.Default) { finish(active, end) }
    }

    private suspend fun runScript(active: ActiveTake): ScriptEnd {
        while (true) {
            val elapsed = elapsedMs(active)
            _uiState.value = _uiState.value.copy(scriptElapsedMs = elapsed)
            if (elapsed >= active.script.totalMs) return ScriptEnd.COMPLETED
            if (stopRequested) return ScriptEnd.STOPPED
            if (active.scenario.goalDriven && active.lock.withCaptureLock { active.evaluator.isGoalReached() }) {
                return ScriptEnd.COMPLETED
            }
            if (active.lock.withCaptureLock { active.failure != null }) return ScriptEnd.FAILED
            delay(TICK_MS)
        }
    }

    private suspend fun awaitGate(): Boolean {
        val deadline = monotonicNanos() + GATE_TIMEOUT_NS
        while (!_uiState.value.gate.passed) {
            if (stopRequested) return false
            check(monotonicNanos() < deadline) {
                "the hard gate never passed: ${_uiState.value.gate}"
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

    @Suppress("LongMethod", "CyclomaticComplexMethod")
    private fun onFrame(frame: ARFrame) {
        val sequence = frameSequence++
        val pixels = requireNotNull(frame.capturedImage) { "ARKit frame has no captured image" }
        val geometry = converter.geometry(pixels)
        nativeWidth = geometry.width
        nativeHeight = geometry.height
        val luma = converter.averageLuma(pixels)
        val face = (frame.anchors.firstOrNull { it is ARFaceAnchor } as? ARFaceAnchor)
            ?.takeIf { it.isTracked }
        val transform = face?.let(::transformOf)
        val blendshapes = face?.let(::blendshapesOf)
        val metrics = gateMetrics(transform)
        val gate = HardGate.evaluate(metrics?.box, luma, metrics?.iod ?: 0f)
        _uiState.value = _uiState.value.let { current ->
            current.copy(
                gate = gate,
                distanceHint = HardGate.distanceHint(metrics?.iod ?: 0f, face != null),
                // ARKit's native face topology is not MediaPipe's 478-point contract. The
                // participant preview renders native ARSCNFaceGeometry separately; recordings do
                // not relabel it as common landmarks.
                landmarks = null,
                aspect = geometry.height.toFloat() / geometry.width,
                resultsObserved = current.resultsObserved + 1,
            )
        }

        val active = take ?: return
        val elapsed = elapsedMs(active)
        val spanIndex = active.script.spans.indexOfFirst { elapsed < it.endMs }
        if (spanIndex < 0 || !active.lock.withCaptureLock { active.recording }) return
        val phase = active.script.spans[spanIndex].phase
        val captureNs = (frame.timestamp * NS_PER_SECOND).toLong()
        try {
            active.frames?.write(
                RawFrame(
                    width = geometry.width,
                    height = geometry.height,
                    analysisFrameSeq = sequence,
                    captureTimestampNs = captureNs,
                    inferenceTimestampMs = captureNs / NS_PER_MILLISECOND,
                    phase = phase,
                    pixels = converter.rgba(pixels).pixels,
                ),
            )
            active.results.write(
                InferenceRecord(
                    analysisFrameSeq = sequence,
                    captureTimestampNs = captureNs,
                    resultElapsedNs = monotonicNanos(),
                    phase = phase,
                    hasFace = face != null,
                    inferenceFailed = false,
                    landmarks = null,
                    blendshapes = blendshapes,
                    transform = transform,
                ),
            )
            collect(active, spanIndex, sequence, captureNs, phase, metrics, blendshapes, transform)
        } catch (t: Throwable) {
            if (t is Error) throw t
            active.lock.withCaptureLock {
                if (active.failure == null) active.failure = t
                active.recording = false
            }
            _uiState.value = _uiState.value.copy(lastError = t.message ?: t::class.simpleName)
        }
    }

    @Suppress("LongParameterList")
    private fun collect(
        active: ActiveTake,
        spanIndex: Int,
        sequence: Int,
        captureNs: Long,
        phase: CapturePhase,
        metrics: GateMetrics?,
        blendshapes: FloatArray?,
        transform: FloatArray?,
    ) {
        active.lock.withCaptureLock {
            active.admitted++
            if (active.frames != null) active.stored++
            if (blendshapes == null) active.noFace++
            if (spanIndex !in active.spanFirstSeq) active.spanFirstSeq[spanIndex] = sequence
            active.spanLastSeq[spanIndex] = sequence
            metrics?.let { active.iods.add(it.iod) }
            if (blendshapes != null && transform != null) {
                active.evaluator.onFrame(
                    ear = 1f - (blendshapes[EYE_BLINK_LEFT] + blendshapes[EYE_BLINK_RIGHT]) / 2f,
                    mouthRatio = 1f + (blendshapes[MOUTH_SMILE_LEFT] + blendshapes[MOUTH_SMILE_RIGHT]) * SMILE_SCALE,
                    frontal = HeadPose.isFrontal(transform),
                    neutralPhase = phase == CapturePhase.NEUTRAL,
                    yawSin = HeadPose.yawSin(transform),
                    pitchSin = HeadPose.pitchSin(transform),
                    captureTimestampNs = captureNs,
                )
                _uiState.value = _uiState.value.copy(progress = active.evaluator.progress())
            }
        }
    }

    private fun finish(active: ActiveTake, end: ScriptEnd): TakeOutcome {
        active.lock.withCaptureLock { active.recording = false }
        // A frame may already have passed the recording check on ARKit's delegate queue. Draining
        // that queue before closing the sinks keeps every successful write in the take.
        dispatch_sync(delegateQueue) {}
        take = null
        val validation = active.lock.withCaptureLock { active.evaluator.outcome() }
        val resultFailure = runCatching { active.results.close() }.exceptionOrNull()
        val frameFailure = active.frames?.let { runCatching { it.close() }.exceptionOrNull() }
        val failure = active.lock.withCaptureLock { active.failure } ?: resultFailure ?: frameFailure
        if (!shouldPublishTake(end, failure != null)) {
            store.discard(active.directory)
            return TakeOutcome(
                scenario = active.scenario,
                recordCount = active.results.recordCount,
                softValidationPassed = false,
                reason = if (end == ScriptEnd.STOPPED) "take_cancelled" else "take_failed",
                error = failure?.let { it.message ?: it::class.simpleName },
            )
        }
        val hashes = active.results.hashes()
        val manifest = manifestFor(active, validation, hashes)
        publishCaptureText(
            active.directory,
            InferenceRecordFormat.TAKE_MANIFEST_FILE,
            rawFrameJson.encodeToString(manifest),
        )
        store.publish(active.directory, active.id)
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

    private fun manifestFor(
        active: ActiveTake,
        validation: LiveEvaluator.Outcome,
        hashes: List<String>,
    ): TakeManifest = active.lock.withCaptureLock {
        TakeManifest(
            schemaVersion = TakeManifest.SCHEMA_VERSION,
            takeId = active.id,
            scenarioId = active.scenario.id,
            attempt = active.attempt,
            startedAtIso = captureIsoTimestamp(active.startedAtMs),
            condition = condition(),
            analysisFramesAdmitted = active.admitted,
            inferenceResults = active.results.recordCount,
            noFaceResults = active.noFace,
            inferenceFailedResults = 0,
            inferenceErrors = if (active.failure == null) 0 else 1,
            framesStored = active.stored,
            softValidationPassed = validation.passed,
            softValidationReason = validation.reason,
            medianIod = median(active.iods),
            outOfDistanceBandFraction = outOfBand(active.iods),
            spans = active.script.spans.mapIndexed { index, span ->
                TakeSpan(
                    cue = span.cue.name,
                    phaseCode = span.phase.code,
                    startMs = span.startMs,
                    endMs = span.endMs,
                    firstFrameSeq = active.spanFirstSeq[index] ?: -1,
                    lastFrameSeq = active.spanLastSeq[index] ?: -1,
                )
            },
            blendshapeNames = LiveLinkBlendShapes.names,
            resultIndexSha256 = hashes[0],
            landmarksSha256 = hashes[1],
            blendshapesSha256 = hashes[2],
            transformSha256 = hashes[3],
            captureWorkflow = captureWorkflow,
        )
    }

    private fun condition(): CaptureCondition = iosGuidedCaptureCondition(
        nativeWidth = nativeWidth,
        nativeHeight = nativeHeight,
        provenance = provenance,
    )

    @Suppress("UNCHECKED_CAST")
    private fun blendshapesOf(anchor: ARFaceAnchor): FloatArray {
        val raw = anchor.blendShapes as? Map<String, NSNumber> ?: emptyMap()
        val normalized = raw.entries.associate { normalizeArKitName(it.key) to it.value.floatValue }
        return FloatArray(LiveLinkBlendShapes.names.size) { index ->
            normalized[LiveLinkBlendShapes.names[index]] ?: 0f
        }
    }

    private fun normalizeArKitName(name: String): String = when {
        name.endsWith("_L") -> name.dropLast(2) + "Left"
        name.endsWith("_R") -> name.dropLast(2) + "Right"
        else -> name
    }

    private fun transformOf(anchor: ARFaceAnchor): FloatArray = FloatArray(InferenceRecordFormat.TRANSFORM_FLOATS).also { out ->
        anchor.transform.useContents {
            val values = ptr.reinterpret<FloatVar>()
            for (index in out.indices) out[index] = values[index]
        }
    }

    /** Converts ARKit's metric face translation into the normalized inputs expected by HardGate. */
    private fun gateMetrics(transform: FloatArray?): GateMetrics? {
        transform ?: return null
        val distance = transform[14].absoluteValue
        if (distance <= 0f) return null
        val scale = TARGET_DISTANCE_METERS / distance
        val centerX = SCREEN_CENTER + transform[12] / (distance * HORIZONTAL_NORMALIZER)
        val centerY = SCREEN_CENTER - transform[13] / (distance * VERTICAL_NORMALIZER)
        return GateMetrics(
            box = HardGate.FaceBox(centerX, centerY, FACE_WIDTH * scale, FACE_HEIGHT * scale),
            iod = TARGET_IOD * scale,
        )
    }

    private fun elapsedMs(active: ActiveTake): Long =
        (monotonicNanos() - active.startedAtNs) / NS_PER_MILLISECOND

    private fun monotonicNanos(): Long =
        (NSProcessInfo.processInfo.systemUptime * NS_PER_SECOND).toLong()

    private fun median(values: List<Float>): Float =
        if (values.isEmpty()) 0f else values.sorted()[values.size / 2]

    private fun outOfBand(values: List<Float>): Float =
        if (values.isEmpty()) 0f else values.count { !HardGate.inBand(it) }.toFloat() / values.size

    override fun stop() {
        stopRequested = true
    }

    override fun release() {
        released = true
        stopRequested = true
        take?.let { active ->
            active.lock.withCaptureLock { active.recording = false }
            runCatching { active.results.close() }
            runCatching { active.frames?.close() }
            store.discard(active.directory)
            take = null
        }
        arSession.pause()
        arSession.delegate = null
        arSessionLease?.release()
        arSessionLease = null
    }

    /** Directory containing only fully published takes. */
    public val takesDirectory: String get() = store.takesRoot

    internal fun removeStudyTake(takeId: String) {
        studyTakeRecords.value = withoutStudyTake(studyTakeRecords.value, takeId)
    }

    private inner class SessionDelegate : NSObject(), ARSessionDelegateProtocol {
        @ObjCSignatureOverride
        override fun session(session: ARSession, didUpdateFrame: ARFrame) {
            // A Kotlin exception is not allowed to escape an Objective-C delegate callback: doing
            // so terminates the process before SwiftUI can present a useful failure. Treat a bad
            // frame/conversion as a capture-session failure and stop the callback stream instead.
            try {
                onFrame(didUpdateFrame)
            } catch (failure: Throwable) {
                failSession(
                    "ARKit frame processing failed: ${failure.message ?: failure::class.simpleName}",
                    failure,
                )
            }
        }

        @ObjCSignatureOverride
        override fun session(session: ARSession, didFailWithError: NSError) {
            val message = didFailWithError.localizedDescription
            failSession(message, IllegalStateException(message))
        }
    }

    private fun failSession(message: String, failure: Throwable) {
        _uiState.value = _uiState.value.copy(lastError = message)
        take?.let { active ->
            active.lock.withCaptureLock {
                if (active.failure == null) active.failure = failure
                active.recording = false
            }
        }
        arSession.pause()
    }

    private data class GateMetrics(val box: HardGate.FaceBox, val iod: Float)

    private companion object {
        const val FRAMES_DIR = "frames"
        const val TICK_MS = 33L
        const val NS_PER_MILLISECOND = 1_000_000L
        const val NS_PER_SECOND = 1_000_000_000.0
        const val GATE_TIMEOUT_NS = 60_000_000_000L
        const val TARGET_DISTANCE_METERS = 0.45f
        const val TARGET_IOD = 0.33f
        const val SCREEN_CENTER = 0.5f
        const val FACE_WIDTH = 0.45f
        const val FACE_HEIGHT = 0.55f
        const val HORIZONTAL_NORMALIZER = 1.4f
        const val VERTICAL_NORMALIZER = 1.8f
        const val SMILE_SCALE = 0.2f
        const val EYE_BLINK_LEFT = 0
        const val EYE_BLINK_RIGHT = 7
        const val MOUTH_SMILE_LEFT = 23
        const val MOUTH_SMILE_RIGHT = 24
    }
}

/**
 * The exact condition written by [IosCaptureSession] for every guided take.
 *
 * It is internal so iOS tests exercise the production condition builder rather than reproduce a
 * similar common condition by hand. The native ARSCNView camera/mesh preview is present throughout
 * this workflow, so it must not be pooled with the historical no-preview condition.
 */
internal fun iosGuidedCaptureCondition(
    nativeWidth: Int,
    nativeHeight: Int,
    provenance: SourceProvenance,
): CaptureCondition = CaptureCondition(
    nativeResolution = "${nativeWidth}x$nativeHeight",
    runningMode = "AR_SESSION_LIVE",
    delegate = "ARKIT",
    numFaces = 1,
    minDetectionConfidence = 0f,
    minTrackingConfidence = 0f,
    minPresenceConfidence = 0f,
    mediapipeVersion = "not-applicable",
    modelSha256 = ARKIT_PROVIDER_SHA256,
    mirrored = false,
    participantPreview = ParticipantPreview.NATIVE_CAMERA_WITH_GUIDE,
    layer = MeasurementLayer.DEVICE_REALTIME,
    provenance = provenance,
)
