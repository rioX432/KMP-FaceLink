package io.github.kmpfacelink.model

/**
 * Configuration for [io.github.kmpfacelink.api.FaceTracker].
 *
 * @property enableSmoothing Apply temporal smoothing to reduce jitter (default: true)
 * @property smoothingFactor Exponential moving average factor (0.0–1.0).
 *           Lower = smoother but more latency. Default: 0.5
 * @property enableCalibration Enable per-session calibration (default: false)
 * @property cameraFacing Which camera to use
 */
public data class FaceTrackerConfig(
    val enableSmoothing: Boolean = true,
    val smoothingFactor: Float = 0.5f,
    val enableCalibration: Boolean = false,
    val cameraFacing: CameraFacing = CameraFacing.FRONT,
)

/**
 * Camera facing direction.
 */
public enum class CameraFacing {
    FRONT,
    BACK,
}
