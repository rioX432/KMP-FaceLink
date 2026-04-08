package io.github.kmpfacelink.util

/**
 * Streaming smoothness metric based on the Sum of Squared Jerks (SSJ).
 *
 * Jerk is the third derivative of position with respect to time and is the
 * standard perceptual measure of motion smoothness: a minimum-jerk trajectory
 * appears visually clean while a high-jerk trajectory shows as jitter or
 * twitchiness. Lower SSJ values indicate smoother motion.
 *
 * This implementation works on any univariate signal (for example, a single
 * blend-shape channel or a single Euler axis of the head transform). Feed it
 * sample-by-sample via [record] and call [ssj] to read the current SSJ value
 * over the configured sliding window. The metric requires at least
 * [MINIMUM_SAMPLES_FOR_JERK] samples before it produces a non-zero result.
 *
 * Intended use cases:
 * - Debugging smoothing filters by logging SSJ per blend shape during
 *   development.
 * - Comparing two filter configurations on the same recorded session.
 * - Unit tests that assert smoothness behaviour of new filters.
 *
 * Not thread-safe — intended for single-coroutine use like the other
 * per-frame instrumentation classes in this package.
 */
internal class SmoothnessMetric(
    private val windowSize: Int = DEFAULT_WINDOW_SIZE,
) {

    init {
        require(windowSize >= MINIMUM_SAMPLES_FOR_JERK) {
            "windowSize must be >= $MINIMUM_SAMPLES_FOR_JERK to compute jerk, was $windowSize"
        }
    }

    /** Ring buffer of recent sample values. */
    private val samples = FloatArray(windowSize)

    /** Total number of samples observed (may exceed [windowSize]). */
    private var count = 0

    /**
     * Records one sample of the underlying signal.
     *
     * Samples are assumed to arrive at a roughly constant frame rate — [ssj]
     * returns a value in squared signal units per (frame-interval)^6 rather
     * than a time-normalised jerk.
     */
    fun record(sample: Float) {
        samples[count % windowSize] = sample
        count++
    }

    /**
     * Returns the sum of squared jerks over the current window.
     *
     * Returns `0f` until [MINIMUM_SAMPLES_FOR_JERK] samples have been recorded,
     * because jerk requires three successive second differences.
     */
    fun ssj(): Float = computeJerkAccumulator().sum

    /**
     * Returns the mean squared jerk over the current window, or `0f` when
     * fewer than [MINIMUM_SAMPLES_FOR_JERK] samples have been recorded.
     */
    fun meanSquaredJerk(): Float {
        val accumulator = computeJerkAccumulator()
        if (accumulator.sampleCount <= 0) return 0f
        return accumulator.sum / accumulator.sampleCount.toFloat()
    }

    /**
     * Walks the current window once and returns the sum of squared jerks and
     * the number of jerk samples contributing to it. Both [ssj] and
     * [meanSquaredJerk] share this pass to avoid iterating the buffer twice.
     */
    private fun computeJerkAccumulator(): JerkAccumulator {
        if (count < MINIMUM_SAMPLES_FOR_JERK) return JerkAccumulator(0f, 0)
        val validLength = if (count < windowSize) count else windowSize
        // Produces `validLength - JERK_KERNEL_WIDTH` jerk samples.
        var sum = 0f
        var sampleCount = 0
        for (offset in JERK_KERNEL_WIDTH until validLength) {
            val jerk = jerkAt(offset)
            sum += jerk * jerk
            sampleCount++
        }
        return JerkAccumulator(sum, sampleCount)
    }

    /** Intermediate result shared between [ssj] and [meanSquaredJerk]. */
    private data class JerkAccumulator(val sum: Float, val sampleCount: Int)

    /** Resets the metric to the empty state. */
    fun reset() {
        count = 0
        for (i in samples.indices) {
            samples[i] = 0f
        }
    }

    /**
     * Computes the discrete jerk `j[n] = x[n] - 3*x[n-1] + 3*x[n-2] - x[n-3]`
     * at window offset [offset] using the ring buffer.
     *
     * `offset` is the logical position in the current window, where `0` refers
     * to the oldest sample still in the window and `validLength - 1` to the
     * most recently recorded sample.
     */
    private fun jerkAt(offset: Int): Float {
        val baseIndex = resolveBaseIndex()
        val x0 = samples[(baseIndex + offset - JERK_KERNEL_WIDTH + windowSize) % windowSize]
        val x1 = samples[(baseIndex + offset - JERK_KERNEL_WIDTH + 1 + windowSize) % windowSize]
        val x2 = samples[(baseIndex + offset - JERK_KERNEL_WIDTH + 2 + windowSize) % windowSize]
        val x3 = samples[(baseIndex + offset + windowSize) % windowSize]
        return x3 - JERK_COEFFICIENT * x2 + JERK_COEFFICIENT * x1 - x0
    }

    /**
     * Returns the ring-buffer index that corresponds to the oldest sample
     * still inside the current window.
     */
    private fun resolveBaseIndex(): Int {
        if (count < windowSize) return 0
        return count % windowSize
    }

    internal companion object {
        internal const val DEFAULT_WINDOW_SIZE = 30
        internal const val MINIMUM_SAMPLES_FOR_JERK = 4
        private const val JERK_KERNEL_WIDTH = 3

        /**
         * Coefficient of the middle terms of the discrete third-difference kernel
         * `x[n] - 3·x[n-1] + 3·x[n-2] - x[n-3]`, which is the discrete analogue of
         * the third derivative (jerk) of the input signal.
         */
        private const val JERK_COEFFICIENT = 3f
    }
}
