package io.github.kmpfacelink.model

/**
 * Configuration for [io.github.kmpfacelink.api.HandTracker].
 *
 * @property smoothingConfig Smoothing filter configuration. Reuses [SmoothingConfig] from face tracking.
 * @property maxHands Maximum number of hands to detect (1–2)
 * @property enableGestureRecognition Whether to classify gestures from landmarks
 * @property cameraFacing Which camera to use
 * @property thermalPolicy Policy controlling how the tracker adapts to thermal pressure.
 *           Defaults to [ThermalPolicy.Disabled].
 */
public data class HandTrackerConfig(
    val smoothingConfig: SmoothingConfig = SmoothingConfig.Ema(),
    val maxHands: Int = 2,
    val enableGestureRecognition: Boolean = true,
    val cameraFacing: CameraFacing = CameraFacing.FRONT,
    val thermalPolicy: ThermalPolicy = ThermalPolicy.Disabled,
) {
    init {
        require(maxHands in 1..2) { "maxHands must be 1 or 2, was $maxHands" }
    }
}
