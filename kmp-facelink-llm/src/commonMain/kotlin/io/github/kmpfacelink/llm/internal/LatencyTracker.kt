package io.github.kmpfacelink.llm.internal

/**
 * Tracks on-device inference latency using a sliding window of recent measurements.
 *
 * Provides average latency and a recommendation on whether to prefer cloud based
 * on whether the average exceeds a given timeout threshold.
 *
 * @param windowSize Number of recent latency samples to retain.
 */
internal class LatencyTracker(
    private val windowSize: Int = DEFAULT_WINDOW_SIZE,
) {
    private val samples = ArrayDeque<Long>(windowSize)

    /** Records a latency measurement in milliseconds. */
    internal fun record(latencyMs: Long) {
        if (samples.size >= windowSize) {
            samples.removeFirst()
        }
        samples.addLast(latencyMs)
    }

    /**
     * Average latency of recent samples in milliseconds.
     * Returns 0 if no samples have been recorded.
     */
    internal val averageLatencyMs: Long
        get() = if (samples.isEmpty()) 0L else samples.sum() / samples.size

    /** Returns true if average latency exceeds the given timeout. */
    internal fun exceedsTimeout(timeoutMs: Long): Boolean {
        if (samples.isEmpty()) return false
        return averageLatencyMs > timeoutMs
    }

    /** Number of recorded samples. */
    internal val sampleCount: Int get() = samples.size

    /** Clears all recorded samples. */
    internal fun reset() {
        samples.clear()
    }

    internal companion object {
        internal const val DEFAULT_WINDOW_SIZE = 10
    }
}
