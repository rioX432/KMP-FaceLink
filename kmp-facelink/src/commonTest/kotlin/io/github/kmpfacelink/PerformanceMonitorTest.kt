package io.github.kmpfacelink

import io.github.kmpfacelink.util.PerformanceMonitor
import io.github.kmpfacelink.util.withPerformanceMonitor
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PerformanceMonitorTest {

    // ── Empty state ──────────────────────────────────────────────────────────

    @Test
    fun emptyMonitorReturnsZeroMetrics() {
        val monitor = PerformanceMonitor()
        val metrics = monitor.getMetrics()

        assertEquals(0.0, metrics.fps)
        assertEquals(0.0, metrics.averageLatencyMs)
        assertEquals(0.0, metrics.p95LatencyMs)
        assertEquals(0L, metrics.frameCount)
        assertEquals(0L, metrics.droppedFrames)
    }

    // ── FPS calculation ──────────────────────────────────────────────────────

    @Test
    fun fpsCalculationWithKnownInterval() {
        val monitor = PerformanceMonitor()
        // 10 frames at 100 ms intervals → 9 intervals → 9 / 0.9s = 10 fps
        for (i in 0 until 10) {
            monitor.recordFrame(i * 100L)
        }
        val metrics = monitor.getMetrics()
        assertApproxDouble(10.0, metrics.fps, epsilon = 0.1)
    }

    @Test
    fun singleFrameReturnsFpsZero() {
        val monitor = PerformanceMonitor()
        monitor.recordFrame(1000L)
        assertEquals(0.0, monitor.getMetrics().fps)
    }

    // ── Latency ──────────────────────────────────────────────────────────────

    @Test
    fun averageLatencyIsCorrect() {
        val monitor = PerformanceMonitor()
        monitor.recordLatency(10.0)
        monitor.recordLatency(20.0)
        monitor.recordLatency(30.0)
        assertApproxDouble(20.0, monitor.getMetrics().averageLatencyMs, epsilon = 0.001)
    }

    @Test
    fun p95LatencyCalculation() {
        val monitor = PerformanceMonitor(windowSize = 100)
        // 95 values at 10 ms, 5 values at 100 ms → p95 index = (100 * 0.95).toInt() = 95
        // sorted: [10, 10, ..., 10 (95 times), 100, 100, 100, 100, 100]
        // index 95 → 100
        for (i in 0 until 95) monitor.recordLatency(10.0)
        for (i in 0 until 5) monitor.recordLatency(100.0)
        val p95 = monitor.getMetrics().p95LatencyMs
        assertApproxDouble(100.0, p95, epsilon = 0.001)
    }

    // ── Ring buffer overflow ──────────────────────────────────────────────────

    @Test
    fun ringBufferOverflowStillYieldsCorrectFps() {
        val windowSize = 10
        val monitor = PerformanceMonitor(windowSize = windowSize)
        // Record 30 frames at 33 ms intervals; window holds only last 10
        for (i in 0 until 30) {
            monitor.recordFrame(i * 33L)
        }
        // After overflow, FPS should still be ~30 fps based on last 10 timestamps
        val metrics = monitor.getMetrics()
        assertApproxDouble(30.3, metrics.fps, epsilon = 1.0)
        assertEquals(30L, metrics.frameCount)
    }

    @Test
    fun latencyRingBufferOverflowKeepsLastWindowValues() {
        val windowSize = 5
        val monitor = PerformanceMonitor(windowSize = windowSize)
        // Record 10 values; last 5 are all 50.0
        for (i in 0 until 5) monitor.recordLatency(10.0)
        for (i in 0 until 5) monitor.recordLatency(50.0)
        assertApproxDouble(50.0, monitor.getMetrics().averageLatencyMs, epsilon = 0.001)
    }

    // ── Reset ────────────────────────────────────────────────────────────────

    @Test
    fun resetClearsAllData() {
        val monitor = PerformanceMonitor()
        monitor.recordFrame(0L)
        monitor.recordFrame(33L)
        monitor.recordLatency(5.0)
        monitor.reset()

        val metrics = monitor.getMetrics()
        assertEquals(0.0, metrics.fps)
        assertEquals(0.0, metrics.averageLatencyMs)
        assertEquals(0.0, metrics.p95LatencyMs)
        assertEquals(0L, metrics.frameCount)
        assertEquals(0L, metrics.droppedFrames)
    }

    @Test
    fun afterResetNewRecordingsWorkCorrectly() {
        val monitor = PerformanceMonitor()
        monitor.recordFrame(0L)
        monitor.recordLatency(100.0)
        monitor.reset()
        monitor.recordLatency(5.0)
        assertApproxDouble(5.0, monitor.getMetrics().averageLatencyMs, epsilon = 0.001)
    }

    // ── Dropped frames ────────────────────────────────────────────────────────

    @Test
    fun duplicateTimestampCountsAsDroppedFrame() {
        val monitor = PerformanceMonitor()
        monitor.recordFrame(100L)
        monitor.recordFrame(100L) // same timestamp → dropped
        monitor.recordFrame(100L) // same timestamp → dropped
        val metrics = monitor.getMetrics()
        assertEquals(3L, metrics.frameCount)
        assertEquals(2L, metrics.droppedFrames)
    }

    @Test
    fun noDroppedFramesForUniqueTimestamps() {
        val monitor = PerformanceMonitor()
        monitor.recordFrame(100L)
        monitor.recordFrame(200L)
        monitor.recordFrame(300L)
        assertEquals(0L, monitor.getMetrics().droppedFrames)
    }

    // ── Flow extension ────────────────────────────────────────────────────────

    @Test
    fun withPerformanceMonitorRecordsAllFrames() = runTest {
        val monitor = PerformanceMonitor()
        val timestamps = listOf(0L, 33L, 66L, 99L)
        val flow = flowOf(*timestamps.toTypedArray())

        val collected = flow
            .withPerformanceMonitor(monitor) { it }
            .toList()

        assertEquals(timestamps, collected)
        assertEquals(4L, monitor.getMetrics().frameCount)
    }

    @Test
    fun withPerformanceMonitorDoesNotAlterEmittedValues() = runTest {
        data class Frame(val id: Int, val ts: Long)

        val monitor = PerformanceMonitor()
        val frames = listOf(Frame(1, 0L), Frame(2, 33L), Frame(3, 66L))
        val flow = flowOf(*frames.toTypedArray())

        val result = flow
            .withPerformanceMonitor(monitor) { it.ts }
            .toList()

        assertEquals(frames, result)
    }

    // ── Thread safety (sequential lock verification) ───────────────────────────

    @Test
    fun rapidRecordAndGetMetricsDoesNotThrow() {
        val monitor = PerformanceMonitor()
        repeat(500) { i ->
            monitor.recordFrame(i * 16L)
            monitor.recordLatency(i.toDouble())
            monitor.getMetrics()
        }
        val metrics = monitor.getMetrics()
        assertEquals(500L, metrics.frameCount)
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun assertApproxDouble(expected: Double, actual: Double, epsilon: Double) {
        assertTrue(
            abs(expected - actual) < epsilon,
            "Expected ~$expected but was $actual (epsilon=$epsilon)",
        )
    }
}
