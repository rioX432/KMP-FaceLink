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
 * @property maxFaces Maximum number of faces to detect simultaneously (1–[MAX_FACES]).
 *           Android (MediaPipe): fully supported — up to [MAX_FACES] faces tracked at once.
 *           iOS (ARKit): ARKit only supports one face; values greater than 1 have no effect.
 *           Default is 1 (single-face mode).
 * @property minDetectionConfidence Minimum confidence score for face detection to be considered
 *           successful. Android (MediaPipe) only — has no effect on iOS (ARKit). Must be in 0.0..1.0.
 * @property minTrackingConfidence Minimum confidence score for face tracking to be considered
 *           successful. Android (MediaPipe) only — has no effect on iOS (ARKit). Must be in 0.0..1.0.
 * @property minPresenceConfidence Minimum confidence score for face presence to be considered
 *           valid. Android (MediaPipe) only — has no effect on iOS (ARKit). Must be in 0.0..1.0.
 * @property thermalPolicy Policy controlling how the tracker adapts to thermal pressure.
 *           Defaults to [ThermalPolicy.Disabled].
 *           **Ignored when [powerMode] is set** — [PowerMode] provides a higher-level
 *           abstraction that subsumes thermal policy.
 * @property powerMode Power management profile controlling frame rate, thermal adaptation,
 *           idle detection, and battery-aware downgrade. When non-null, overrides [thermalPolicy].
 *           Defaults to null (legacy behavior using [thermalPolicy]).
 * @property lowBatteryThreshold Battery level (0.0–1.0) at which the tracker auto-downgrades
 *           the power mode by one step. Only effective when [powerMode] is set.
 *           Defaults to 0.15 (15%).
 * @property enableIdleDetection Whether to reduce frame rate when the face is stationary.
 *           Only effective when [powerMode] is set. Defaults to true.
 * @property analysisWidth Width of the camera analysis stream in pixels. Android only.
 *           Lower values improve performance at the cost of accuracy. Defaults to 640.
 * @property analysisHeight Height of the camera analysis stream in pixels. Android only.
 *           Lower values improve performance at the cost of accuracy. Defaults to 480.
 */
public data class FaceTrackerConfig(
    val smoothingConfig: SmoothingConfig = SmoothingConfig.Ema(),
    val enhancerConfig: BlendShapeEnhancerConfig = BlendShapeEnhancerConfig.Default(),
    val calibrationConfig: CalibrationConfig = CalibrationConfig(),
    val cameraFacing: CameraFacing = CameraFacing.FRONT,
    val modelAssetPath: String = "models/face_landmarker_v2_with_blendshapes.task",
    val stopDrainDelayMs: Long = 100L,
    val releaseTimeoutMs: Long = 500L,
    val maxFaces: Int = 1,
    val minDetectionConfidence: Float = DEFAULT_CONFIDENCE,
    val minTrackingConfidence: Float = DEFAULT_CONFIDENCE,
    val minPresenceConfidence: Float = DEFAULT_CONFIDENCE,
    val thermalPolicy: ThermalPolicy = ThermalPolicy.Disabled,
    val powerMode: PowerMode? = null,
    val lowBatteryThreshold: Float = DEFAULT_LOW_BATTERY_THRESHOLD,
    val enableIdleDetection: Boolean = true,
    val analysisWidth: Int = DEFAULT_ANALYSIS_WIDTH,
    val analysisHeight: Int = DEFAULT_ANALYSIS_HEIGHT,
) {
    init {
        require(maxFaces in 1..MAX_FACES) {
            "maxFaces must be in 1..$MAX_FACES, was $maxFaces"
        }
        require(minDetectionConfidence in 0f..1f) {
            "minDetectionConfidence must be in 0.0..1.0, was $minDetectionConfidence"
        }
        require(minTrackingConfidence in 0f..1f) {
            "minTrackingConfidence must be in 0.0..1.0, was $minTrackingConfidence"
        }
        require(minPresenceConfidence in 0f..1f) {
            "minPresenceConfidence must be in 0.0..1.0, was $minPresenceConfidence"
        }
        require(lowBatteryThreshold in 0f..1f) {
            "lowBatteryThreshold must be in 0.0..1.0, was $lowBatteryThreshold"
        }
        require(analysisWidth > 0) { "analysisWidth must be positive, was $analysisWidth" }
        require(analysisHeight > 0) { "analysisHeight must be positive, was $analysisHeight" }
    }

    public companion object {
        /** Maximum number of faces that can be tracked simultaneously. */
        public const val MAX_FACES: Int = 4

        /** Default MediaPipe confidence threshold. */
        public const val DEFAULT_CONFIDENCE: Float = 0.5f

        /** Default battery level at which power mode auto-downgrades (15%). */
        public const val DEFAULT_LOW_BATTERY_THRESHOLD: Float = 0.15f

        /** Default camera analysis stream width in pixels. */
        public const val DEFAULT_ANALYSIS_WIDTH: Int = 640

        /** Default camera analysis stream height in pixels. */
        public const val DEFAULT_ANALYSIS_HEIGHT: Int = 480
    }
}

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
