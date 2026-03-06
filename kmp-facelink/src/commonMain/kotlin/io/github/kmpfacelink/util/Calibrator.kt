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
 * Neutral baseline detection: once the user holds a stable expression for
 * [neutralWindowSize] consecutive frames (low variance), that average is stored as the
 * neutral baseline and subtracted from all subsequent normalized values so that the resting face
 * maps to 0 for every shape.
 *
 * @param warmupFrames Number of frames to collect statistics before normalization begins.
 * @param decayRate EMA decay rate applied to the observed min/max range each frame.
 * @param neutralWindowSize Number of consecutive post-warmup frames used to detect neutral.
 */
internal class Calibrator(
    private val warmupFrames: Int = 60,
    private val decayRate: Float = 0.001f,
    neutralWindowSize: Int = 30,
) {

    private val minValues = mutableMapOf<BlendShape, Float>()
    private val maxValues = mutableMapOf<BlendShape, Float>()
    private var frameCount = 0

    private val neutralDetector = NeutralDetector(windowSize = neutralWindowSize)
    private var neutralBaseline: BlendShapeData? = null

    /**
     * Update observed ranges and return calibrated blend shape data.
     *
     * During the warmup phase, raw values are returned unchanged.
     * After warmup, values are normalized to [0.0, 1.0] based on the EMA-tracked range,
     * with the neutral baseline subtracted so that the resting face registers as 0.
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
                val neutralValue = neutralBaseline?.get(shape) ?: 0f
                if (range > EPSILON) {
                    ((value - neutralValue) / range).coerceIn(0f, 1f)
                } else {
                    value
                }
            }
        }

        // Feed post-warmup frames to neutral detector; update baseline when stable
        if (!isWarmup) {
            val detected = neutralDetector.addFrame(data)
            if (detected != null) {
                neutralBaseline = detected
            }
        }

        return calibrated
    }

    fun reset() {
        minValues.clear()
        maxValues.clear()
        frameCount = 0
        neutralDetector.reset()
        neutralBaseline = null
    }

    /**
     * Detects a neutral (resting) expression by monitoring variance of blend shape values
     * over a sliding window. When the variance falls below [NEUTRAL_VARIANCE_THRESHOLD], the
     * window average is stored as the neutral baseline.
     */
    private class NeutralDetector(private val windowSize: Int = 30) {

        private val history = ArrayDeque<BlendShapeData>(windowSize)
        private var detectedNeutral: BlendShapeData? = null

        fun addFrame(data: BlendShapeData): BlendShapeData? {
            history.addLast(data)
            if (history.size > windowSize) history.removeFirst()
            if (history.size < windowSize) return detectedNeutral
            val variance = computeAverageVariance()
            if (variance < NEUTRAL_VARIANCE_THRESHOLD) {
                detectedNeutral = computeAverage()
            }
            return detectedNeutral
        }

        fun reset() {
            history.clear()
            detectedNeutral = null
        }

        private fun computeAverageVariance(): Float {
            val shapes = history.first().keys
            if (shapes.isEmpty()) return 0f
            var totalVariance = 0f
            for (shape in shapes) {
                val values = history.map { it[shape] ?: 0f }
                val mean = values.sum() / values.size
                val variance = values.sumOf { v -> ((v - mean) * (v - mean)).toDouble() }.toFloat() / values.size
                totalVariance += variance
            }
            return totalVariance / shapes.size
        }

        private fun computeAverage(): BlendShapeData {
            val shapes = history.first().keys
            return shapes.associateWith { shape ->
                val values = history.map { it[shape] ?: 0f }
                values.sum() / values.size
            }
        }

        private companion object {
            const val NEUTRAL_VARIANCE_THRESHOLD = 0.001f
        }
    }

    private companion object {
        const val EPSILON = 0.01f
    }
}
