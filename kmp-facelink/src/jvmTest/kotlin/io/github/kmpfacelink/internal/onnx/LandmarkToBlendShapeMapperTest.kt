package io.github.kmpfacelink.internal.onnx

import io.github.kmpfacelink.model.BlendShape
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LandmarkToBlendShapeMapperTest {

    /**
     * Create a neutral face landmark array where all 66 points
     * form a roughly symmetric face in normalized [0,1] coordinates.
     */
    private fun neutralFaceLandmarks(): FloatArray {
        val landmarks = FloatArray(NUM_LANDMARKS * 2)
        // Place landmarks in a canonical neutral position
        for (i in 0 until NUM_LANDMARKS) {
            landmarks[i * 2] = NEUTRAL_POSITIONS[i * 2]
            landmarks[i * 2 + 1] = NEUTRAL_POSITIONS[i * 2 + 1]
        }
        return landmarks
    }

    /**
     * Pins the exact set this estimator emits.
     *
     * A shape only joins this set once a solver measures it, so adding a solver is a deliberate
     * two-part change: the solver, and this expectation. That is what stops an implementation gap
     * from quietly becoming, or quietly ceasing to be, part of the published schema.
     */
    @Test
    fun emittedShapesAreExactlyTheMeasuredSet() {
        val result = LandmarkToBlendShapeMapper.map(neutralFaceLandmarks())
        assertEquals(EMITTED_SHAPES, result.keys)
    }

    @Test
    fun shapesThisInputCannotObserveAreAbsentRatherThanZero() {
        val result = LandmarkToBlendShapeMapper.map(neutralFaceLandmarks())
        for (shape in NOT_EMITTED_SHAPES) {
            assertTrue(shape !in result, "$shape must be absent, not present as ${result[shape]}")
        }
        assertEquals(BlendShape.entries.size, EMITTED_SHAPES.size + NOT_EMITTED_SHAPES.size)
    }

    @Test
    fun allValuesInZeroToOneRange() {
        val landmarks = neutralFaceLandmarks()
        val result = LandmarkToBlendShapeMapper.map(landmarks)
        for ((shape, value) in result) {
            assertTrue(value in 0f..1f, "$shape value $value is outside [0,1]")
        }
    }

    @Test
    fun neutralFaceHasLowBlinkValues() {
        val landmarks = neutralFaceLandmarks()
        val result = LandmarkToBlendShapeMapper.map(landmarks)
        val leftBlink = result[BlendShape.EYE_BLINK_LEFT] ?: 0f
        val rightBlink = result[BlendShape.EYE_BLINK_RIGHT] ?: 0f
        // Neutral face: eyes open, blink should be low
        assertTrue(leftBlink < 0.5f, "Left blink $leftBlink should be low for neutral face")
        assertTrue(rightBlink < 0.5f, "Right blink $rightBlink should be low for neutral face")
    }

    @Test
    fun closedEyeProducesHighBlink() {
        val landmarks = neutralFaceLandmarks()
        // Close right eye by moving upper and lower lids together
        // Right eye indices: 36(outer), 37(upper1), 38(upper2), 39(inner), 40(lower2), 41(lower1)
        val midY = (landmarks[RIGHT_EYE_UPPER * 2 + 1] + landmarks[RIGHT_EYE_LOWER * 2 + 1]) / 2f
        landmarks[RIGHT_EYE_UPPER * 2 + 1] = midY
        landmarks[RIGHT_EYE_LOWER * 2 + 1] = midY
        landmarks[RIGHT_EYE_UPPER2 * 2 + 1] = midY
        landmarks[RIGHT_EYE_LOWER2 * 2 + 1] = midY

        val result = LandmarkToBlendShapeMapper.map(landmarks)
        val rightBlink = result[BlendShape.EYE_BLINK_RIGHT] ?: 0f
        assertTrue(rightBlink > 0.5f, "Right blink $rightBlink should be high for closed eye")
    }

    @Test
    fun openMouthProducesJawOpen() {
        val landmarks = neutralFaceLandmarks()
        // Move inner mouth lower point down significantly
        landmarks[INNER_MOUTH_LOWER * 2 + 1] += 0.08f

        val result = LandmarkToBlendShapeMapper.map(landmarks)
        val jawOpen = result[BlendShape.JAW_OPEN] ?: 0f
        assertTrue(jawOpen > 0.2f, "Jaw open $jawOpen should be elevated for open mouth")
    }

    companion object {
        private const val NUM_LANDMARKS = 66

        /** Shapes measured by the eye, brow, and mouth solvers. */
        private val EMITTED_SHAPES = setOf(
            BlendShape.EYE_BLINK_LEFT, BlendShape.EYE_BLINK_RIGHT,
            BlendShape.EYE_SQUINT_LEFT, BlendShape.EYE_SQUINT_RIGHT,
            BlendShape.EYE_WIDE_LEFT, BlendShape.EYE_WIDE_RIGHT,
            BlendShape.BROW_DOWN_LEFT, BlendShape.BROW_DOWN_RIGHT,
            BlendShape.BROW_INNER_UP,
            BlendShape.BROW_OUTER_UP_LEFT, BlendShape.BROW_OUTER_UP_RIGHT,
            BlendShape.NOSE_SNEER_LEFT, BlendShape.NOSE_SNEER_RIGHT,
            BlendShape.JAW_OPEN, BlendShape.JAW_LEFT, BlendShape.JAW_RIGHT,
            BlendShape.MOUTH_SMILE_LEFT, BlendShape.MOUTH_SMILE_RIGHT,
            BlendShape.MOUTH_FROWN_LEFT, BlendShape.MOUTH_FROWN_RIGHT,
            BlendShape.MOUTH_PUCKER, BlendShape.MOUTH_FUNNEL,
            BlendShape.MOUTH_LEFT, BlendShape.MOUTH_RIGHT,
            BlendShape.MOUTH_CLOSE,
            BlendShape.MOUTH_UPPER_UP_LEFT, BlendShape.MOUTH_UPPER_UP_RIGHT,
            BlendShape.MOUTH_LOWER_DOWN_LEFT, BlendShape.MOUTH_LOWER_DOWN_RIGHT,
            BlendShape.MOUTH_STRETCH_LEFT, BlendShape.MOUTH_STRETCH_RIGHT,
            BlendShape.MOUTH_DIMPLE_LEFT, BlendShape.MOUTH_DIMPLE_RIGHT,
            BlendShape.MOUTH_PRESS_LEFT, BlendShape.MOUTH_PRESS_RIGHT,
            BlendShape.MOUTH_ROLL_UPPER, BlendShape.MOUTH_ROLL_LOWER,
            BlendShape.MOUTH_SHRUG_UPPER, BlendShape.MOUTH_SHRUG_LOWER,
        )

        /**
         * Shapes this estimator does not emit today.
         *
         * The eight gaze shapes need iris and pupil points, which the 66-point set does not carry;
         * tongue has no observation point at all; jaw protrusion is not visible in a 2D contour;
         * and the three cheek shapes are the subject of separate work. None of these is a statement
         * that the platform can never support them.
         */
        private val NOT_EMITTED_SHAPES = setOf(
            BlendShape.EYE_LOOK_IN_LEFT, BlendShape.EYE_LOOK_OUT_LEFT,
            BlendShape.EYE_LOOK_UP_LEFT, BlendShape.EYE_LOOK_DOWN_LEFT,
            BlendShape.EYE_LOOK_IN_RIGHT, BlendShape.EYE_LOOK_OUT_RIGHT,
            BlendShape.EYE_LOOK_UP_RIGHT, BlendShape.EYE_LOOK_DOWN_RIGHT,
            BlendShape.TONGUE_OUT, BlendShape.JAW_FORWARD,
            BlendShape.CHEEK_PUFF,
            BlendShape.CHEEK_SQUINT_LEFT, BlendShape.CHEEK_SQUINT_RIGHT,
        )

        // Right eye landmark indices
        private const val RIGHT_EYE_UPPER = 37
        private const val RIGHT_EYE_UPPER2 = 38
        private const val RIGHT_EYE_LOWER = 41
        private const val RIGHT_EYE_LOWER2 = 40

        // Inner mouth
        private const val INNER_MOUTH_LOWER = 64

        // Approximate neutral face positions for 66 OpenSeeFace landmarks
        // x,y pairs in [0,1] normalized coordinates
        @Suppress("LongMethod")
        private val NEUTRAL_POSITIONS: FloatArray by lazy {
            floatArrayOf(
                // 0-16: Jaw contour (left to right from viewer's perspective)
                0.25f, 0.45f, 0.27f, 0.52f, 0.29f, 0.59f, 0.31f, 0.65f,
                0.33f, 0.70f, 0.37f, 0.74f, 0.41f, 0.77f, 0.46f, 0.79f,
                0.50f, 0.80f, 0.54f, 0.79f, 0.59f, 0.77f, 0.63f, 0.74f,
                0.67f, 0.70f, 0.69f, 0.65f, 0.71f, 0.59f, 0.73f, 0.52f,
                0.75f, 0.45f,
                // 17-21: Right eyebrow
                0.32f, 0.35f, 0.35f, 0.33f, 0.39f, 0.32f, 0.43f, 0.33f,
                0.46f, 0.35f,
                // 22-26: Left eyebrow
                0.54f, 0.35f, 0.57f, 0.33f, 0.61f, 0.32f, 0.65f, 0.33f,
                0.68f, 0.35f,
                // 27-35: Nose
                0.50f, 0.40f, 0.50f, 0.45f, 0.50f, 0.50f, 0.50f, 0.55f,
                0.45f, 0.57f, 0.47f, 0.58f, 0.50f, 0.59f, 0.53f, 0.58f,
                0.55f, 0.57f,
                // 36-41: Right eye
                0.35f, 0.40f, 0.37f, 0.38f, 0.40f, 0.38f, 0.43f, 0.40f,
                0.40f, 0.42f, 0.37f, 0.42f,
                // 42-47: Left eye
                0.57f, 0.40f, 0.60f, 0.38f, 0.63f, 0.38f, 0.65f, 0.40f,
                0.63f, 0.42f, 0.60f, 0.42f,
                // 48-59: Outer mouth
                0.42f, 0.67f, 0.44f, 0.65f, 0.47f, 0.64f, 0.50f, 0.65f,
                0.53f, 0.64f, 0.56f, 0.65f, 0.58f, 0.67f, 0.56f, 0.70f,
                0.53f, 0.71f, 0.50f, 0.72f, 0.47f, 0.71f, 0.44f, 0.70f,
                // 60-65: Inner mouth
                0.44f, 0.67f, 0.50f, 0.66f, 0.56f, 0.67f, 0.56f, 0.69f,
                0.50f, 0.69f, 0.44f, 0.69f,
            )
        }
    }
}
