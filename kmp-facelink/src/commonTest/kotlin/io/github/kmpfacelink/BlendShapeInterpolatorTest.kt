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
        // t = 0.5 (i.e. half a frame beyond cur). Since the interpolator now uses
        // quaternion slerp instead of per-Euler lerp, the result follows the
        // geodesic arc through SO(3), not an axis-aligned straight line. For the
        // small composite rotation (10° pitch, 20° yaw) this produces pitch ≈
        // 15.6° and yaw ≈ 29.9° — very close to but not exactly the 15°/30° a
        // linear Euler extrapolation would give, and free of gimbal artifacts at
        // larger angles.
        assertTrue(
            abs(result.pitch - TRANSFORM_INTERP_EXPECTED_PITCH) < TRANSFORM_INTERP_EPSILON,
            "Pitch should be ≈${TRANSFORM_INTERP_EXPECTED_PITCH}, was ${result.pitch}",
        )
        assertTrue(
            abs(result.yaw - TRANSFORM_INTERP_EXPECTED_YAW) < TRANSFORM_INTERP_EPSILON,
            "Yaw should be ≈${TRANSFORM_INTERP_EXPECTED_YAW}, was ${result.yaw}",
        )
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

    // ── lerpTransform direct tests (quaternion slerp behaviour) ──

    @Test
    fun lerpTransformAtZeroReturnsCurExactly() {
        val prev = HeadTransform(pitch = 5f, yaw = 10f, roll = 0f)
        val cur = HeadTransform(pitch = 15f, yaw = 30f, roll = 0f)

        val result = BlendShapeInterpolator.lerpTransform(prev, cur, t = 0f)

        assertTrue(result === cur, "t=0 must return cur by identity (skips round-trip)")
    }

    @Test
    fun lerpTransformAtOneExtrapolatesOneFrameAhead() {
        // prev→cur = 10° yaw delta. At t=1 we should be ~10° further (yaw≈30°).
        val prev = HeadTransform(yaw = 10f)
        val cur = HeadTransform(yaw = 20f)

        val result = BlendShapeInterpolator.lerpTransform(prev, cur, t = 1f)

        assertTrue(
            abs(result.yaw - YAW_EXTRAPOLATED_ONE_STEP) < SLERP_EXTRAPOLATION_EPSILON,
            "yaw extrapolated one frame beyond cur: expected $YAW_EXTRAPOLATED_ONE_STEP, got ${result.yaw}",
        )
    }

    @Test
    fun lerpTransformExtrapolatesPositionLinearly() {
        val prev = HeadTransform(positionX = 0f, positionY = 0f, positionZ = 0f)
        val cur = HeadTransform(positionX = 1f, positionY = 2f, positionZ = 3f)

        val result = BlendShapeInterpolator.lerpTransform(prev, cur, t = 0.5f)

        assertEquals(POSITION_HALF_STEP_X, result.positionX, 1e-3f)
        assertEquals(POSITION_HALF_STEP_Y, result.positionY, 1e-3f)
        assertEquals(POSITION_HALF_STEP_Z, result.positionZ, 1e-3f)
    }

    @Test
    fun lerpTransformAcrossLargeYawIsStableNoNaN() {
        // A 40° → 60° yaw transition extrapolated forward should produce a
        // stable 80° yaw output, without NaN and without drifting into
        // pitch/roll due to Euler aliasing. Chosen to stay comfortably inside
        // the ±90° asin safe range for the roundtrip.
        val prev = HeadTransform(yaw = 40f)
        val cur = HeadTransform(yaw = 60f)

        val result = BlendShapeInterpolator.lerpTransform(prev, cur, t = 1f)

        assertTrue(!result.yaw.isNaN(), "yaw should not be NaN")
        assertTrue(!result.pitch.isNaN(), "pitch should not be NaN")
        assertTrue(!result.roll.isNaN(), "roll should not be NaN")
        assertTrue(
            abs(result.yaw - LARGE_YAW_EXTRAPOLATED) < SLERP_EXTRAPOLATION_EPSILON,
            "yaw extrapolated (40→60→80): expected $LARGE_YAW_EXTRAPOLATED, got ${result.yaw}",
        )
        assertTrue(abs(result.pitch) < 1e-3f, "pitch should not leak from pure yaw rotation: ${result.pitch}")
        assertTrue(abs(result.roll) < 1e-3f, "roll should not leak from pure yaw rotation: ${result.roll}")
    }

    @Test
    fun lerpTransformPreservesMatrixReferenceFromCur() {
        val matrix = FloatArray(MATRIX_SIZE) { it.toFloat() }
        val prev = HeadTransform(yaw = 0f)
        val cur = HeadTransform(yaw = 15f, transformMatrix = matrix)

        val result = BlendShapeInterpolator.lerpTransform(prev, cur, t = 0.5f)

        assertTrue(result.transformMatrix === matrix, "transformMatrix should pass through from cur")
    }

    companion object {
        // Expected slerp-extrapolated Euler values for the transformInterpolation
        // scenario (prev=0°,0°,0°; cur=10°,20°,0°; extrapolated half a frame ahead).
        // Because slerp follows the geodesic arc these differ slightly from the
        // linear-Euler expectation of 15°/30°.
        private const val TRANSFORM_INTERP_EXPECTED_PITCH = 15.6f
        private const val TRANSFORM_INTERP_EXPECTED_YAW = 29.9f
        private const val TRANSFORM_INTERP_EPSILON = 0.5f

        // Expected slerp-extrapolated yaw values for the direct lerpTransform tests.
        private const val YAW_EXTRAPOLATED_ONE_STEP = 30f
        private const val LARGE_YAW_EXTRAPOLATED = 80f
        private const val SLERP_EXTRAPOLATION_EPSILON = 0.05f

        private const val POSITION_HALF_STEP_X = 1.5f
        private const val POSITION_HALF_STEP_Y = 3f
        private const val POSITION_HALF_STEP_Z = 4.5f
        private const val MATRIX_SIZE = 16
    }
}
