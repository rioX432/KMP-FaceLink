package io.github.kmpfacelink

import io.github.kmpfacelink.model.BlendShape
import io.github.kmpfacelink.model.BlendShapeData
import io.github.kmpfacelink.model.HeadTransform
import io.github.kmpfacelink.util.BlendShapeInterpolator
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class BlendShapeInterpolatorTest {

    private fun blendShapes(value: Float): BlendShapeData =
        BlendShape.entries.associateWith { value }

    private fun transform(pitch: Float = 0f, yaw: Float = 0f): HeadTransform =
        HeadTransform(pitch = pitch, yaw = yaw)

    @Test
    fun noDataReturnsNull() {
        val interpolator = BlendShapeInterpolator()
        assertNull(interpolator.interpolateBlendShapes(100L))
        assertNull(interpolator.interpolateTransform(100L))
        assertFalse(interpolator.hasData)
    }

    @Test
    fun singleFrameReturnsLastKnown() {
        val interpolator = BlendShapeInterpolator()
        interpolator.onFrameProcessed(blendShapes(0.5f), transform(pitch = 10f), 100L)

        assertTrue(interpolator.hasData)
        val result = interpolator.interpolateBlendShapes(150L)
        assertNotNull(result)
        // With only one frame, should return a copy of the current data
        for (shape in BlendShape.entries) {
            assertEquals(0.5f, result[shape]!!, 0.001f)
        }
    }

    @Test
    fun twoFramesExtrapolatesForward() {
        val interpolator = BlendShapeInterpolator()
        // Frame 1: all 0.3
        interpolator.onFrameProcessed(blendShapes(0.3f), transform(pitch = 0f), 0L)
        // Frame 2: all 0.5 (delta = +0.2 over 100ms)
        interpolator.onFrameProcessed(blendShapes(0.5f), transform(pitch = 10f), 100L)

        // Interpolate at t=150ms (50ms after last frame, t=0.5)
        val result = interpolator.interpolateBlendShapes(150L)
        assertNotNull(result)
        // Expected: 0.5 + (0.5 - 0.3) * 0.5 = 0.5 + 0.1 = 0.6
        for (shape in BlendShape.entries) {
            assertEquals(0.6f, result[shape]!!, 0.001f)
        }
    }

    @Test
    fun extrapolationClampsToValidRange() {
        val interpolator = BlendShapeInterpolator()
        // Moving from 0.8 to 0.95
        interpolator.onFrameProcessed(blendShapes(0.8f), transform(), 0L)
        interpolator.onFrameProcessed(blendShapes(0.95f), transform(), 100L)

        // At t=200ms (t=1.0), extrapolation: 0.95 + 0.15 * 1.0 = 1.1 -> clamped to 1.0
        val result = interpolator.interpolateBlendShapes(200L)
        assertNotNull(result)
        for (shape in BlendShape.entries) {
            assertTrue(result[shape]!! <= 1.0f, "Value should be clamped to 1.0")
        }
    }

    @Test
    fun extrapolationFactorCapped() {
        val interpolator = BlendShapeInterpolator()
        interpolator.onFrameProcessed(blendShapes(0.4f), transform(), 0L)
        interpolator.onFrameProcessed(blendShapes(0.5f), transform(), 100L)

        // Very far in future (t=3.0) — should be capped at MAX_EXTRAPOLATION_FACTOR (1.5)
        val result = interpolator.interpolateBlendShapes(400L)
        assertNotNull(result)
        // Expected: 0.5 + 0.1 * 1.5 = 0.65
        for (shape in BlendShape.entries) {
            assertEquals(0.65f, result[shape]!!, 0.001f)
        }
    }

    @Test
    fun transformInterpolation() {
        val interpolator = BlendShapeInterpolator()
        interpolator.onFrameProcessed(blendShapes(0.5f), transform(pitch = 0f, yaw = 0f), 0L)
        interpolator.onFrameProcessed(blendShapes(0.5f), transform(pitch = 10f, yaw = 20f), 100L)

        val result = interpolator.interpolateTransform(150L)
        assertNotNull(result)
        // t = 0.5: pitch = 10 + (10-0)*0.5 = 15, yaw = 20 + (20-0)*0.5 = 30
        assertTrue(abs(result.pitch - 15f) < 0.01f, "Pitch should be ~15, was ${result.pitch}")
        assertTrue(abs(result.yaw - 30f) < 0.01f, "Yaw should be ~30, was ${result.yaw}")
    }

    @Test
    fun resetClearsAllState() {
        val interpolator = BlendShapeInterpolator()
        interpolator.onFrameProcessed(blendShapes(0.5f), transform(), 100L)
        assertTrue(interpolator.hasData)

        interpolator.reset()
        assertFalse(interpolator.hasData)
        assertNull(interpolator.interpolateBlendShapes(200L))
    }

    @Test
    fun zeroFrameDeltaReturnsCurrent() {
        val interpolator = BlendShapeInterpolator()
        interpolator.onFrameProcessed(blendShapes(0.3f), transform(pitch = 5f), 100L)
        interpolator.onFrameProcessed(blendShapes(0.5f), transform(pitch = 10f), 100L) // same timestamp

        val result = interpolator.interpolateBlendShapes(150L)
        assertNotNull(result)
        // Zero delta returns current values unchanged
        for (shape in BlendShape.entries) {
            assertEquals(0.5f, result[shape]!!, 0.001f)
        }
    }
}
