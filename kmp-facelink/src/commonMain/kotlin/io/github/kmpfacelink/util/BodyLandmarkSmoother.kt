package io.github.kmpfacelink.util

import io.github.kmpfacelink.model.SmoothingConfig
import io.github.kmpfacelink.model.TrackedBody

/**
 * Common interface for body landmark smoothing filters.
 */
internal interface BodyLandmarkSmoother {
    /**
     * Apply smoothing to the tracked bodies.
     *
     * @param bodies Raw tracked body data for the current frame
     * @param timestampMs Frame timestamp in milliseconds
     * @return Smoothed tracked body data
     */
    fun smooth(bodies: List<TrackedBody>, timestampMs: Long): List<TrackedBody>

    /** Reset internal state (e.g. after tracking loss). */
    fun reset()
}

/**
 * Create a [BodyLandmarkSmoother] from [SmoothingConfig], or null for [SmoothingConfig.None].
 */
internal fun SmoothingConfig.createBodySmoother(): BodyLandmarkSmoother? = when (this) {
    is SmoothingConfig.None -> null
    is SmoothingConfig.Ema -> BodyEmaFilter(alpha)
    is SmoothingConfig.OneEuro -> BodyOneEuroFilter(minCutoff, beta, dCutoff)
}
