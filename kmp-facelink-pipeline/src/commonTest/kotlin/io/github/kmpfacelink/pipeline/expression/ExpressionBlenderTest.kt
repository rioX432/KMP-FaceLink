package io.github.kmpfacelink.pipeline.expression

import io.github.kmpfacelink.model.BlendShape
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ExpressionBlenderTest {

    private val blender = ExpressionBlender()

    @Test
    fun mouthRegionUsesLipSyncValues() {
        val lipSync = mapOf(
            BlendShape.JAW_OPEN to 0.8f,
            BlendShape.MOUTH_SMILE_LEFT to 0.6f,
        )
        val emotion = mapOf(
            BlendShape.JAW_OPEN to 0.3f,
            BlendShape.MOUTH_SMILE_LEFT to 0.9f,
        )
        val result = blender.blend(lipSync, emotion)
        // Mouth region: lip sync dominates (lipSyncWeight=1.0)
        assertEquals(0.8f, result[BlendShape.JAW_OPEN]!!, 0.01f)
        assertEquals(0.6f, result[BlendShape.MOUTH_SMILE_LEFT]!!, 0.01f)
    }

    @Test
    fun eyeBrowRegionUsesEmotionValues() {
        val lipSync = mapOf(
            BlendShape.EYE_WIDE_LEFT to 0.2f,
            BlendShape.BROW_DOWN_LEFT to 0.1f,
        )
        val emotion = mapOf(
            BlendShape.EYE_WIDE_LEFT to 0.9f,
            BlendShape.BROW_DOWN_LEFT to 0.8f,
        )
        val result = blender.blend(lipSync, emotion)
        // Eye/brow region: emotion dominates (emotionWeight=0.7)
        assertEquals(0.63f, result[BlendShape.EYE_WIDE_LEFT]!!, 0.01f)
        assertEquals(0.56f, result[BlendShape.BROW_DOWN_LEFT]!!, 0.01f)
    }

    @Test
    fun cheekRegionUsesEmotionValues() {
        val lipSync = mapOf(BlendShape.CHEEK_SQUINT_LEFT to 0.1f)
        val emotion = mapOf(BlendShape.CHEEK_SQUINT_LEFT to 0.5f)
        val result = blender.blend(lipSync, emotion)
        // Cheek is eye/brow region
        assertEquals(0.35f, result[BlendShape.CHEEK_SQUINT_LEFT]!!, 0.01f)
    }

    @Test
    fun otherRegionUsesHigherValue() {
        val lipSync = mapOf(BlendShape.NOSE_SNEER_LEFT to 0.3f)
        val emotion = mapOf(BlendShape.NOSE_SNEER_LEFT to 0.7f)
        val result = blender.blend(lipSync, emotion)
        assertEquals(0.7f, result[BlendShape.NOSE_SNEER_LEFT]!!, 0.01f)
    }

    @Test
    fun tongueRegionUsesLipSync() {
        val lipSync = mapOf(BlendShape.TONGUE_OUT to 0.5f)
        val emotion = mapOf(BlendShape.TONGUE_OUT to 0.9f)
        val result = blender.blend(lipSync, emotion)
        assertEquals(0.5f, result[BlendShape.TONGUE_OUT]!!, 0.01f)
    }

    @Test
    fun emptyInputsProduceEmptyOutput() {
        val result = blender.blend(emptyMap(), emptyMap())
        assertTrue(result.isEmpty())
    }

    @Test
    fun missingKeysInOneSourceTreatedAsZero() {
        val lipSync = mapOf(BlendShape.MOUTH_FUNNEL to 0.4f)
        val emotion = mapOf(BlendShape.EYE_BLINK_LEFT to 0.6f)
        val result = blender.blend(lipSync, emotion)
        // Mouth from lip sync only
        assertEquals(0.4f, result[BlendShape.MOUTH_FUNNEL]!!, 0.01f)
        // Eye from emotion only (0.6 * 0.7 = 0.42)
        assertEquals(0.42f, result[BlendShape.EYE_BLINK_LEFT]!!, 0.01f)
    }

    @Test
    fun valuesClampedToZeroOne() {
        val blender = ExpressionBlender(lipSyncWeight = 2.0f, emotionWeight = 2.0f)
        val lipSync = mapOf(BlendShape.JAW_OPEN to 0.9f)
        val emotion = mapOf(BlendShape.EYE_WIDE_LEFT to 0.9f)
        val result = blender.blend(lipSync, emotion)
        assertTrue(result[BlendShape.JAW_OPEN]!! <= 1.0f)
        assertTrue(result[BlendShape.EYE_WIDE_LEFT]!! <= 1.0f)
    }
}
