@file:OptIn(ExperimentalForeignApi::class)
@file:Suppress(
    // Swift-safe setup and publish-last cleanup must classify every Kotlin/Native failure.
    "TooGenericExceptionCaught",
    "InstanceOfCheckForException",
)

package io.github.kmpfacelink.capture

import io.github.kmpfacelink.model.LiveLinkBlendShapes
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import platform.Foundation.NSData
import platform.Foundation.NSLock
import platform.Foundation.NSNumber
import platform.Foundation.NSProcessInfo
import platform.UIKit.UIView
import kotlin.math.absoluteValue

private const val ARKIT_PROVIDER_SHA256 = "2222770b59580b1925bec9cafae56c306ee8cc989867841ffbb8aeeb83a82ca4"

/**
 * A one-callback claim created before Swift copies an optional camera payload.
 *
 * The claim freezes whether this callback belongs to a take, closing the race where recording can
 * start between a separate `needsPixels` query and frame delivery. It contains no ARKit object.
 */
public data class IosCaptureFrameDemand(
    val analysisFrameSeq: Int,
    val rawPixelsRequired: Boolean,
)

/** Swift-visible non-throwing setup result with a public-safe failure receipt. */
public data class IosCaptureSessionOpenResult(
    val session: IosCaptureSession?,
    val failure: CaptureFailureReceipt?,
)

/**
 * Creates a capture session through a Swift-visible throwing boundary.
 *
 * [IosCaptureSession] prepares its private storage during construction. Kotlin exceptions from a
 * constructor cannot be represented as Swift errors, so Swift hosts should use this factory and
 * present any setup failure instead of allowing it to terminate the process.
 */
public object IosCaptureSessionFactory {
    public fun openRegisteredCoefficient17(
        rootDirectory: String,
        provenance: SourceProvenance,
        captureWorkflow: CaptureWorkflow = CaptureWorkflow.GUIDED_GATE_AUTOSTART_V1,
    ): IosCaptureSessionOpenResult = try {
        IosCaptureSessionOpenResult(
            session = createRegisteredCoefficient17(rootDirectory, provenance, captureWorkflow),
            failure = null,
        )
    } catch (failure: Throwable) {
        if (failure is Error) throw failure
        IosCaptureSessionOpenResult(
            session = null,
            failure = failure.toCaptureFailureReceipt(CaptureFailureCategory.STORAGE_SETUP),
        )
    }

    @Throws(Exception::class)
    public fun create(
        rootDirectory: String,
        provenance: SourceProvenance,
        captureWorkflow: CaptureWorkflow = CaptureWorkflow.GUIDED_GATE_AUTOSTART_V1,
    ): IosCaptureSession = IosCaptureSession(rootDirectory, provenance, captureWorkflow)

    /** Creates the exact coefficient-only 17-take iOS retake registered by the study protocol. */
    @Throws(Exception::class)
    public fun createRegisteredCoefficient17(
        rootDirectory: String,
        provenance: SourceProvenance,
        captureWorkflow: CaptureWorkflow = CaptureWorkflow.GUIDED_GATE_AUTOSTART_V1,
    ): IosCaptureSession = IosCaptureSession(
        rootDirectory = rootDirectory,
        provenance = provenance,
        captureWorkflow = captureWorkflow,
        studyProtocol = CaptureStudyProtocol.iosRegisteredCoefficient17(),
    )
}

/** Records ARKit camera frames and raw face coefficients into the shared take format. */
@Suppress(
    "TooManyFunctions",
    "TooGenericExceptionCaught",
    "InstanceOfCheckForException",
    "InjectDispatcher",
    "ThrowsCount",
)
public class IosCaptureSession(
    rootDirectory: String,
    private val provenance: SourceProvenance,
    private val captureWorkflow: CaptureWorkflow = CaptureWorkflow.GUIDED_GATE_AUTOSTART_V1,
    override val studyProtocol: CaptureStudyProtocol = CaptureStudyProtocol.guided22(),
) : CaptureSession {
    private val store = IosCaptureStore(
        rootDirectory,
        studyProtocol.id,
        studyProtocol.storageProfile,
    )
    override val studyRun: StudyRunIdentity = store.studyRun
    private val _uiState = MutableStateFlow(CaptureUiState())
    override val uiState: StateFlow<CaptureUiState> = _uiState.asStateFlow()
    internal val studyTakeRecords = MutableStateFlow(readIosStudyTakeRecords(store.takesRoot, studyRun))
    private val frameLock = platform.Foundation.NSCondition()

    private var take: ActiveTake? = null
    private var frameSequence = 0
    private var nativeWidth = 0
    private var nativeHeight = 0
    private var stopRequested = false
    private var released = false
    private var arSessionLease: IosArSessionLease? = null
    private var previewView: UIView? = null
    private var preparedFrame: PreparedFrame? = null
    private var frameSourceFailed = false
    private val runGuard = CaptureRunGuard()

    /**
     * Gives the Compose host the Swift-owned native preview view. The view contains the one
     * ARSession but KMP never receives it as an ARKit type or controls its lifecycle.
     */
    public fun attachPreviewView(view: UIView) {
        check(!released) { "capture session is released" }
        check(previewView == null || previewView === view) { "a different native preview is already attached" }
        previewView = view
    }

    internal fun attachedPreviewView(): UIView =
        checkNotNull(previewView) { "Swift must attach the native AR preview before showing capture UI" }

    @Suppress("LongParameterList")
    private class ActiveTake(
        val id: String,
        val directory: String,
        val scenario: CaptureScenario,
        val attempt: Int,
        val script: TakeScript,
        val startedAtMs: Long,
        val startedAtNs: Long,
        val frames: IosRawFrameSink?,
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

    private data class PreparedFrame(
        val demand: IosCaptureFrameDemand,
        val active: ActiveTake?,
    )

    /**
     * Opens one ARKit session and keeps warm-up outside every measured take.
     *
     * Swift/ARC owns the ARSession, ARSCNView delegate, and all ARKit callback objects. This
     * method acquires only the process-local lease; Swift configures and runs the already attached
     * native preview immediately afterwards.
     */
    @Throws(IllegalStateException::class)
    public fun start() {
        try {
            check(!released) { "capture session is released" }
            if (arSessionLease != null) return
            arSessionLease = IosArSessionLeaseRegistry.acquire("guided capture")
        } catch (failure: Throwable) {
            if (failure is Error || failure is CaptureFailureException) throw failure
            throw captureFailure(CaptureFailureCategory.ROOT_IN_USE, failure)
        }
    }

    override suspend fun record(scenario: CaptureScenario, storageProfile: CaptureStorageProfile): TakeOutcome {
        if (isFrameSourceFailed()) throw captureFailure(CaptureFailureCategory.FRAME_SOURCE)
        if (!runGuard.tryEnter()) throw captureFailure(CaptureFailureCategory.CONFIGURATION)
        try {
            return recordGuarded(scenario, storageProfile)
        } catch (failure: Throwable) {
            if (failure.toCaptureFailureReceipt().code == CaptureFailureCategory.FRAME_SOURCE.code) {
                markFrameSourceFailed(CaptureFailureCategory.FRAME_SOURCE.receipt())
            }
            throw failure
        } finally {
            _uiState.value = if (isFrameSourceFailed()) {
                _uiState.value.withTerminalCaptureFailure(CaptureFailureCategory.FRAME_SOURCE.receipt())
            } else {
                _uiState.value.copy(
                    recording = false,
                    progress = LiveProgress.None,
                    scriptElapsedMs = null,
                )
            }
            runGuard.leave()
        }
    }

    private suspend fun recordGuarded(
        scenario: CaptureScenario,
        storageProfile: CaptureStorageProfile,
    ): TakeOutcome {
        if (take != null || released || storageProfile != studyRun.storageProfile) {
            throw captureFailure(CaptureFailureCategory.CONFIGURATION)
        }
        val attempt = nextProtocolAttempt(studyTakeRecords.value, studyProtocol, scenario)
        stopRequested = false
        if (!awaitGate()) return cancelledOutcome(scenario)
        val startedAtMs = captureCurrentTimeMillis()
        val id = store.newTakeId(scenario, attempt, startedAtMs)
        val active = withIncompleteTakeDirectory(
            create = { store.createTmpDir(id) },
            discard = store::discard,
        ) { directory ->
            if (nativeWidth <= 0 || nativeHeight <= 0) {
                throw captureFailure(CaptureFailureCategory.FRAME_SOURCE)
            }
            ActiveTake(
                id = id,
                directory = directory,
                scenario = scenario,
                attempt = attempt,
                script = TakeScript(scenario),
                startedAtMs = startedAtMs,
                startedAtNs = monotonicNanos(),
                frames = when (storageProfile) {
                    CaptureStorageProfile.COEFFICIENTS_ONLY_V1 -> null
                    // V2 intentionally fails closed until the Swift canonicalizer is added and its
                    // asymmetric orientation fixture is source-tested. Native ARKit bytes are never a fallback.
                    CaptureStorageProfile.CANONICAL_RGBA_240X320_V1,
                    CaptureStorageProfile.LEGACY_DIAGNOSTIC_ONLY,
                    -> throw captureFailure(CaptureFailureCategory.CONFIGURATION)
                },
                results = try {
                    openInferenceRecordSink(directory)
                } catch (failure: Throwable) {
                    if (failure is Error || failure is CaptureFailureException) throw failure
                    throw captureFailure(CaptureFailureCategory.STREAM_WRITE, failure)
                },
            )
        }
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
            if (active.lock.withCaptureLock { active.failure != null }) return ScriptEnd.FAILED
            val elapsed = elapsedMs(active)
            _uiState.value = _uiState.value.copy(scriptElapsedMs = elapsed)
            if (elapsed >= active.script.totalMs) return ScriptEnd.COMPLETED
            if (stopRequested) return ScriptEnd.STOPPED
            if (active.scenario.goalDriven && active.lock.withCaptureLock { active.evaluator.isGoalReached() }) {
                return ScriptEnd.COMPLETED
            }
            delay(TICK_MS)
        }
    }

    private suspend fun awaitGate(): Boolean {
        val deadline = monotonicNanos() + GATE_TIMEOUT_NS
        while (!_uiState.value.gate.passed) {
            if (stopRequested) return false
            if (monotonicNanos() >= deadline) {
                throw captureFailure(CaptureFailureCategory.FRAME_SOURCE)
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

    /**
     * Freezes one callback's take membership before Swift decides whether to copy RGBA bytes.
     * Swift serializes its renderer callbacks. The condition additionally makes stop/release wait
     * for the one snapshot currently being copied before KMP closes the take sinks.
     */
    @Throws(IllegalStateException::class)
    public fun prepareFrame(): IosCaptureFrameDemand {
        return frameLock.withCaptureLock {
            check(!released) { "capture session is released" }
            check(!frameSourceFailed) { CaptureFailureCategory.FRAME_SOURCE.code }
            check(preparedFrame == null) { "the previous ARKit frame demand was not completed" }
            val active = take?.takeIf { candidate ->
                candidate.lock.withCaptureLock { candidate.recording }
            }
            val demand = IosCaptureFrameDemand(
                analysisFrameSeq = frameSequence++,
                rawPixelsRequired = active?.frames != null,
            )
            preparedFrame = PreparedFrame(demand, active)
            demand
        }
    }

    /**
     * Accepts a Swift-owned value snapshot. No ARKit/SceneKit/CoreVideo object is allowed here.
     * `rgba` must be an immutable owned copy and is required only for a demand frozen inside a take.
     */
    @Suppress("LongMethod", "CyclomaticComplexMethod", "LongParameterList")
    @Throws(IllegalStateException::class, IllegalArgumentException::class)
    public fun acceptFrame(
        demand: IosCaptureFrameDemand,
        width: Int,
        height: Int,
        captureTimestampNs: Long,
        luma: Float,
        transform: List<NSNumber>?,
        blendshapes: Map<String, NSNumber>?,
        rgba: NSData?,
    ) {
        var prepared: PreparedFrame? = null
        try {
            prepared = frameLock.withCaptureLock {
                val pending = checkNotNull(preparedFrame) { "no ARKit frame demand is pending" }
                check(pending.demand == demand) { "ARKit frame demand does not match the pending callback" }
                pending
            }
            onFrame(
                checkNotNull(prepared),
                width,
                height,
                captureTimestampNs,
                luma,
                transform,
                blendshapes,
                rgba,
            )
        } catch (failure: Throwable) {
            if (failure is Error) throw failure
            failSession(captureFailure(CaptureFailureCategory.FRAME_SOURCE, failure))
        } finally {
            frameLock.withCaptureLock {
                if (preparedFrame?.demand == demand) {
                    preparedFrame = null
                    frameLock.broadcast()
                }
            }
        }
    }

    /** Clears a demand when Swift cannot produce its owned snapshot. */
    public fun rejectFrame(demand: IosCaptureFrameDemand, @Suppress("UNUSED_PARAMETER") message: String) {
        frameLock.withCaptureLock {
            if (preparedFrame?.demand == demand) {
                preparedFrame = null
                frameLock.broadcast()
            }
        }
        failSession(captureFailure(CaptureFailureCategory.FRAME_SOURCE))
    }

    /** Surfaces an ARSession/Swift extraction failure without throwing through Objective-C. */
    public fun reportFrameSourceFailure(@Suppress("UNUSED_PARAMETER") message: String) {
        frameLock.withCaptureLock {
            preparedFrame = null
            frameLock.broadcast()
        }
        failSession(captureFailure(CaptureFailureCategory.FRAME_SOURCE))
    }

    @Suppress("LongMethod", "CyclomaticComplexMethod", "LongParameterList")
    private fun onFrame(
        prepared: PreparedFrame,
        width: Int,
        height: Int,
        captureTimestampNs: Long,
        luma: Float,
        rawTransform: List<NSNumber>?,
        rawBlendshapes: Map<String, NSNumber>?,
        rgba: NSData?,
    ) {
        if (isFrameSourceFailed()) return
        require(width > 0 && height > 0) { "ARKit frame geometry must be non-empty" }
        require(captureTimestampNs >= 0L) { "ARKit capture timestamp must not be negative" }
        require(luma.isFinite()) { "ARKit frame luma must be finite" }
        require((rawTransform == null) == (rawBlendshapes == null)) {
            "tracked face transform and blendshapes must be present together"
        }
        require(!prepared.demand.rawPixelsRequired || rgba != null) {
            "the frozen ARKit frame demand requires owned RGBA bytes"
        }
        require(prepared.demand.rawPixelsRequired || rgba == null) {
            "owned RGBA bytes were supplied outside a frozen recording demand"
        }
        nativeWidth = width
        nativeHeight = height
        val transform = rawTransform?.let(::transformOf)
        val blendshapes = rawBlendshapes?.let(::blendshapesOf)
        val metrics = gateMetrics(transform)
        val gate = HardGate.evaluate(metrics?.box, luma, metrics?.iod ?: 0f)
        _uiState.value = _uiState.value.let { current ->
            current.copy(
                gate = gate,
                distanceHint = HardGate.distanceHint(metrics?.iod ?: 0f, transform != null),
                // ARKit's native face topology is not MediaPipe's 478-point contract. The
                // participant preview renders native ARSCNFaceGeometry separately; recordings do
                // not relabel it as common landmarks.
                landmarks = null,
                aspect = height.toFloat() / width,
                resultsObserved = current.resultsObserved + 1,
            )
        }
        if (isFrameSourceFailed()) {
            _uiState.value = _uiState.value.withTerminalCaptureFailure(
                CaptureFailureCategory.FRAME_SOURCE.receipt(),
            )
            return
        }

        val active = prepared.active ?: return
        val elapsed = elapsedMs(active)
        val spanIndex = active.script.spans.indexOfFirst { elapsed < it.endMs }
        if (spanIndex < 0 || !active.lock.withCaptureLock { active.recording }) return
        val phase = active.script.spans[spanIndex].phase
        val sequence = prepared.demand.analysisFrameSeq
        val captureNs = captureTimestampNs
        try {
            active.frames?.writeOwned(
                IosOwnedRawFrame(
                    width = width,
                    height = height,
                    analysisFrameSeq = sequence,
                    captureTimestampNs = captureNs,
                    inferenceTimestampMs = captureNs / NS_PER_MILLISECOND,
                    phase = phase,
                    pixels = checkNotNull(rgba),
                ),
            )
            active.results.write(
                InferenceRecord(
                    analysisFrameSeq = sequence,
                    captureTimestampNs = captureNs,
                    resultElapsedNs = monotonicNanos(),
                    phase = phase,
                    hasFace = transform != null,
                    inferenceFailed = false,
                    landmarks = null,
                    blendshapes = blendshapes,
                    transform = transform,
                ),
            )
            collect(active, spanIndex, sequence, captureNs, phase, metrics, blendshapes, transform)
        } catch (t: Throwable) {
            if (t is Error) throw t
            val failure = captureFailure(CaptureFailureCategory.STREAM_WRITE, t)
            active.lock.withCaptureLock {
                if (active.failure == null) active.failure = failure
                active.recording = false
            }
            val receipt = failure.toCaptureFailureReceipt()
            _uiState.value = _uiState.value.copy(lastError = receipt.code)
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
            CaptureStorageReceipt.coefficientsOnly(0, 0).requireNextRecord(active.admitted)
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
        // Swift may still be copying the one callback whose demand was frozen before recording
        // closed. Do not close the sinks until that owned snapshot is accepted or rejected.
        drainPreparedFrame()
        take = null
        val validation = active.lock.withCaptureLock { active.evaluator.outcome() }
        val resultFailure = runCatching { active.results.close() }.exceptionOrNull()?.let {
            captureFailure(CaptureFailureCategory.STREAM_WRITE, it)
        }
        val frameFailure = active.frames?.let { sink ->
            runCatching { sink.close() }.exceptionOrNull()?.let {
                captureFailure(CaptureFailureCategory.STREAM_WRITE, it)
            }
        }
        val failure = active.lock.withCaptureLock { active.failure } ?: resultFailure ?: frameFailure
        if (!shouldPublishTake(end, failure != null)) {
            store.discard(active.directory)
            val receipt = failure?.toCaptureFailureReceipt() ?: CaptureFailureCategory.UNKNOWN.receipt()
            return TakeOutcome(
                scenario = active.scenario,
                recordCount = active.results.recordCount,
                softValidationPassed = false,
                reason = if (end == ScriptEnd.STOPPED) "take_cancelled" else "take_failed",
                error = receipt.code,
            )
        }
        val hashes = active.results.hashes()
        val manifest = manifestFor(active, validation, hashes)
        cleanupIncompleteTakeOnFailure(active.directory, store::discard) {
            try {
                publishCaptureText(
                    active.directory,
                    InferenceRecordFormat.TAKE_MANIFEST_FILE,
                    rawFrameJson.encodeToString(manifest),
                )
                store.publish(active.directory, active.id)
            } catch (failure: Throwable) {
                if (failure is Error || failure is CaptureFailureException) throw failure
                throw captureFailure(CaptureFailureCategory.STREAM_WRITE, failure)
            }
        }
        studyTakeRecords.value = withPublishedStudyTake(
            studyTakeRecords.value,
            StudyTakeRecord(active.id, active.scenario, active.attempt, studyRun),
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
            storageReceipt = CaptureStorageReceipt.coefficientsOnly(nativeWidth, nativeHeight),
            studyRun = studyRun,
        )
    }

    private fun condition(): CaptureCondition = iosGuidedCaptureCondition(
        nativeWidth = nativeWidth,
        nativeHeight = nativeHeight,
        provenance = provenance,
    )

    private fun blendshapesOf(raw: Map<String, NSNumber>): FloatArray {
        val normalized = raw.entries.associate { normalizeArKitName(it.key) to it.value.floatValue }
        return FloatArray(LiveLinkBlendShapes.names.size) { index ->
            normalized[LiveLinkBlendShapes.names[index]] ?: 0f
        }
    }

    private fun transformOf(raw: List<NSNumber>): FloatArray {
        require(raw.size == InferenceRecordFormat.TRANSFORM_FLOATS) {
            "ARKit face transform must contain ${InferenceRecordFormat.TRANSFORM_FLOATS} values"
        }
        return FloatArray(raw.size) { index -> raw[index].floatValue }
    }

    private fun normalizeArKitName(name: String): String = when {
        name.endsWith("_L") -> name.dropLast(2) + "Left"
        name.endsWith("_R") -> name.dropLast(2) + "Right"
        else -> name
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
        try {
            released = true
            stopRequested = true
            take?.let { active -> active.lock.withCaptureLock { active.recording = false } }
            // This also covers a no-record warm-up callback: release must not clear a demand while
            // Swift is still copying its owned snapshot.
            drainPreparedFrame()
            take?.let { active ->
                runCatching { active.results.close() }
                runCatching { active.frames?.close() }
                store.discard(active.directory)
                take = null
            }
            frameLock.withCaptureLock {
                preparedFrame = null
                frameLock.broadcast()
            }
            previewView = null
            arSessionLease?.release()
            arSessionLease = null
        } finally {
            store.close()
        }
    }

    /** Directory containing only fully published takes. */
    public val takesDirectory: String get() = store.takesRoot

    internal fun removeStudyTake(takeId: String) {
        studyTakeRecords.value = withoutStudyTake(studyTakeRecords.value, takeId)
    }

    private fun failSession(failure: Throwable) {
        val receipt = failure.toCaptureFailureReceipt(CaptureFailureCategory.UNKNOWN)
        _uiState.value = if (receipt.retryable) {
            _uiState.value.copy(lastError = receipt.code)
        } else {
            if (receipt.code == CaptureFailureCategory.FRAME_SOURCE.code) {
                markFrameSourceFailed(receipt)
            }
            _uiState.value.withTerminalCaptureFailure(receipt)
        }
        take?.let { active ->
            active.lock.withCaptureLock {
                if (active.failure == null) active.failure = failure
                active.recording = false
            }
        }
    }

    private fun isFrameSourceFailed(): Boolean = frameLock.withCaptureLock { frameSourceFailed }

    private fun markFrameSourceFailed(receipt: CaptureFailureReceipt) {
        frameLock.withCaptureLock { frameSourceFailed = true }
        _uiState.value = _uiState.value.withTerminalCaptureFailure(receipt)
    }

    private fun drainPreparedFrame() {
        frameLock.withCaptureLock {
            while (preparedFrame != null) frameLock.wait()
        }
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
