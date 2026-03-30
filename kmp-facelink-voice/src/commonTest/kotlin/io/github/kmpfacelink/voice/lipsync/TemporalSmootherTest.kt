package io.github.kmpfacelink.voice.lipsync

import io.github.kmpfacelink.model.BlendShape
import io.github.kmpfacelink.voice.lipsync.internal.TemporalSmoother
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private const val FLOAT_TOLERANCE = 0.01f

class TemporalSmootherTest {

    @Test
    fun noSmoothingPassesThroughDirectly() {
        val smoother = TemporalSmoother(smoothing = 0f)
        val target = mapOf(BlendShape.JAW_OPEN to 0.8f)
        val result = smoother.smooth(target)

        assertEquals(0.8f, result[BlendShape.JAW_OPEN]!!, FLOAT_TOLERANCE)
    }

    @Test
    fun maxSmoothingApproachesSlowly() {
        val smoother = TemporalSmoother(smoothing = 0.9f)
        val target = mapOf(BlendShape.JAW_OPEN to 1.0f)
        val result = smoother.smooth(target)

        // With 0.9 smoothing, alpha=0.1, so first frame: 0 + 0.1 * 1.0 = 0.1
        assertEquals(0.1f, result[BlendShape.JAW_OPEN]!!, FLOAT_TOLERANCE)
    }

    @Test
    fun consecutiveFramesConverge() {
        val smoother = TemporalSmoother(smoothing = 0.5f)
        val target = mapOf(BlendShape.JAW_OPEN to 1.0f)

        var lastValue = 0f
        repeat(10) {
            val result = smoother.smooth(target)
            val value = result[BlendShape.JAW_OPEN] ?: 0f
            assertTrue(value > lastValue, "Values should increase toward target")
            lastValue = value
        }
        assertTrue(lastValue > 0.9f, "Should converge close to target after 10 frames")
    }

    @Test
    fun decayToZeroRemovesKeys() {
        val smoother = TemporalSmoother(smoothing = 0f)

        // First frame with value
        smoother.smooth(mapOf(BlendShape.JAW_OPEN to 0.8f))

        // Next frame with zero target — should decay
        val result = smoother.smooth(emptyMap())
        assertTrue(
            result.isEmpty() || (result[BlendShape.JAW_OPEN] ?: 0f) < 0.01f,
            "Should remove or zero out blend shapes that are no longer targeted",
        )
    }

    @Test
    fun resetClearsPreviousState() {
        val smoother = TemporalSmoother(smoothing = 0.9f)
        smoother.smooth(mapOf(BlendShape.JAW_OPEN to 1.0f))
        smoother.reset()

        // After reset, should behave as if first frame
        val result = smoother.smooth(mapOf(BlendShape.JAW_OPEN to 1.0f))
        assertEquals(0.1f, result[BlendShape.JAW_OPEN]!!, FLOAT_TOLERANCE)
    }
}
