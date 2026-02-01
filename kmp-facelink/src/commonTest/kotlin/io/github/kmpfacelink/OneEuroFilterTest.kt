package io.github.kmpfacelink

import io.github.kmpfacelink.model.BlendShape
import io.github.kmpfacelink.util.OneEuroFilter
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertTrue

class OneEuroFilterTest {

    private fun assertApprox(expected: Float, actual: Float, epsilon: Float = 0.05f) {
        assertTrue(abs(expected - actual) < epsilon, "Expected ~$expected but was $actual")
    }

    @Test
    fun firstCallReturnsInputAsIs() {
        val filter = OneEuroFilter()
        val input = mapOf(BlendShape.JAW_OPEN to 0.6f)
        val result = filter.smooth(input, 0L)
        assertApprox(0.6f, result[BlendShape.JAW_OPEN]!!, epsilon = 0.001f)
    }

    @Test
    fun stableSignalConverges() {
        val filter = OneEuroFilter(minCutoff = 1.0f, beta = 0.0f)
        val value = 0.5f
        var result = mapOf(BlendShape.JAW_OPEN to value)

        // Feed constant signal for many frames at ~30fps
        for (i in 0..60) {
            result = filter.smooth(mapOf(BlendShape.JAW_OPEN to value), i * 33L)
        }
        // Should converge to the constant value
        assertApprox(value, result[BlendShape.JAW_OPEN]!!, epsilon = 0.01f)
    }

    @Test
    fun higherBetaReducesLag() {
        // Low beta = more smoothing = more lag
        val lowBeta = OneEuroFilter(minCutoff = 1.0f, beta = 0.0f)
        // High beta = less smoothing during fast movement
        val highBeta = OneEuroFilter(minCutoff = 1.0f, beta = 1.0f)

        // Initial frame
        val initial = mapOf(BlendShape.JAW_OPEN to 0.0f)
        lowBeta.smooth(initial, 0L)
        highBeta.smooth(initial, 0L)

        // Sudden jump
        val jump = mapOf(BlendShape.JAW_OPEN to 1.0f)
        val resultLow = lowBeta.smooth(jump, 33L)
        val resultHigh = highBeta.smooth(jump, 33L)

        // High beta should track the jump faster (closer to 1.0)
        val lowVal = resultLow[BlendShape.JAW_OPEN]!!
        val highVal = resultHigh[BlendShape.JAW_OPEN]!!
        assertTrue(
            highVal > lowVal,
            "High beta ($highVal) should be closer to 1.0 than low beta ($lowVal)",
        )
    }

    @Test
    fun resetClearsState() {
        val filter = OneEuroFilter()

        // Feed some data
        filter.smooth(mapOf(BlendShape.JAW_OPEN to 0.8f), 0L)
        filter.smooth(mapOf(BlendShape.JAW_OPEN to 0.2f), 33L)

        // Reset
        filter.reset()

        // After reset, first call should return input as-is
        val result = filter.smooth(mapOf(BlendShape.JAW_OPEN to 0.5f), 100L)
        assertApprox(0.5f, result[BlendShape.JAW_OPEN]!!, epsilon = 0.001f)
    }

    @Test
    fun sameTimestampPassesThrough() {
        val filter = OneEuroFilter()

        // First frame
        filter.smooth(mapOf(BlendShape.JAW_OPEN to 0.3f), 100L)

        // Same timestamp — should pass through as-is (avoids division by zero)
        val result = filter.smooth(mapOf(BlendShape.JAW_OPEN to 0.9f), 100L)
        assertApprox(0.9f, result[BlendShape.JAW_OPEN]!!, epsilon = 0.001f)
    }

    @Test
    fun multipleBlendShapesSmoothedIndependently() {
        val filter = OneEuroFilter(minCutoff = 1.0f, beta = 0.0f)

        val frame0 = mapOf(
            BlendShape.JAW_OPEN to 0.0f,
            BlendShape.EYE_BLINK_LEFT to 1.0f,
        )
        filter.smooth(frame0, 0L)

        val frame1 = mapOf(
            BlendShape.JAW_OPEN to 1.0f,
            BlendShape.EYE_BLINK_LEFT to 0.0f,
        )
        val result = filter.smooth(frame1, 33L)

        // Both should be smoothed but in opposite directions
        val jaw = result[BlendShape.JAW_OPEN]!!
        val eye = result[BlendShape.EYE_BLINK_LEFT]!!
        assertTrue(jaw < 1.0f, "JAW_OPEN should be smoothed below 1.0: $jaw")
        assertTrue(eye > 0.0f, "EYE_BLINK_LEFT should be smoothed above 0.0: $eye")
    }
}
