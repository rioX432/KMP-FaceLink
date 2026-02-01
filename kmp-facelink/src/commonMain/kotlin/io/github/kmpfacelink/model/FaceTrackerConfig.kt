package io.github.kmpfacelink.model

/**
 * Configuration for [io.github.kmpfacelink.api.FaceTracker].
 *
 * @property smoothingConfig Smoothing filter configuration. Use [SmoothingConfig.None],
 *           [SmoothingConfig.Ema], or [SmoothingConfig.OneEuro].
 * @property enableSmoothing Apply temporal smoothing to reduce jitter (default: true).
 *           Deprecated — use [smoothingConfig] instead.
 * @property smoothingFactor Exponential moving average factor (0.0–1.0).
 *           Deprecated — use [smoothingConfig] instead.
 * @property enhancerConfig Blend shape enhancer configuration for improving low-accuracy
 *           MediaPipe parameters using geometric landmark calculations.
 *           Defaults to [BlendShapeEnhancerConfig.Default]. Use [BlendShapeEnhancerConfig.None]
 *           to disable. Has no effect on iOS (ARKit).
 * @property enableCalibration Enable per-session calibration (default: false)
 * @property cameraFacing Which camera to use
 */
public data class FaceTrackerConfig(
    val smoothingConfig: SmoothingConfig = SmoothingConfig.Ema(),
    val enhancerConfig: BlendShapeEnhancerConfig = BlendShapeEnhancerConfig.Default(),
    @Deprecated("Use smoothingConfig instead", replaceWith = ReplaceWith("smoothingConfig"))
    val enableSmoothing: Boolean = true,
    @Deprecated("Use smoothingConfig instead", replaceWith = ReplaceWith("smoothingConfig"))
    val smoothingFactor: Float = 0.5f,
    val enableCalibration: Boolean = false,
    val cameraFacing: CameraFacing = CameraFacing.FRONT,
)

/**
 * Smoothing filter configuration.
 */
public sealed class SmoothingConfig {
    /** No smoothing applied. */
    public data object None : SmoothingConfig()

    /**
     * Exponential Moving Average filter.
     * @param alpha Smoothing factor (0.0–1.0). Higher = less smoothing.
     */
    public data class Ema(val alpha: Float = 0.5f) : SmoothingConfig()

    /**
     * One Euro adaptive low-pass filter.
     * @param minCutoff Minimum cutoff frequency in Hz (lower = more smoothing at rest)
     * @param beta Speed coefficient (higher = less lag during fast movements)
     * @param dCutoff Derivative cutoff frequency in Hz
     */
    public data class OneEuro(
        val minCutoff: Float = 1.0f,
        val beta: Float = 0.007f,
        val dCutoff: Float = 1.0f,
    ) : SmoothingConfig()
}

/**
 * Camera facing direction.
 */
public enum class CameraFacing {
    FRONT,
    BACK,
}
