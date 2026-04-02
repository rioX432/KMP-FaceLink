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

    @Test
    fun mapProducesAll52BlendShapes() {
        val landmarks = neutralFaceLandmarks()
        val result = LandmarkToBlendShapeMapper.map(landmarks)
        assertEquals(BlendShape.entries.size, result.size, "Should produce all 52 blend shapes")
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
