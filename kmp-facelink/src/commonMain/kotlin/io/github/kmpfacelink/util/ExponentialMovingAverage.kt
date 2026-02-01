package io.github.kmpfacelink.util

import io.github.kmpfacelink.model.BlendShape
import io.github.kmpfacelink.model.BlendShapeData

/**
 * Exponential Moving Average (EMA) smoother for blend shape data.
 *
 * Applies temporal smoothing to reduce jitter:
 *   smoothed = alpha * newValue + (1 - alpha) * previousSmoothed
 *
 * @param alpha Smoothing factor (0.0–1.0). Higher = less smoothing, lower = more smoothing.
 */
internal class ExponentialMovingAverage(private val alpha: Float = 0.5f) : BlendShapeSmoother {

    private var previousValues: MutableMap<BlendShape, Float>? = null

    /**
     * Apply EMA smoothing to the given blend shape data.
     * First call returns the input as-is (no previous data to smooth against).
     */
    fun smooth(data: BlendShapeData): BlendShapeData {
        val prev = previousValues
        if (prev == null) {
            previousValues = data.toMutableMap()
            return data
        }

        val smoothed = mutableMapOf<BlendShape, Float>()
        for ((shape, value) in data) {
            val prevValue = prev[shape] ?: value
            val smoothedValue = alpha * value + (1f - alpha) * prevValue
            smoothed[shape] = smoothedValue
            prev[shape] = smoothedValue
        }
        return smoothed
    }

    override fun smooth(data: BlendShapeData, timestampMs: Long): BlendShapeData = smooth(data)

    override fun reset() {
        previousValues = null
    }
}
