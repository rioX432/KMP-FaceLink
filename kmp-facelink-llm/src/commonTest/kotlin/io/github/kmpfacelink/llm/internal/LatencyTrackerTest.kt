package io.github.kmpfacelink.llm.internal

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LatencyTrackerTest {

    @Test
    fun emptyTrackerDoesNotExceedTimeout() {
        val tracker = LatencyTracker()
        assertFalse(tracker.exceedsTimeout(5000L))
    }

    @Test
    fun averageLatencyOfSingleSample() {
        val tracker = LatencyTracker()
        tracker.record(3000L)
        assertEquals(3000L, tracker.averageLatencyMs)
    }

    @Test
    fun averageLatencyOfMultipleSamples() {
        val tracker = LatencyTracker()
        tracker.record(2000L)
        tracker.record(4000L)
        assertEquals(3000L, tracker.averageLatencyMs)
    }

    @Test
    fun exceedsTimeoutWhenAverageIsHigh() {
        val tracker = LatencyTracker()
        tracker.record(6000L)
        tracker.record(7000L)
        assertTrue(tracker.exceedsTimeout(5000L))
    }

    @Test
    fun doesNotExceedTimeoutWhenAverageIsLow() {
        val tracker = LatencyTracker()
        tracker.record(2000L)
        tracker.record(3000L)
        assertFalse(tracker.exceedsTimeout(5000L))
    }

    @Test
    fun slidingWindowEvictsOldSamples() {
        val tracker = LatencyTracker(windowSize = 3)
        tracker.record(10000L)
        tracker.record(1000L)
        tracker.record(1000L)
        tracker.record(1000L) // evicts the 10000 sample

        assertEquals(1000L, tracker.averageLatencyMs)
        assertEquals(3, tracker.sampleCount)
    }

    @Test
    fun resetClearsSamples() {
        val tracker = LatencyTracker()
        tracker.record(5000L)
        tracker.reset()

        assertEquals(0, tracker.sampleCount)
        assertEquals(0L, tracker.averageLatencyMs)
    }

    @Test
    fun emptyTrackerAverageIsZero() {
        val tracker = LatencyTracker()
        assertEquals(0L, tracker.averageLatencyMs)
    }
}
