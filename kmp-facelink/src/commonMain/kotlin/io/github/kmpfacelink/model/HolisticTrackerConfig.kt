package io.github.kmpfacelink.model

/**
 * Configuration for [io.github.kmpfacelink.api.HolisticTracker].
 *
 * Enables simultaneous face, hand, and body tracking through a single
 * unified pipeline sharing one camera session.
 *
 * @property enableFace Whether to enable face tracking
 * @property enableHand Whether to enable hand tracking
 * @property enableBody Whether to enable body tracking
 * @property faceConfig Configuration for the face tracker (used when [enableFace] is true)
 * @property handConfig Configuration for the hand tracker (used when [enableHand] is true)
 * @property bodyConfig Configuration for the body tracker (used when [enableBody] is true)
 * @property cameraFacing Which camera to use. Overrides per-tracker camera facing settings.
 * @property thermalPolicy Policy controlling how all sub-trackers adapt to thermal pressure.
 *           Overrides per-tracker thermal policy settings. Defaults to [ThermalPolicy.Disabled].
 *           **Ignored when [powerMode] is set.**
 * @property powerMode Power management profile for all sub-trackers. When non-null,
 *           overrides both [thermalPolicy] and per-tracker power mode settings.
 * @property inferenceDelegate Preferred hardware accelerator for ML model inference.
 *           Overrides per-tracker inference delegate settings.
 *           Android (MediaPipe) only. Defaults to [InferenceDelegate.AUTO].
 * @property analysisWidth Width of the shared camera analysis stream in pixels. Android only.
 *           Overrides per-tracker analysis width settings for the shared session.
 *           Lower values improve performance at the cost of accuracy. Defaults to 640.
 * @property analysisHeight Height of the shared camera analysis stream in pixels. Android only.
 *           Overrides per-tracker analysis height settings for the shared session.
 *           Lower values improve performance at the cost of accuracy. Defaults to 480.
 */
public data class HolisticTrackerConfig(
    val enableFace: Boolean = true,
    val enableHand: Boolean = true,
    val enableBody: Boolean = true,
    val faceConfig: FaceTrackerConfig = FaceTrackerConfig(),
    val handConfig: HandTrackerConfig = HandTrackerConfig(),
    val bodyConfig: BodyTrackerConfig = BodyTrackerConfig(),
    val cameraFacing: CameraFacing = CameraFacing.FRONT,
    val thermalPolicy: ThermalPolicy = ThermalPolicy.Disabled,
    val powerMode: PowerMode? = null,
    val inferenceDelegate: InferenceDelegate = InferenceDelegate.AUTO,
    val analysisWidth: Int = DEFAULT_ANALYSIS_WIDTH,
    val analysisHeight: Int = DEFAULT_ANALYSIS_HEIGHT,
) {
    init {
        require(enableFace || enableHand || enableBody) {
            "At least one tracking modality must be enabled"
        }
        require(analysisWidth > 0) { "analysisWidth must be positive, was $analysisWidth" }
        require(analysisHeight > 0) { "analysisHeight must be positive, was $analysisHeight" }
    }

    public companion object {
        /** Default camera analysis stream width in pixels. */
        public const val DEFAULT_ANALYSIS_WIDTH: Int = 640

        /** Default camera analysis stream height in pixels. */
        public const val DEFAULT_ANALYSIS_HEIGHT: Int = 480
    }
}

/**
 * Tracking modality that can be enabled in holistic tracking.
 */
public enum class TrackingModality {
    /** Face blend shapes, head transform, and landmarks */
    FACE,

    /** Hand landmarks and gestures */
    HAND,

    /** Body pose landmarks */
    BODY,
}
