package io.github.kmpfacelink.util

/**
 * Tracks real-time performance metrics for a tracking pipeline.
 *
 * Maintains two fixed-size ring buffers:
 * - Frame timestamps, used to compute FPS over the last [windowSize] frames.
 * - Latency measurements, used to compute average and p95 latency.
 *
 * All public methods are thread-safe via [PlatformLock].
 *
 * Usage:
 * ```kotlin
 * val monitor = PerformanceMonitor()
 * tracker.trackingData.collect { data ->
 *     val start = currentTimeMs()
 *     process(data)
 *     monitor.recordFrame(data.timestampMs)
 *     monitor.recordLatency((currentTimeMs() - start).toDouble())
 * }
 * val metrics = monitor.getMetrics()
 * ```
 *
 * @param windowSize Number of recent frames to keep in the ring buffers. Must be >= 2.
 */
public class PerformanceMonitor(
    private val windowSize: Int = DEFAULT_WINDOW_SIZE,
) {

    private val lock = PlatformLock()

    // Ring buffer for frame timestamps (milliseconds)
    private val timestamps = LongArray(windowSize)
    private var timestampHead = 0
    private var timestampCount = 0

    // Ring buffer for latency samples (milliseconds)
    private val latencies = DoubleArray(windowSize)
    private var latencyHead = 0
    private var latencyCount = 0

    // Total counters (not bounded by the window)
    private var totalFrames = 0L
    private var totalDropped = 0L
    private var lastRecordedTimestamp = INVALID_TIMESTAMP

    /**
     * Records a frame emission at [timestampMs].
     *
     * Consecutive calls with the same [timestampMs] value are counted as dropped frames
     * and do not advance the FPS ring buffer.
     */
    public fun recordFrame(timestampMs: Long) {
        lock.withLock {
            totalFrames++
            if (timestampMs == lastRecordedTimestamp) {
                totalDropped++
                return
            }
            lastRecordedTimestamp = timestampMs
            timestamps[timestampHead] = timestampMs
            timestampHead = (timestampHead + 1) % windowSize
            if (timestampCount < windowSize) timestampCount++
        }
    }

    /**
     * Records a processing latency sample in milliseconds.
     */
    public fun recordLatency(latencyMs: Double) {
        lock.withLock {
            latencies[latencyHead] = latencyMs
            latencyHead = (latencyHead + 1) % windowSize
            if (latencyCount < windowSize) latencyCount++
        }
    }

    /**
     * Returns a snapshot of the current performance statistics.
     */
    public fun getMetrics(): PerformanceMetrics {
        lock.withLock {
            return PerformanceMetrics(
                fps = computeFps(),
                averageLatencyMs = computeAverageLatency(),
                p95LatencyMs = computeP95Latency(),
                frameCount = totalFrames,
                droppedFrames = totalDropped,
            )
        }
    }

    /**
     * Clears all recorded measurements and resets counters to zero.
     */
    public fun reset() {
        lock.withLock {
            timestamps.fill(0L)
            timestampHead = 0
            timestampCount = 0
            latencies.fill(0.0)
            latencyHead = 0
            latencyCount = 0
            totalFrames = 0L
            totalDropped = 0L
            lastRecordedTimestamp = INVALID_TIMESTAMP
        }
    }

    // ── Private helpers ──────────────────────────────────────────────────────

    private fun computeFps(): Double {
        if (timestampCount < MIN_FRAMES_FOR_FPS) return 0.0
        val oldest = timestamps[(timestampHead - timestampCount + windowSize) % windowSize]
        val newest = timestamps[(timestampHead - 1 + windowSize) % windowSize]
        val elapsedSeconds = (newest - oldest) / MS_PER_SECOND
        if (elapsedSeconds <= 0.0) return 0.0
        return (timestampCount - 1) / elapsedSeconds
    }

    private fun computeAverageLatency(): Double {
        if (latencyCount == 0) return 0.0
        var sum = 0.0
        for (i in 0 until latencyCount) {
            sum += latencies[(latencyHead - latencyCount + i + windowSize) % windowSize]
        }
        return sum / latencyCount
    }

    private fun computeP95Latency(): Double {
        if (latencyCount == 0) return 0.0
        val snapshot = DoubleArray(latencyCount) { i ->
            latencies[(latencyHead - latencyCount + i + windowSize) % windowSize]
        }
        snapshot.sort()
        val index = (latencyCount * P95_RATIO).toInt().coerceIn(0, latencyCount - 1)
        return snapshot[index]
    }

    public companion object {
        /** Default ring buffer size (120 frames ≈ 2 seconds at 60 fps). */
        public const val DEFAULT_WINDOW_SIZE: Int = 120

        private const val MIN_FRAMES_FOR_FPS = 2
        private const val MS_PER_SECOND = 1000.0
        private const val P95_RATIO = 0.95
        private const val INVALID_TIMESTAMP = Long.MIN_VALUE
    }
}
