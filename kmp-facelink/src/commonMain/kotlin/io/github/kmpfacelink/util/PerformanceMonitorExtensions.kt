package io.github.kmpfacelink.util

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.onEach

/**
 * Wraps a tracking data flow with automatic performance monitoring.
 *
 * Each emission records a frame timestamp on the provided [monitor] using
 * the [timestampExtractor] function to extract the timestamp from the emitted value.
 *
 * Example:
 * ```kotlin
 * val monitor = PerformanceMonitor()
 * tracker.trackingData
 *     .withPerformanceMonitor(monitor) { it.timestampMs }
 *     .collect { data -> render(data) }
 * val metrics = monitor.getMetrics()
 * ```
 *
 * @param monitor The [PerformanceMonitor] to record frames on.
 * @param timestampExtractor Extracts the frame timestamp in milliseconds from each emission.
 * @return A [Flow] that emits the same values as the receiver, with a side-effect of recording
 *         each frame timestamp on [monitor].
 */
public fun <T> Flow<T>.withPerformanceMonitor(
    monitor: PerformanceMonitor,
    timestampExtractor: (T) -> Long,
): Flow<T> = onEach { value ->
    monitor.recordFrame(timestampExtractor(value))
}
