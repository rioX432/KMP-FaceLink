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
 */
public data class BodyTrackerConfig(
    val smoothingConfig: SmoothingConfig = SmoothingConfig.Ema(),
    val maxBodies: Int = 1,
    val cameraFacing: CameraFacing = CameraFacing.FRONT,
    val minDetectionConfidence: Float = DEFAULT_CONFIDENCE,
    val minTrackingConfidence: Float = DEFAULT_CONFIDENCE,
    val minPresenceConfidence: Float = DEFAULT_CONFIDENCE,
    val thermalPolicy: ThermalPolicy = ThermalPolicy.Disabled,
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
    }

    public companion object {
        /** Default MediaPipe confidence threshold. */
        public const val DEFAULT_CONFIDENCE: Float = 0.5f

        private const val MAX_BODIES = 3
    }
}
