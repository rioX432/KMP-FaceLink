package io.github.kmpfacelink

import io.github.kmpfacelink.model.BlendShape
import io.github.kmpfacelink.util.ExponentialMovingAverage
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertTrue

class ExponentialMovingAverageTest {

    private fun assertApprox(expected: Float, actual: Float, epsilon: Float = 0.001f) {
        assertTrue(abs(expected - actual) < epsilon, "Expected $expected but was $actual")
    }

    @Test
    fun firstCallReturnsInputAsIs() {
        val ema = ExponentialMovingAverage(alpha = 0.5f)
        val input = mapOf(BlendShape.JAW_OPEN to 0.8f)
        val result = ema.smooth(input)
        assertApprox(0.8f, result.getValue(BlendShape.JAW_OPEN))
    }

    @Test
    fun secondCallAppliesSmoothing() {
        val ema = ExponentialMovingAverage(alpha = 0.5f)
        val first = mapOf(BlendShape.JAW_OPEN to 1.0f)
        ema.smooth(first)

        val second = mapOf(BlendShape.JAW_OPEN to 0.0f)
        val result = ema.smooth(second)

        // smoothed = 0.5 * 0.0 + 0.5 * 1.0 = 0.5
        assertApprox(0.5f, result.getValue(BlendShape.JAW_OPEN))
    }

    @Test
    fun highAlphaMeansLessSmoothing() {
        val ema = ExponentialMovingAverage(alpha = 0.9f)
        val first = mapOf(BlendShape.JAW_OPEN to 1.0f)
        ema.smooth(first)

        val second = mapOf(BlendShape.JAW_OPEN to 0.0f)
        val result = ema.smooth(second)

        // smoothed = 0.9 * 0.0 + 0.1 * 1.0 = 0.1
        assertApprox(0.1f, result.getValue(BlendShape.JAW_OPEN))
    }

    @Test
    fun lowAlphaMeansMoreSmoothing() {
        val ema = ExponentialMovingAverage(alpha = 0.1f)
        val first = mapOf(BlendShape.JAW_OPEN to 1.0f)
        ema.smooth(first)

        val second = mapOf(BlendShape.JAW_OPEN to 0.0f)
        val result = ema.smooth(second)

        // smoothed = 0.1 * 0.0 + 0.9 * 1.0 = 0.9
        assertApprox(0.9f, result.getValue(BlendShape.JAW_OPEN))
    }

    @Test
    fun resetClearsPreviousValues() {
        val ema = ExponentialMovingAverage(alpha = 0.5f)
        ema.smooth(mapOf(BlendShape.JAW_OPEN to 1.0f))
        ema.reset()

        val result = ema.smooth(mapOf(BlendShape.JAW_OPEN to 0.3f))
        assertApprox(0.3f, result.getValue(BlendShape.JAW_OPEN))
    }
}
