package io.github.kmpfacelink.model

/**
 * Configuration for [io.github.kmpfacelink.api.FaceTracker].
 *
 * @property smoothingConfig Smoothing filter configuration. Use [SmoothingConfig.None],
 *           [SmoothingConfig.Ema], or [SmoothingConfig.OneEuro].
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
    public data class Ema(val alpha: Float = 0.5f) : SmoothingConfig() {
        init {
            require(alpha in 0f..1f) { "alpha must be in 0.0..1.0, was $alpha" }
        }
    }

    /**
     * One Euro adaptive low-pass filter.
     * @param minCutoff Minimum cutoff frequency in Hz (lower = more smoothing at rest)
     * @param beta Speed coefficient (higher = less lag during fast movements)
     * @param dCutoff Derivative cutoff frequency in Hz
     * @param predictionMs Prediction horizon in milliseconds. When > 0, the filter
     *        extrapolates the filtered value forward using the smoothed derivative:
     *        `predicted = filtered + derivative * (predictionMs / 1000)`.
     *        Useful for reducing perceived latency in remote tracking scenarios.
     *        Default is 0 (no prediction).
     */
    public data class OneEuro(
        val minCutoff: Float = 1.0f,
        val beta: Float = 0.007f,
        val dCutoff: Float = 1.0f,
        val predictionMs: Float = 0f,
    ) : SmoothingConfig() {
        init {
            require(minCutoff > 0f) { "minCutoff must be positive, was $minCutoff" }
            require(beta >= 0f) { "beta must be non-negative, was $beta" }
            require(dCutoff > 0f) { "dCutoff must be positive, was $dCutoff" }
            require(predictionMs >= 0f) { "predictionMs must be non-negative, was $predictionMs" }
        }
    }
}

/**
 * Camera facing direction.
 */
public enum class CameraFacing {
    /** Front-facing (selfie) camera. Supported on both Android and iOS. */
    FRONT,

    /** Rear camera. Supported on Android only. iOS face tracking requires the TrueDepth front camera. */
    BACK,
}
