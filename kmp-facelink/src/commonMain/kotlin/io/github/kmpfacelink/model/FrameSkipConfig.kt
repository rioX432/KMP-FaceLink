package io.github.kmpfacelink.model

/**
 * Aggressiveness level for confidence-guided frame skipping.
 *
 * Controls how aggressively frames are skipped when tracking confidence is high.
 * Higher aggressiveness skips more frames, reducing compute at the cost of accuracy.
 *
 * @property confidenceThreshold Minimum confidence to allow skipping.
 * @property maxConsecutiveSkips Maximum frames that can be skipped consecutively.
 * @property movementThreshold Maximum blend shape delta to consider stable enough to skip.
 */
public enum class FrameSkipLevel(
    public val confidenceThreshold: Float,
    public val maxConsecutiveSkips: Int,
    public val movementThreshold: Float,
) {
    /** Skip at most 1 frame, only when very stable. ~95%+ accuracy. */
    CONSERVATIVE(
        confidenceThreshold = 0.9f,
        maxConsecutiveSkips = 1,
        movementThreshold = 0.015f,
    ),

    /** Skip up to 2 frames when stable. Good accuracy/compute trade-off. */
    BALANCED(
        confidenceThreshold = 0.8f,
        maxConsecutiveSkips = 2,
        movementThreshold = 0.025f,
    ),

    /** Skip up to 3 frames when stable. Maximum compute savings. */
    AGGRESSIVE(
        confidenceThreshold = 0.7f,
        maxConsecutiveSkips = 3,
        movementThreshold = 0.04f,
    ),
}

/**
 * Configuration for confidence-guided frame skipping.
 *
 * When enabled, the tracker evaluates each frame's confidence and movement delta
 * to decide whether full detection is needed or a lightweight interpolation can be used.
 *
 * @property enabled Whether confidence-guided frame skipping is active.
 * @property level Aggressiveness level controlling skip behavior.
 */
public data class FrameSkipConfig(
    val enabled: Boolean = false,
    val level: FrameSkipLevel = FrameSkipLevel.BALANCED,
)
