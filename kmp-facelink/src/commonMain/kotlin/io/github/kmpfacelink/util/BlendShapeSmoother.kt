package io.github.kmpfacelink.util

import io.github.kmpfacelink.model.BlendShapeData
import io.github.kmpfacelink.model.FaceTrackerConfig
import io.github.kmpfacelink.model.SmoothingConfig

/**
 * Common interface for blend shape smoothing filters.
 */
internal interface BlendShapeSmoother {
    /**
     * Apply smoothing to the given blend shape data.
     *
     * @param data Raw blend shape values for the current frame
     * @param timestampMs Frame timestamp in milliseconds (used by frequency-aware filters)
     * @return Smoothed blend shape values
     */
    fun smooth(data: BlendShapeData, timestampMs: Long): BlendShapeData

    /** Reset internal state (e.g. after tracking loss). */
    fun reset()
}

/**
 * Create a [BlendShapeSmoother] from this config's [SmoothingConfig],
 * or null for [SmoothingConfig.None].
 */
internal fun FaceTrackerConfig.createSmoother(): BlendShapeSmoother? =
    smoothingConfig.createFaceSmoother()

/**
 * Create a [BlendShapeSmoother] from [SmoothingConfig], or null for [SmoothingConfig.None].
 */
internal fun SmoothingConfig.createFaceSmoother(): BlendShapeSmoother? = when (this) {
    is SmoothingConfig.None -> null
    is SmoothingConfig.Ema -> ExponentialMovingAverage(alpha)
    is SmoothingConfig.OneEuro -> OneEuroFilter(minCutoff, beta, dCutoff, predictionMs)
    is SmoothingConfig.CategoryAware -> CategoryAwareSmoother(this)
    is SmoothingConfig.NeuroPredictive -> NeuroPredictorSmoother(windowSize)
}
