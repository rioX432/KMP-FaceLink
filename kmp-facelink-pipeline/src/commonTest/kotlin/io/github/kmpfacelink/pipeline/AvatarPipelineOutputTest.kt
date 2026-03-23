package io.github.kmpfacelink.pipeline

import io.github.kmpfacelink.actions.emotion.Emotion
import io.github.kmpfacelink.model.BlendShape
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AvatarPipelineOutputTest {

    @Test
    fun constructsWithAllFields() {
        val blendShapes = mapOf(BlendShape.jawOpen to 0.5f, BlendShape.mouthSmileLeft to 0.3f)
        val output = AvatarPipelineOutput(
            blendShapes = blendShapes,
            emotion = Emotion.HAPPY,
            text = "Hello!",
            isAudioPlaying = true,
            timestampMs = 12345L,
        )
        assertEquals(blendShapes, output.blendShapes)
        assertEquals(Emotion.HAPPY, output.emotion)
        assertEquals("Hello!", output.text)
        assertTrue(output.isAudioPlaying)
        assertEquals(12345L, output.timestampMs)
    }

    @Test
    fun nullableFieldsCanBeNull() {
        val output = AvatarPipelineOutput(
            blendShapes = emptyMap(),
            emotion = null,
            text = null,
            isAudioPlaying = false,
            timestampMs = 0L,
        )
        assertNull(output.emotion)
        assertNull(output.text)
        assertFalse(output.isAudioPlaying)
    }

    @Test
    fun equalityByValue() {
        val a = AvatarPipelineOutput(
            blendShapes = mapOf(BlendShape.jawOpen to 1.0f),
            emotion = Emotion.SAD,
            text = "text",
            isAudioPlaying = false,
            timestampMs = 100L,
        )
        val b = a.copy()
        assertEquals(a, b)
    }
}
