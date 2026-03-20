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

    // Pre-allocated scratch map reused each frame to avoid per-call allocations.
    // Callers that store the result (e.g. FaceTrackingData) must copy it via HashMap(result).
    private val scratch: HashMap<BlendShape, Float> = BlendShapeMapPool().create()

    /**
     * Apply EMA smoothing to the given blend shape data.
     * First call returns the input as-is (no previous data to smooth against).
     *
     * The returned map is the internal scratch buffer — copy it if you need to hold a reference
     * across the next [smooth] call.
     */
    fun smooth(data: BlendShapeData): BlendShapeData {
        val prev = previousValues
        if (prev == null) {
            previousValues = data.toMutableMap()
            return data
        }

        scratch.clear()
        for ((shape, value) in data) {
            val prevValue = prev[shape] ?: value
            val smoothedValue = alpha * value + (1f - alpha) * prevValue
            scratch[shape] = smoothedValue
            prev[shape] = smoothedValue
        }
        return scratch
    }

    override fun smooth(data: BlendShapeData, timestampMs: Long): BlendShapeData = smooth(data)

    override fun reset() {
        previousValues = null
    }
}
