package io.github.kmpfacelink.model

/**
 * Per-session calibration configuration.
 *
 * @property enabled Enable per-session calibration (default: false).
 * @property warmupFrames Number of frames to collect statistics before normalization begins.
 *           During warmup, raw values are passed through unchanged (default: 60 ≈ 2 s at 30 fps).
 * @property rangeDecayRate EMA decay rate applied to the observed min/max range each frame.
 *           A small positive value allows the range to slowly shrink after outliers, recovering
 *           dynamic range over time (default: 0.001).
 */
public data class CalibrationConfig(
    val enabled: Boolean = false,
    val warmupFrames: Int = 60,
    val rangeDecayRate: Float = 0.001f,
)

/**
 * Configuration for [io.github.kmpfacelink.api.FaceTracker].
 *
 * @property smoothingConfig Smoothing filter configuration. Use [SmoothingConfig.None],
 *           [SmoothingConfig.Ema], or [SmoothingConfig.OneEuro].
 * @property enhancerConfig Blend shape enhancer configuration for improving low-accuracy
 *           MediaPipe parameters using geometric landmark calculations.
 *           Defaults to [BlendShapeEnhancerConfig.Default]. Use [BlendShapeEnhancerConfig.None]
 *           to disable. Android only — ARKit provides high-quality blend shapes natively,
 *           so this setting has no effect on iOS.
 * @property calibrationConfig Per-session calibration configuration. See [CalibrationConfig].
 * @property cameraFacing Which camera to use
 * @property modelAssetPath Path to the MediaPipe face landmarker model asset relative to the
 *           Android assets directory. Has no effect on iOS (ARKit). Override to use a custom or
 *           updated model bundle.
 * @property stopDrainDelayMs Milliseconds to wait after unbinding the camera before closing the
 *           MediaPipe landmarker, allowing any in-flight async detection result to be delivered.
 *           Android only.
 * @property releaseTimeoutMs Maximum milliseconds to wait for the camera analysis executor to
 *           terminate during [io.github.kmpfacelink.api.FaceTracker.release]. Android only.
 */
public data class FaceTrackerConfig(
    val smoothingConfig: SmoothingConfig = SmoothingConfig.Ema(),
    val enhancerConfig: BlendShapeEnhancerConfig = BlendShapeEnhancerConfig.Default(),
    val calibrationConfig: CalibrationConfig = CalibrationConfig(),
    val cameraFacing: CameraFacing = CameraFacing.FRONT,
    val modelAssetPath: String = "models/face_landmarker_v2_with_blendshapes.task",
    val stopDrainDelayMs: Long = 100L,
    val releaseTimeoutMs: Long = 500L,
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

    /**
     * Category-aware One Euro filter that applies different smoothing parameters per
     * facial region. Fast-moving regions (mouth, jaw) use higher cutoff frequencies for
     * lower lag; slow-moving regions (brow, cheek) use lower cutoff frequencies for
     * heavier jitter suppression.
     *
     * @param mouthConfig One Euro params for mouth blend shapes.
     * @param eyeConfig One Euro params for eye blend shapes.
     * @param browConfig One Euro params for brow blend shapes.
     * @param cheekConfig One Euro params for cheek blend shapes.
     * @param jawConfig One Euro params for jaw blend shapes.
     * @param otherConfig One Euro params for nose and other blend shapes.
     */
    public data class CategoryAware(
        val mouthConfig: OneEuro = OneEuro(minCutoff = 3.0f, beta = 0.01f),
        val eyeConfig: OneEuro = OneEuro(minCutoff = 2.0f, beta = 0.008f),
        val browConfig: OneEuro = OneEuro(minCutoff = 1.0f, beta = 0.005f),
        val cheekConfig: OneEuro = OneEuro(minCutoff = 0.8f, beta = 0.005f),
        val jawConfig: OneEuro = OneEuro(minCutoff = 1.5f, beta = 0.007f),
        val otherConfig: OneEuro = OneEuro(),
    ) : SmoothingConfig()
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
