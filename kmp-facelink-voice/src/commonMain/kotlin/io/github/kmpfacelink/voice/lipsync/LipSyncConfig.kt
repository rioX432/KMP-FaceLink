package io.github.kmpfacelink.voice.lipsync

private const val DEFAULT_SMOOTHING = 0.3f
private const val DEFAULT_AMPLITUDE_SCALE = 1.0f
private const val DEFAULT_TARGET_FPS = 60

/**
 * Configuration for lip sync animation.
 *
 * @property smoothing Smoothing factor for blend shape transitions (0.0 = no smoothing, 1.0 = max)
 * @property amplitudeScale Scale factor for amplitude-based animation
 * @property targetFps Target frame rate for animation output
 */
public data class LipSyncConfig(
    val smoothing: Float = DEFAULT_SMOOTHING,
    val amplitudeScale: Float = DEFAULT_AMPLITUDE_SCALE,
    val targetFps: Int = DEFAULT_TARGET_FPS,
)
