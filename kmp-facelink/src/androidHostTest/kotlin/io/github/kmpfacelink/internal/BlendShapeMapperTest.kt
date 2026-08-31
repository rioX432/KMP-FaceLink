package io.github.kmpfacelink.internal

import io.github.kmpfacelink.model.BlendShape
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class BlendShapeMapperTest {

    @Test
    fun mapFromMediaPipeWithValidArKitName() {
        val categories = listOf("eyeBlinkLeft" to 0.8f)
        val result = BlendShapeMapper.mapFromMediaPipe(categories)

        assertEquals(0.8f, result[BlendShape.EYE_BLINK_LEFT])
    }

    @Test
    fun mapFromMediaPipeSkipsNeutralCategory() {
        val categories = listOf(
            "_neutral" to 1.0f,
            "eyeBlinkLeft" to 0.5f,
        )
        val result = BlendShapeMapper.mapFromMediaPipe(categories)

        assertEquals(0.5f, result[BlendShape.EYE_BLINK_LEFT])
        // _neutral should not appear as any BlendShape value > 0
        // (all unmatched shapes are filled with 0.0)
    }

    @Test
    fun mapFromMediaPipeFiltersUnknownCategoryName() {
        val categories = listOf("nonExistentBlendShape" to 0.9f)
        val result = BlendShapeMapper.mapFromMediaPipe(categories)

        // Unknown names are filtered out; all entries should be 0.0
        assertTrue(result.values.all { it == 0f })
    }

    @Test
    fun mapFromMediaPipeWithEmptyInput() {
        val result = BlendShapeMapper.mapFromMediaPipe(emptyList())

        // MediaPipe reported nothing, so there is nothing to report. Fifty-two zeros would claim
        // fifty-two measurements that never happened.
        assertTrue(result.isEmpty())
    }

    @Test
    fun mapFromMediaPipePreservesScoreValues() {
        val score = 0.42f
        val categories = listOf("jawOpen" to score)
        val result = BlendShapeMapper.mapFromMediaPipe(categories)

        assertEquals(score, result[BlendShape.JAW_OPEN])
    }

    @Test
    fun mapFromMediaPipeClampsScoreAboveOne() {
        val categories = listOf("jawOpen" to 1.5f)
        val result = BlendShapeMapper.mapFromMediaPipe(categories)

        assertEquals(1.0f, result[BlendShape.JAW_OPEN])
    }

    @Test
    fun mapFromMediaPipeClampsScoreBelowZero() {
        val categories = listOf("jawOpen" to -0.3f)
        val result = BlendShapeMapper.mapFromMediaPipe(categories)

        assertEquals(0.0f, result[BlendShape.JAW_OPEN])
    }

    @Test
    fun mapFromMediaPipeReportsOnlyWhatMediaPipeSent() {
        val categories = listOf("eyeBlinkLeft" to 0.7f)
        val result = BlendShapeMapper.mapFromMediaPipe(categories)

        assertEquals(1, result.size)
        assertEquals(0.7f, result[BlendShape.EYE_BLINK_LEFT])
    }

    @Test
    fun tongueOutIsAbsentBecauseMediaPipeHasNoSuchChannel() {
        // MediaPipe's schema has no tongueOut. Reporting it as 0f would be indistinguishable from
        // a measured resting tongue, which is the one reading this contract exists to prevent.
        val everyMediaPipeChannel = BlendShape.entries
            .filter { it != BlendShape.TONGUE_OUT }
            .map { it.arKitName to 0.5f }
        val result = BlendShapeMapper.mapFromMediaPipe(everyMediaPipeChannel)

        assertEquals(BlendShape.entries.size - 1, result.size)
        assertNull(result[BlendShape.TONGUE_OUT])
    }

    @Test
    fun mapFromMediaPipeHandlesAllBlendShapes() {
        // Build input for all 52 ARKit blend shapes
        val categories = BlendShape.entries.mapIndexed { index, shape ->
            val score = (index + 1).toFloat() / BlendShape.entries.size
            shape.arKitName to score
        }

        val result = BlendShapeMapper.mapFromMediaPipe(categories)

        assertEquals(BlendShape.entries.size, result.size)
        for ((index, shape) in BlendShape.entries.withIndex()) {
            val expectedScore = (index + 1).toFloat() / BlendShape.entries.size
            assertEquals(
                expectedScore,
                result[shape],
                "Mismatch for ${shape.name}",
            )
        }
    }

    @Test
    fun mapFromMediaPipeWithMultipleCategories() {
        val categories = listOf(
            "eyeBlinkLeft" to 0.9f,
            "eyeBlinkRight" to 0.85f,
            "jawOpen" to 0.3f,
            "mouthSmileLeft" to 0.6f,
            "tongueOut" to 0.1f,
        )
        val result = BlendShapeMapper.mapFromMediaPipe(categories)

        assertEquals(0.9f, result[BlendShape.EYE_BLINK_LEFT])
        assertEquals(0.85f, result[BlendShape.EYE_BLINK_RIGHT])
        assertEquals(0.3f, result[BlendShape.JAW_OPEN])
        assertEquals(0.6f, result[BlendShape.MOUTH_SMILE_LEFT])
        assertEquals(0.1f, result[BlendShape.TONGUE_OUT])
    }
}
