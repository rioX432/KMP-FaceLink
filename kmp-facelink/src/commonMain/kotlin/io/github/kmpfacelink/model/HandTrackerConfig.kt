package io.github.kmpfacelink.model

/**
 * Configuration for [io.github.kmpfacelink.api.HandTracker].
 *
 * @property smoothingConfig Smoothing filter configuration. Reuses [SmoothingConfig] from face tracking.
 * @property maxHands Maximum number of hands to detect (1–2)
 * @property enableGestureRecognition Whether to classify gestures from landmarks
 * @property cameraFacing Which camera to use
 */
public data class HandTrackerConfig(
    val smoothingConfig: SmoothingConfig = SmoothingConfig.Ema(),
    val maxHands: Int = 2,
    val enableGestureRecognition: Boolean = true,
    val cameraFacing: CameraFacing = CameraFacing.FRONT,
)
