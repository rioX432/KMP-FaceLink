package io.github.kmpfacelink.pipeline.expression

import io.github.kmpfacelink.actions.emotion.Emotion
import io.github.kmpfacelink.model.BlendShape
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ExpressionMapperTest {

    private val mapper = DefaultExpressionMapper()

    @Test
    fun happyWithIntensityProducesScaledSmile() {
        val result = mapper.map(Emotion.HAPPY, 0.8f)
        assertTrue(result.isNotEmpty())
        // HAPPY template includes MOUTH_SMILE_LEFT at 0.7 weight
        // 0.7 * 0.8 = 0.56
        val smileLeft = result[BlendShape.MOUTH_SMILE_LEFT]
        assertTrue(smileLeft != null && smileLeft > 0f)
        assertEquals(0.56f, smileLeft!!, 0.01f)
    }

    @Test
    fun fullIntensityReturnsTemplateWeights() {
        val result = mapper.map(Emotion.HAPPY, 1.0f)
        // At full intensity, weights should match template exactly
        val smileLeft = result[BlendShape.MOUTH_SMILE_LEFT]
        assertEquals(0.7f, smileLeft!!, 0.01f)
    }

    @Test
    fun zeroIntensityReturnsZeroWeights() {
        val result = mapper.map(Emotion.HAPPY, 0f)
        for ((_, value) in result) {
            assertEquals(0f, value, 0.001f)
        }
    }

    @Test
    fun neutralReturnsEmptyMap() {
        val result = mapper.map(Emotion.NEUTRAL, 1.0f)
        assertTrue(result.isEmpty())
    }

    @Test
    fun allValuesClampedToZeroOne() {
        // Even with intensity > 1 (edge case), values should be clamped
        val result = mapper.map(Emotion.HAPPY, 2.0f)
        for ((_, value) in result) {
            assertTrue(value in 0f..1f)
        }
    }

    @Test
    fun angryProducesBrowDown() {
        val result = mapper.map(Emotion.ANGRY, 1.0f)
        val browDown = result[BlendShape.BROW_DOWN_LEFT]
        assertTrue(browDown != null && browDown > 0f)
    }

    @Test
    fun surprisedProducesWideEyes() {
        val result = mapper.map(Emotion.SURPRISED, 0.5f)
        val eyeWide = result[BlendShape.EYE_WIDE_LEFT]
        assertTrue(eyeWide != null && eyeWide > 0f)
    }
}
