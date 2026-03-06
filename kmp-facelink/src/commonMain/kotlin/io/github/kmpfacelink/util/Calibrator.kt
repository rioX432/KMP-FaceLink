package io.github.kmpfacelink.util

import io.github.kmpfacelink.model.BlendShape
import io.github.kmpfacelink.model.BlendShapeData
import kotlin.math.max
import kotlin.math.min

/**
 * Per-session calibration that learns per-user min/max ranges and normalizes values.
 *
 * During the warmup phase ([warmupFrames] frames), raw values are passed through unchanged while
 * observed min/max ranges are collected. After warmup, values are remapped from
 * [observedMin, observedMax] to [0.0, 1.0].
 *
 * The observed range decays toward the current value over time using an EMA with [decayRate],
 * allowing the range to slowly shrink after outliers and recover dynamic range.
 *
 * @param warmupFrames Number of frames to collect statistics before normalization begins.
 * @param decayRate EMA decay rate applied to the observed min/max range each frame.
 */
internal class Calibrator(
    private val warmupFrames: Int = 60,
    private val decayRate: Float = 0.001f,
) {

    private val minValues = mutableMapOf<BlendShape, Float>()
    private val maxValues = mutableMapOf<BlendShape, Float>()
    private var frameCount = 0

    /**
     * Update observed ranges and return calibrated blend shape data.
     *
     * During the warmup phase, raw values are returned unchanged.
     * After warmup, values are normalized to [0.0, 1.0] based on the EMA-tracked range.
     */
    fun calibrate(data: BlendShapeData): BlendShapeData {
        frameCount++
        val isWarmup = frameCount <= warmupFrames

        val calibrated = mutableMapOf<BlendShape, Float>()
        for ((shape, value) in data) {
            val currentMin = minValues[shape]
            val currentMax = maxValues[shape]

            val newMin: Float
            val newMax: Float
            if (currentMin == null || currentMax == null) {
                newMin = value
                newMax = value
            } else {
                // EMA-based range decay: range slowly shrinks toward current values after outliers
                newMin = (1f - decayRate) * currentMin + decayRate * min(currentMin, value)
                newMax = (1f - decayRate) * currentMax + decayRate * max(currentMax, value)
            }
            minValues[shape] = newMin
            maxValues[shape] = newMax

            calibrated[shape] = if (isWarmup) {
                value
            } else {
                val range = newMax - newMin
                if (range > EPSILON) {
                    ((value - newMin) / range).coerceIn(0f, 1f)
                } else {
                    value
                }
            }
        }
        return calibrated
    }

    fun reset() {
        minValues.clear()
        maxValues.clear()
        frameCount = 0
    }

    private companion object {
        const val EPSILON = 0.01f
    }
}
