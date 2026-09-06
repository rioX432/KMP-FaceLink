package io.github.kmpfacelink.capture

import kotlinx.coroutines.flow.StateFlow

/**
 * What the recording screen shows while a take is running.
 *
 * The gate and the live progress come from the same evaluators the saved metadata is built from,
 * so what the subject is told on screen and what the take is later judged by cannot drift apart.
 *
 * @property gate whether the frame currently clears the hard gate's bbox, luma, and inter-ocular
 *   proxy bands. The countdown must not start until it does. Passing the proxy gate is a workflow
 *   state, not validation of physical distance, lighting, pose, or take quality.
 * @property distanceHint what to tell the subject when the gate is failing on distance
 * @property progress how far through its goal a goal-driven scenario is
 * @property landmarks latest normalized landmarks for the common mesh overlay. The platform camera
 *   preview is rendered separately underneath it. This is null when the platform's native face
 *   topology cannot truthfully be represented by the common MediaPipe 478-point contract
 * @property aspect normalized landmark source height divided by width. The overlay uses it for the
 *   shared FIT/FILL viewport geometry; it is not evidence that a native preview is physically aligned
 * @property resultsObserved how many inference results have come back since the session opened.
 *   The operator needs to see that the pipeline is alive before starting a take, and "no face yet"
 *   and "no frames at all" look identical without it
 * @property lastError legacy stable failure code. It never contains platform exception text or a
 *   filesystem path; new code should use [lastFailure]
 * @property scriptElapsedMs position within the take script, or null when no take is running. The
 *   session publishes the same clock it stamps the spans with, so that what the operator is told to
 *   do and what the recording says they were told to do cannot drift apart. Deriving it in the UI
 *   from the moment the button was pressed drifts by however long the hard gate took to pass.
 */
public data class CaptureUiState(
    val gate: HardGate.Result = HardGate.Result(false, false, false, false, false),
    val distanceHint: HardGate.DistanceHint = HardGate.DistanceHint.NONE,
    val progress: LiveProgress = LiveProgress.None,
    val landmarks: FloatArray? = null,
    val aspect: Float = 1f,
    val recording: Boolean = false,
    val resultsObserved: Int = 0,
    val lastError: String? = null,
    val scriptElapsedMs: Long? = null,
) {
    /**
     * Additive public-safe view of [lastError]. Keeping it outside the primary constructor preserves
     * the existing constructor, copy, equality, and component contracts.
     */
    public val lastFailure: CaptureFailureReceipt?
        get() = lastError?.let { code ->
            CaptureFailureCategory.entries.firstOrNull { it.code == code }?.receipt()
        }

    // Generated equals would compare the landmark array by identity, which makes every emission
    // look like a change and recomposes the overlay on every frame regardless.
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is CaptureUiState) return false
        return gate == other.gate &&
            distanceHint == other.distanceHint &&
            progress == other.progress &&
            aspect == other.aspect &&
            recording == other.recording &&
            resultsObserved == other.resultsObserved &&
            lastError == other.lastError &&
            scriptElapsedMs == other.scriptElapsedMs &&
            landmarks.contentEquals(other.landmarks)
    }

    override fun hashCode(): Int {
        var result = gate.hashCode()
        result = 31 * result + distanceHint.hashCode()
        result = 31 * result + progress.hashCode()
        result = 31 * result + aspect.hashCode()
        result = 31 * result + recording.hashCode()
        result = 31 * result + resultsObserved
        result = 31 * result + lastError.hashCode()
        result = 31 * result + scriptElapsedMs.hashCode()
        result = 31 * result + landmarks.contentHashCode()
        return result
    }
}

/** Clears stale live guidance when a non-retryable source failure terminates the run. */
internal fun CaptureUiState.withTerminalCaptureFailure(
    receipt: CaptureFailureReceipt,
): CaptureUiState {
    require(!receipt.retryable) { "terminal capture failure must not be retryable" }
    return copy(
        gate = HardGate.Result(false, false, false, false, false),
        distanceHint = HardGate.DistanceHint.NONE,
        progress = LiveProgress.None,
        landmarks = null,
        recording = false,
        lastError = receipt.code,
        scriptElapsedMs = null,
    )
}

/** How a take ended. */
public data class TakeOutcome(
    val scenario: CaptureScenario,
    val recordCount: Int,
    val softValidationPassed: Boolean,
    val reason: String,
    val error: String? = null,
) {
    /** Additive structured view of the legacy error field; unknown strings are never surfaced. */
    public val failure: CaptureFailureReceipt?
        get() = error?.let { code ->
            CaptureFailureCategory.entries.firstOrNull { it.code == code }?.receipt()
        }
}

/**
 * Drives one capture session: camera in, records out.
 *
 * The interface lives in common code so the recording UI can be written once. Everything
 * underneath it is platform work — CameraX and MediaPipe on Android, AVFoundation and ARKit on
 * iOS — and none of it belongs in a screen.
 *
 * Implementations are not thread-safe; drive them from the UI's dispatcher.
 */
public interface CaptureSession {

    /** One source of truth for the run identity, storage contract, and visible take order. */
    public val studyProtocol: CaptureStudyProtocol

    /** Live state for the recording screen. */
    public val uiState: StateFlow<CaptureUiState>

    /** Immutable active study boundary; published takes from another run never affect this UI. */
    public val studyRun: StudyRunIdentity

    /**
     * Begins a take. Recording only starts once the configured bbox, luma, and inter-ocular proxy
     * bands pass. The returned outcome records that workflow condition; it does not certify the
     * physical environment or expression quality.
     *
     * [storageProfile] is explicit: coefficient-only is safe by default in the guided UI, while
     * canonical RGBA is a separately bounded, replay-specific choice.
     */
    public suspend fun record(
        scenario: CaptureScenario,
        storageProfile: CaptureStorageProfile,
    ): TakeOutcome

    /** Stops an in-flight take early, keeping whatever was recorded. */
    public fun stop()

    /** Releases the camera and the inference engine. */
    public fun release()
}
