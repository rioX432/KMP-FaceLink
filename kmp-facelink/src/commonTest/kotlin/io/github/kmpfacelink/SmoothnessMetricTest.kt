package io.github.kmpfacelink

import io.github.kmpfacelink.util.SmoothnessMetric
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertTrue

class SmoothnessMetricTest {

    // ── Degenerate cases ──

    @Test
    fun ssjIsZeroBeforeMinimumSamples() {
        val metric = SmoothnessMetric(windowSize = WINDOW)
        metric.record(1f)
        metric.record(2f)
        metric.record(3f)
        assertEquals(0f, metric.ssj(), "SSJ must be zero with fewer than 4 samples")
    }

    @Test
    fun constructorRejectsTooSmallWindow() {
        assertFails { SmoothnessMetric(windowSize = 3) }
    }

    // ── Polynomial signals → zero jerk ──

    @Test
    fun constantSignalHasZeroJerk() {
        val metric = SmoothnessMetric(windowSize = WINDOW)
        repeat(WINDOW) { metric.record(CONSTANT_SAMPLE) }
        assertEquals(0f, metric.ssj(), "constant signal should have zero SSJ")
    }

    @Test
    fun linearSignalHasZeroJerk() {
        // A linear ramp has zero 2nd, 3rd, ... derivatives.
        val metric = SmoothnessMetric(windowSize = WINDOW)
        for (i in 0 until WINDOW) metric.record(i.toFloat())
        assertEquals(0f, metric.ssj(), "linear signal should have zero SSJ")
    }

    @Test
    fun quadraticSignalHasZeroJerk() {
        // A quadratic (constant acceleration) also has zero jerk.
        val metric = SmoothnessMetric(windowSize = WINDOW)
        for (i in 0 until WINDOW) {
            val value = i.toFloat() * i.toFloat()
            metric.record(value)
        }
        assertEquals(0f, metric.ssj(), "quadratic signal should have zero SSJ")
    }

    // ── Non-smooth signals → positive jerk ──

    @Test
    fun stepSignalHasPositiveJerk() {
        // A step function has a discrete delta spike in the third derivative.
        val metric = SmoothnessMetric(windowSize = WINDOW)
        repeat(WINDOW / 2) { metric.record(0f) }
        repeat(WINDOW / 2) { metric.record(1f) }
        assertTrue(metric.ssj() > 0f, "step signal should have positive SSJ")
    }

    @Test
    fun noisierSignalHasLargerJerkThanSmoothOne() {
        val smooth = SmoothnessMetric(windowSize = WINDOW)
        val jittery = SmoothnessMetric(windowSize = WINDOW)

        // Smooth: slow sinusoid (approximately zero jerk over a short window).
        for (i in 0 until WINDOW) {
            val t = i.toFloat() / WINDOW
            smooth.record(SMOOTH_AMPLITUDE * kotlin.math.sin(t * kotlin.math.PI.toFloat()))
        }

        // Jittery: alternating ±1 signal (maximum high-frequency jerk).
        for (i in 0 until WINDOW) {
            jittery.record(if (i % 2 == 0) 1f else -1f)
        }

        assertTrue(
            jittery.ssj() > smooth.ssj(),
            "alternating signal SSJ (${jittery.ssj()}) should exceed smooth sinusoid SSJ (${smooth.ssj()})",
        )
    }

    // ── Reset ──

    @Test
    fun resetClearsAccumulatedSamples() {
        val metric = SmoothnessMetric(windowSize = WINDOW)
        repeat(WINDOW) { metric.record(it.toFloat()) }

        metric.reset()

        assertEquals(0f, metric.ssj(), "SSJ should be zero after reset")
        // After reset we should once again need the full minimum sample count.
        metric.record(1f)
        metric.record(2f)
        metric.record(3f)
        assertEquals(0f, metric.ssj(), "SSJ should stay zero until minimum samples are re-recorded")
    }

    // ── Mean squared jerk ──

    @Test
    fun meanSquaredJerkIsZeroForZeroSsj() {
        val metric = SmoothnessMetric(windowSize = WINDOW)
        repeat(WINDOW) { metric.record(CONSTANT_SAMPLE) }
        assertEquals(0f, metric.meanSquaredJerk())
    }

    companion object {
        private const val WINDOW = 16
        private const val CONSTANT_SAMPLE = 0.5f
        private const val SMOOTH_AMPLITUDE = 0.1f
    }
}
