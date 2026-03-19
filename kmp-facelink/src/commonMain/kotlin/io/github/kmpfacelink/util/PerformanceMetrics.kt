package io.github.kmpfacelink.util

/**
 * Snapshot of tracking performance statistics.
 *
 * @property fps Average frames per second over the measurement window.
 * @property averageLatencyMs Average frame processing latency in milliseconds.
 * @property p95LatencyMs 95th percentile latency in milliseconds.
 * @property frameCount Total frames processed since last reset.
 * @property droppedFrames Number of frames skipped due to backpressure.
 */
public data class PerformanceMetrics(
    val fps: Double,
    val averageLatencyMs: Double,
    val p95LatencyMs: Double,
    val frameCount: Long,
    val droppedFrames: Long,
)
