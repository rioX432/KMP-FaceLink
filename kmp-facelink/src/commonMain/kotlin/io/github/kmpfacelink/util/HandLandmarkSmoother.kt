package io.github.kmpfacelink.util

import io.github.kmpfacelink.model.SmoothingConfig
import io.github.kmpfacelink.model.TrackedHand

/**
 * Common interface for hand landmark smoothing filters.
 */
internal interface HandLandmarkSmoother {
    /**
     * Apply smoothing to the tracked hands.
     *
     * @param hands Raw tracked hand data for the current frame
     * @param timestampMs Frame timestamp in milliseconds
     * @return Smoothed tracked hand data
     */
    fun smooth(hands: List<TrackedHand>, timestampMs: Long): List<TrackedHand>

    /** Reset internal state (e.g. after tracking loss). */
    fun reset()
}

/**
 * Create a [HandLandmarkSmoother] from [SmoothingConfig], or null for [SmoothingConfig.None].
 */
internal fun SmoothingConfig.createHandSmoother(): HandLandmarkSmoother? = when (this) {
    is SmoothingConfig.None -> null
    is SmoothingConfig.Ema -> HandEmaFilter(alpha)
    is SmoothingConfig.OneEuro -> HandOneEuroFilter(minCutoff, beta, dCutoff)
}
