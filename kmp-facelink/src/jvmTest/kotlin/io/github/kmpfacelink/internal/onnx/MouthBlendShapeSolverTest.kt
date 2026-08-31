package io.github.kmpfacelink.internal.onnx

import io.github.kmpfacelink.internal.onnx.OpenSeeFaceLandmarkIndices as LM
import io.github.kmpfacelink.model.BlendShape
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Every shape this solver emits is checked twice: once against the deformation it is meant to
 * measure, and once against a control deformation it must ignore. A shape that responds to its own
 * stimulus but also to the control is not measuring what its name says, so the control is what
 * makes the positive case mean anything.
 *
 * The fixture is a synthetic neutral built so that each measurement sits exactly on the resting
 * constant in [MouthBlendShapeSolver]. It fixes the mechanics only: it says nothing about whether
 * these values track a real face, which is a separate measurement.
 */
class MouthBlendShapeSolverTest {

    private fun solve(landmarks: FloatArray): Map<BlendShape, Float> {
        val output = HashMap<BlendShape, Float>()
        MouthBlendShapeSolver.solve(landmarks, output)
        return output
    }

    private fun Map<BlendShape, Float>.at(shape: BlendShape): Float =
        this[shape] ?: error("$shape was not emitted")

    // region shapes the solver must not emit

    @Test
    fun jawForwardIsNotEmitted() {
        assertFalse(
            BlendShape.JAW_FORWARD in solve(neutral()),
            "jawForward is not observable in the 2D contour and must be absent, not zero",
        )
    }

    @Test
    fun emittedShapesAreExactlyTheMeasuredSet() {
        assertEquals(EXPECTED_SHAPES, solve(neutral()).keys)
    }

    @Test
    fun allEmittedValuesAreInRange() {
        for ((shape, value) in solve(neutral())) {
            assertTrue(value in 0f..1f, "$shape produced $value, outside [0,1]")
        }
    }

    // endregion

    // region mouthClose: jaw drops while the lips stay together

    @Test
    fun mouthCloseRespondsToJawDropWithLipsTogether() {
        val face = neutral().apply { moveY(LM.JAW_CENTER, +0.06f) }
        assertTrue(solve(face).at(BlendShape.MOUTH_CLOSE) > 0.5f)
    }

    @Test
    fun mouthCloseIgnoresJawDropWithLipsApart() {
        val face = neutral().apply {
            moveY(LM.JAW_CENTER, +0.06f)
            moveY(LM.INNER_MOUTH_LOWER, +0.03f)
        }
        assertTrue(
            solve(face).at(BlendShape.MOUTH_CLOSE) < 0.1f,
            "an open mouth must not read as lips held closed",
        )
    }

    // endregion

    // region stretch and dimple: corner travel away from and toward the midline

    @Test
    fun mouthStretchRespondsToCornerWidening() {
        val face = neutral().apply { moveX(LM.MOUTH_RIGHT, -0.02f) }
        val result = solve(face)
        assertTrue(result.at(BlendShape.MOUTH_STRETCH_RIGHT) > 0.5f)
        assertTrue(
            result.at(BlendShape.MOUTH_STRETCH_LEFT) < 0.1f,
            "widening one corner must not move the other side",
        )
    }

    @Test
    fun mouthStretchIgnoresJawOpening() {
        val face = neutral().apply { moveY(LM.INNER_MOUTH_LOWER, +0.04f) }
        assertTrue(solve(face).at(BlendShape.MOUTH_STRETCH_RIGHT) < 0.1f)
    }

    @Test
    fun mouthDimpleRespondsToCornerNarrowingWhileClosed() {
        val face = neutral().apply { moveX(LM.MOUTH_RIGHT, +0.015f) }
        val result = solve(face)
        assertTrue(result.at(BlendShape.MOUTH_DIMPLE_RIGHT) > 0.5f)
        assertTrue(result.at(BlendShape.MOUTH_DIMPLE_LEFT) < 0.1f)
    }

    @Test
    fun mouthDimpleIgnoresTheSameNarrowingWhenLipsPart() {
        val face = neutral().apply {
            moveX(LM.MOUTH_RIGHT, +0.015f)
            moveY(LM.INNER_MOUTH_LOWER, +0.03f)
        }
        assertTrue(
            solve(face).at(BlendShape.MOUTH_DIMPLE_RIGHT) < 0.1f,
            "narrowing with parted lips is a pucker or funnel, not a dimple",
        )
    }

    // endregion

    // region press: corner vermilion thins while the lips are closed

    @Test
    fun mouthPressRespondsToCornerThinningWhileClosed() {
        val face = neutral().apply { moveY(LM.MOUTH_LOWER_RIGHT, -0.012f) }
        val result = solve(face)
        assertTrue(result.at(BlendShape.MOUTH_PRESS_RIGHT) > 0.5f)
        assertTrue(result.at(BlendShape.MOUTH_PRESS_LEFT) < 0.1f)
    }

    @Test
    fun mouthPressIgnoresTheSameThinningWhenLipsPart() {
        val face = neutral().apply {
            moveY(LM.MOUTH_LOWER_RIGHT, -0.012f)
            moveY(LM.INNER_MOUTH_LOWER, +0.03f)
        }
        assertTrue(solve(face).at(BlendShape.MOUTH_PRESS_RIGHT) < 0.1f)
    }

    // endregion

    // region roll and shrug: one vermilion thickness measurement per lip, opposite signs

    @Test
    fun mouthRollUpperRespondsToUpperLipThinning() {
        val face = neutral().apply { moveY(LM.MOUTH_UPPER_CENTER, +0.008f) }
        val result = solve(face)
        assertTrue(result.at(BlendShape.MOUTH_ROLL_UPPER) > 0.5f)
        assertTrue(
            result.at(BlendShape.MOUTH_ROLL_LOWER) < 0.1f,
            "thinning the upper lip must not read as the lower lip rolling",
        )
    }

    @Test
    fun mouthShrugUpperRespondsToUpperLipThickening() {
        val face = neutral().apply { moveY(LM.MOUTH_UPPER_CENTER, -0.008f) }
        val result = solve(face)
        assertTrue(result.at(BlendShape.MOUTH_SHRUG_UPPER) > 0.5f)
        assertTrue(result.at(BlendShape.MOUTH_SHRUG_LOWER) < 0.1f)
    }

    @Test
    fun mouthRollLowerRespondsToLowerLipThinning() {
        val face = neutral().apply { moveY(LM.MOUTH_LOWER_CENTER, -0.008f) }
        val result = solve(face)
        assertTrue(result.at(BlendShape.MOUTH_ROLL_LOWER) > 0.5f)
        assertTrue(result.at(BlendShape.MOUTH_ROLL_UPPER) < 0.1f)
    }

    @Test
    fun rollAndShrugOnTheSameLipAreMutuallyExclusive() {
        for (offset in listOf(-0.008f, 0f, +0.008f)) {
            val result = solve(neutral().apply { moveY(LM.MOUTH_UPPER_CENTER, offset) })
            val product = result.at(BlendShape.MOUTH_ROLL_UPPER) * result.at(BlendShape.MOUTH_SHRUG_UPPER)
            assertTrue(product == 0f, "roll and shrug both fired at offset $offset")
        }
    }

    // endregion

    // region per-side independence, the defect this solver previously had

    @Test
    fun mouthUpperUpIsMeasuredPerSide() {
        val face = neutral().apply { moveY(LM.MOUTH_UPPER_CENTER_RIGHT, -0.02f) }
        val result = solve(face)
        assertTrue(result.at(BlendShape.MOUTH_UPPER_UP_RIGHT) > 0.5f)
        assertTrue(
            result.at(BlendShape.MOUTH_UPPER_UP_LEFT) < 0.1f,
            "the left value must not be a copy of the right",
        )
    }

    @Test
    fun mouthLowerDownIsMeasuredPerSide() {
        val face = neutral().apply { moveY(LM.MOUTH_LOWER_CENTER_LEFT, +0.025f) }
        val result = solve(face)
        assertTrue(result.at(BlendShape.MOUTH_LOWER_DOWN_LEFT) > 0.5f)
        assertTrue(result.at(BlendShape.MOUTH_LOWER_DOWN_RIGHT) < 0.1f)
    }

    @Test
    fun raisingTheUpperLipIncreasesMouthUpperUp() {
        val neutralValue = solve(neutral()).at(BlendShape.MOUTH_UPPER_UP_RIGHT)
        val raised = solve(neutral().apply { moveY(LM.MOUTH_UPPER_CENTER_RIGHT, -0.02f) })
            .at(BlendShape.MOUTH_UPPER_UP_RIGHT)
        val lowered = solve(neutral().apply { moveY(LM.MOUTH_UPPER_CENTER_RIGHT, +0.02f) })
            .at(BlendShape.MOUTH_UPPER_UP_RIGHT)
        assertTrue(
            raised > neutralValue && lowered <= neutralValue,
            "moving the upper lip toward the nose must raise the value, not lower it",
        )
    }

    // endregion

    @Test
    fun neutralFaceProducesLowValuesForEveryDeviationShape() {
        val result = solve(neutral())
        for (shape in DEVIATION_SHAPES) {
            assertTrue(result.at(shape) < 0.1f, "$shape read ${result.at(shape)} on a neutral face")
        }
    }

    // region fixture

    private fun FloatArray.moveX(index: Int, delta: Float) {
        this[index * 2] += delta
    }

    private fun FloatArray.moveY(index: Int, delta: Float) {
        this[index * 2 + 1] += delta
    }

    /**
     * A synthetic resting face whose inter-ocular distance is 0.30, built so that every
     * measurement the solver takes lands on its resting constant.
     */
    private fun neutral(): FloatArray {
        val points = FloatArray(NUM_LANDMARKS * 2)
        fun put(index: Int, px: Float, py: Float) {
            points[index * 2] = px
            points[index * 2 + 1] = py
        }

        // Only the points this solver reads need to be placed; the rest stay at the origin and are
        // never touched, which also proves the solver does not depend on them.
        put(LM.JAW_CENTER, 0.500f, 0.809f)
        put(LM.NOSE_TIP, 0.500f, 0.550f)
        put(LM.NOSE_BOTTOM, 0.500f, 0.590f)
        put(LM.RIGHT_EYE_OUTER, 0.350f, 0.400f)
        put(LM.LEFT_EYE_OUTER, 0.650f, 0.400f)

        put(LM.MOUTH_RIGHT, 0.419f, 0.670f)
        put(LM.MOUTH_LEFT, 0.581f, 0.670f)
        put(LM.MOUTH_UPPER_RIGHT, 0.440f, 0.6700f)
        put(LM.MOUTH_LOWER_RIGHT, 0.440f, 0.6850f)
        put(LM.MOUTH_UPPER_LEFT, 0.560f, 0.6700f)
        put(LM.MOUTH_LOWER_LEFT, 0.560f, 0.6850f)
        put(LM.MOUTH_UPPER_CENTER_RIGHT, 0.470f, 0.6500f)
        put(LM.MOUTH_UPPER_CENTER_LEFT, 0.530f, 0.6500f)
        put(LM.MOUTH_LOWER_CENTER_RIGHT, 0.470f, 0.6860f)
        put(LM.MOUTH_LOWER_CENTER_LEFT, 0.530f, 0.6860f)
        put(LM.MOUTH_UPPER_CENTER, 0.500f, 0.6600f)
        put(LM.MOUTH_LOWER_CENTER, 0.500f, 0.6869f)
        put(LM.INNER_MOUTH_UPPER, 0.500f, 0.6699f)
        put(LM.INNER_MOUTH_LOWER, 0.500f, 0.6749f)
        return points
    }

    // endregion

    companion object {
        private const val NUM_LANDMARKS = 66

        /** Shapes that read zero on a resting face and rise as the face deforms. */
        private val DEVIATION_SHAPES = listOf(
            BlendShape.MOUTH_CLOSE,
            BlendShape.MOUTH_STRETCH_LEFT,
            BlendShape.MOUTH_STRETCH_RIGHT,
            BlendShape.MOUTH_DIMPLE_LEFT,
            BlendShape.MOUTH_DIMPLE_RIGHT,
            BlendShape.MOUTH_PRESS_LEFT,
            BlendShape.MOUTH_PRESS_RIGHT,
            BlendShape.MOUTH_ROLL_UPPER,
            BlendShape.MOUTH_ROLL_LOWER,
            BlendShape.MOUTH_SHRUG_UPPER,
            BlendShape.MOUTH_SHRUG_LOWER,
            BlendShape.MOUTH_UPPER_UP_LEFT,
            BlendShape.MOUTH_UPPER_UP_RIGHT,
            BlendShape.MOUTH_LOWER_DOWN_LEFT,
            BlendShape.MOUTH_LOWER_DOWN_RIGHT,
        )

        private val EXPECTED_SHAPES = setOf(
            BlendShape.JAW_OPEN,
            BlendShape.JAW_LEFT,
            BlendShape.JAW_RIGHT,
            BlendShape.MOUTH_SMILE_LEFT,
            BlendShape.MOUTH_SMILE_RIGHT,
            BlendShape.MOUTH_FROWN_LEFT,
            BlendShape.MOUTH_FROWN_RIGHT,
            BlendShape.MOUTH_PUCKER,
            BlendShape.MOUTH_FUNNEL,
            BlendShape.MOUTH_LEFT,
            BlendShape.MOUTH_RIGHT,
            BlendShape.MOUTH_CLOSE,
            BlendShape.MOUTH_UPPER_UP_LEFT,
            BlendShape.MOUTH_UPPER_UP_RIGHT,
            BlendShape.MOUTH_LOWER_DOWN_LEFT,
            BlendShape.MOUTH_LOWER_DOWN_RIGHT,
            BlendShape.MOUTH_STRETCH_LEFT,
            BlendShape.MOUTH_STRETCH_RIGHT,
            BlendShape.MOUTH_DIMPLE_LEFT,
            BlendShape.MOUTH_DIMPLE_RIGHT,
            BlendShape.MOUTH_PRESS_LEFT,
            BlendShape.MOUTH_PRESS_RIGHT,
            BlendShape.MOUTH_ROLL_UPPER,
            BlendShape.MOUTH_ROLL_LOWER,
            BlendShape.MOUTH_SHRUG_UPPER,
            BlendShape.MOUTH_SHRUG_LOWER,
        )
    }
}
