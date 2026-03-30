package io.github.kmpfacelink.model

/**
 * Configuration for [io.github.kmpfacelink.api.BodyTracker].
 *
 * @property smoothingConfig Smoothing filter configuration. Reuses [SmoothingConfig] from face tracking.
 * @property maxBodies Maximum number of bodies to detect (1-3)
 * @property cameraFacing Which camera to use
 * @property minDetectionConfidence Minimum confidence score for pose detection to be considered
 *           successful. Android (MediaPipe) only. Must be in 0.0..1.0.
 * @property minTrackingConfidence Minimum confidence score for pose tracking to be considered
 *           successful. Android (MediaPipe) only. Must be in 0.0..1.0.
 * @property minPresenceConfidence Minimum confidence score for pose presence to be considered
 *           valid. Android (MediaPipe) only. Must be in 0.0..1.0.
 * @property thermalPolicy Policy controlling how the tracker adapts to thermal pressure.
 *           Defaults to [ThermalPolicy.Disabled].
 *           **Ignored when [powerMode] is set.**
 * @property powerMode Power management profile. When non-null, overrides [thermalPolicy].
 *           See [FaceTrackerConfig.powerMode] for details.
 * @property enable3D When true, uses 3D body pose estimation on iOS (VNDetectHumanBodyPose3DRequest,
 *           requires iOS 17+). Provides z-depth in meters relative to the root joint.
 *           Ignored on Android (MediaPipe always provides 3D landmarks).
 *           Defaults to false for backward compatibility.
 * @property analysisWidth Width of the camera analysis stream in pixels. Android only.
 *           Lower values improve performance at the cost of accuracy. Defaults to 640.
 * @property analysisHeight Height of the camera analysis stream in pixels. Android only.
 *           Lower values improve performance at the cost of accuracy. Defaults to 480.
 */
public data class BodyTrackerConfig(
    val smoothingConfig: SmoothingConfig = SmoothingConfig.Ema(),
    val maxBodies: Int = 1,
    val cameraFacing: CameraFacing = CameraFacing.FRONT,
    val minDetectionConfidence: Float = DEFAULT_CONFIDENCE,
    val minTrackingConfidence: Float = DEFAULT_CONFIDENCE,
    val minPresenceConfidence: Float = DEFAULT_CONFIDENCE,
    val thermalPolicy: ThermalPolicy = ThermalPolicy.Disabled,
    val powerMode: PowerMode? = null,
    val enable3D: Boolean = false,
    val analysisWidth: Int = DEFAULT_ANALYSIS_WIDTH,
    val analysisHeight: Int = DEFAULT_ANALYSIS_HEIGHT,
) {
    init {
        require(maxBodies in 1..MAX_BODIES) { "maxBodies must be 1-$MAX_BODIES, was $maxBodies" }
        require(minDetectionConfidence in 0f..1f) {
            "minDetectionConfidence must be in 0.0..1.0, was $minDetectionConfidence"
        }
        require(minTrackingConfidence in 0f..1f) {
            "minTrackingConfidence must be in 0.0..1.0, was $minTrackingConfidence"
        }
        require(minPresenceConfidence in 0f..1f) {
            "minPresenceConfidence must be in 0.0..1.0, was $minPresenceConfidence"
        }
        require(analysisWidth > 0) { "analysisWidth must be positive, was $analysisWidth" }
        require(analysisHeight > 0) { "analysisHeight must be positive, was $analysisHeight" }
    }

    public companion object {
        /** Default MediaPipe confidence threshold. */
        public const val DEFAULT_CONFIDENCE: Float = 0.5f

        /** Default camera analysis stream width in pixels. */
        public const val DEFAULT_ANALYSIS_WIDTH: Int = 640

        /** Default camera analysis stream height in pixels. */
        public const val DEFAULT_ANALYSIS_HEIGHT: Int = 480

        private const val MAX_BODIES = 3
    }
}
