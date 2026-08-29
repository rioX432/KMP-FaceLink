package io.github.kmpfacelink.capture

import kotlinx.serialization.Serializable

/**
 * Where the code that produced a take came from.
 *
 * Recorded because getting this wrong is not hypothetical. Twice in one day a measurement was
 * attributed to "production" while the code actually being read was a feature branch from six
 * weeks earlier — once for the MediaPipe version, once for whether a detector shipped at all.
 * A number without its provenance cannot be checked later, and a number that is checked against
 * the wrong tree is worse than no number.
 *
 * @property repo repository name, e.g. `KMP-FaceLink`
 * @property branch branch the build came from. **Naming a release requires a release branch** —
 *   not `main`, not a feature branch
 * @property gitSha full commit hash
 * @property commitTimeIso commit time, ISO-8601. A branch name alone is ambiguous once the branch
 *   has moved on
 * @property dirty whether the working tree had uncommitted changes at build time. A dirty build
 *   cannot be reconstructed from [gitSha] alone, so a take recorded from one is not reproducible
 */
@Serializable
public data class SourceProvenance(
    val repo: String,
    val branch: String,
    val gitSha: String,
    val commitTimeIso: String,
    val dirty: Boolean,
) {
    /**
     * True when a clean source commit is identified. This is necessary but not sufficient for an
     * exact rebuild: dependencies, toolchains, build settings, model, and binary identity remain
     * separate manifest/review inputs.
     */
    public val reproducible: Boolean get() = !dirty && gitSha.isNotBlank()
}

/**
 * Everything that has to travel with a measurement for it to mean anything later.
 *
 * The point of recording frames is that one take can be scored under several conditions, so a
 * number is only interpretable next to the condition that produced it. Keeping them in one object
 * makes that inseparable rather than a convention someone has to remember.
 *
 * @property nativeResolution what the camera actually delivered, e.g. `240x320`. Not what was
 *   requested — the two differ often enough that recording the request instead is misleading
 * @property downsampleTo resolution the frame was scaled to before inference, if any
 * @property downsampleAlgorithm the scaler used. Different filters give different landmarks, so
 *   "downsampled to 240x320" is not a complete description on its own
 * @property runningMode IMAGE, VIDEO or LIVE_STREAM. The streaming modes skip face detection
 *   while tracking holds, so this changes the output for the same picture
 * @property delegate CPU / GPU / NPU as actually resolved, not as requested
 * @property mirrored whether the frame was flipped before inference. The product mirrors its
 *   front camera; a capture that does not is measuring a different input
 * @property participantPreview participant-facing render path present during capture. Rendering a
 *   native preview can change device load even when it does not change inference/storage pixels,
 *   so old mesh-only/blank captures and new camera-preview captures are different conditions
 * @property layer which of the three measurement layers this is
 */
@Serializable
public data class CaptureCondition(
    val nativeResolution: String,
    val downsampleTo: String? = null,
    val downsampleAlgorithm: String? = null,
    val runningMode: String,
    val delegate: String,
    val numFaces: Int,
    val minDetectionConfidence: Float,
    val minTrackingConfidence: Float,
    val minPresenceConfidence: Float,
    val mediapipeVersion: String,
    val modelSha256: String,
    val mirrored: Boolean,
    val participantPreview: ParticipantPreview = ParticipantPreview.NONE,
    val layer: MeasurementLayer,
    val provenance: SourceProvenance,
) {
    init {
        require(modelSha256.isNotBlank()) {
            "modelSha256 is required: two takes are only comparable if the same model produced them"
        }
        require(downsampleAlgorithm == null || downsampleTo != null) {
            "downsampleAlgorithm was given without downsampleTo"
        }
    }
}

/** Participant-facing rendering present while the camera/inference pipeline runs. */
@Serializable
public enum class ParticipantPreview {
    /** No native camera image was rendered. Retained for older stored takes. */
    NONE,

    /** The recorder's existing native camera/AR session was rendered under the shared guide. */
    NATIVE_CAMERA_WITH_GUIDE,
}

/**
 * Which of the three layers a measurement belongs to.
 *
 * These labels preserve three different observational contexts. They do not by themselves make
 * the contexts exchangeable: scheduling, state, selected samples, endpoints, and statistical
 * units can differ. Subtraction, causal attribution, and differences between percentile summaries
 * therefore require a separately defined estimand and a design that matches those boundaries and
 * units; the enum does not authorize any of them.
 */
@Serializable
public enum class MeasurementLayer {
    /** Live camera on the device, in the run's declared live-device configuration. */
    DEVICE_REALTIME,

    /** Recorded frames, re-inferred on the device. */
    DEVICE_OFFLINE,

    /** The same recorded frames, re-inferred on a workstation. */
    HOST_OFFLINE,
}
