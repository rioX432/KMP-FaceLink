package io.github.kmpfacelink.voice.lipsync.internal

import io.github.kmpfacelink.model.BlendShape

private const val EPSILON = 0.001f

/**
 * Applies exponential moving average smoothing to blend shape values across frames.
 *
 * Prevents jarring transitions by interpolating between the previous frame
 * and the target values using a configurable smoothing factor.
 */
internal class TemporalSmoother(private val smoothing: Float) {

    private val previous = mutableMapOf<BlendShape, Float>()

    /**
     * Smooths the given blend shape values against the previous frame.
     *
     * @param target Target blend shape values for this frame
     * @return Smoothed blend shape values
     */
    fun smooth(target: Map<BlendShape, Float>): Map<BlendShape, Float> {
        val alpha = 1f - smoothing
        val allKeys = previous.keys + target.keys
        val result = mutableMapOf<BlendShape, Float>()

        for (key in allKeys) {
            val prev = previous[key] ?: 0f
            val tgt = target[key] ?: 0f
            val smoothed = prev + alpha * (tgt - prev)
            if (smoothed > EPSILON) {
                result[key] = smoothed
            }
        }

        previous.clear()
        previous.putAll(result)

        return result
    }

    /** Resets the smoother state. */
    fun reset() {
        previous.clear()
    }
}
