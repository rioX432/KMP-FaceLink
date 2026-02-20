package io.github.kmpfacelink.util

import io.github.kmpfacelink.model.BlendShape
import io.github.kmpfacelink.model.BlendShapeData
import kotlin.math.PI
import kotlin.math.abs

/**
 * One Euro Filter for blend shape smoothing.
 *
 * An adaptive low-pass filter that adjusts its cutoff frequency based on signal speed:
 * slow movements get heavy smoothing (low jitter), fast movements pass through
 * with minimal lag.
 *
 * Optionally predicts future values by extrapolating the smoothed derivative forward
 * by [predictionMs] milliseconds.
 *
 * Reference: Casiez et al., "1€ Filter: A Simple Speed-based Low-pass Filter
 * for Noisy Input in Interactive Systems", CHI 2012.
 *
 * @param minCutoff Minimum cutoff frequency in Hz. Lower = more smoothing at rest.
 * @param beta Speed coefficient. Higher = less lag during fast movements.
 * @param dCutoff Cutoff frequency for the derivative filter.
 * @param predictionMs Prediction horizon in milliseconds. 0 = no prediction.
 */
internal class OneEuroFilter(
    private val minCutoff: Float = 1.0f,
    private val beta: Float = 0.007f,
    private val dCutoff: Float = 1.0f,
    private val predictionMs: Float = 0f,
) : BlendShapeSmoother {

    private var lastTimestampMs: Long = -1L
    private val states = mutableMapOf<BlendShape, ShapeState>()

    override fun smooth(data: BlendShapeData, timestampMs: Long): BlendShapeData {
        // First frame or same timestamp — pass through
        if (lastTimestampMs < 0 || timestampMs <= lastTimestampMs) {
            lastTimestampMs = timestampMs
            val result = HashMap<BlendShape, Float>(data.size)
            for ((shape, value) in data) {
                states[shape] = ShapeState(value, 0f)
                result[shape] = value
            }
            return result
        }

        val dt = (timestampMs - lastTimestampMs) / MILLIS_PER_SECOND
        lastTimestampMs = timestampMs
        val predictionSeconds = predictionMs / MILLIS_PER_SECOND

        val result = HashMap<BlendShape, Float>(data.size)
        for ((shape, rawValue) in data) {
            val state = states[shape]
            if (state == null) {
                // New shape seen for the first time
                states[shape] = ShapeState(rawValue, 0f)
                result[shape] = rawValue
                continue
            }

            // Derivative estimation
            val dx = (rawValue - state.x) / dt
            val edx = lowPass(dx, state.dx, smoothingFactor(dt, dCutoff))

            // Adaptive cutoff
            val cutoff = minCutoff + beta * abs(edx)
            val filteredValue = lowPass(rawValue, state.x, smoothingFactor(dt, cutoff))

            states[shape] = ShapeState(filteredValue, edx)

            // Prediction: extrapolate forward using smoothed derivative
            val outputValue = if (predictionSeconds > 0f) {
                (filteredValue + edx * predictionSeconds).coerceIn(0f, 1f)
            } else {
                filteredValue
            }

            result[shape] = outputValue
        }

        return result
    }

    override fun reset() {
        states.clear()
        lastTimestampMs = -1L
    }

    private fun smoothingFactor(dt: Float, cutoff: Float): Float {
        val r = 2 * PI.toFloat() * cutoff * dt
        return r / (r + 1f)
    }

    private fun lowPass(x: Float, xPrev: Float, alpha: Float): Float =
        alpha * x + (1f - alpha) * xPrev

    private data class ShapeState(val x: Float, val dx: Float)

    companion object {
        private const val MILLIS_PER_SECOND = 1000f
    }
}
