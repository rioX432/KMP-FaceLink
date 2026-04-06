package io.github.kmpfacelink.voice.lipsync

import io.github.kmpfacelink.model.BlendShape
import io.github.kmpfacelink.voice.lipsync.internal.BlendShapeOutputMap
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class BlendShapeOutputMapTest {

    @Test
    fun emptyOutputProducesEmptyMap() {
        val result = BlendShapeOutputMap.toBlendShapeMap(FloatArray(0))
        assertTrue(result.isEmpty())
    }

    @Test
    fun zeroWeightsAreOmitted() {
        val output = FloatArray(15) { 0f }
        val result = BlendShapeOutputMap.toBlendShapeMap(output)
        assertTrue(result.isEmpty(), "Zero weights should be omitted")
    }

    @Test
    fun weightsAreClamped() {
        val output = FloatArray(15) { 1.5f }
        val result = BlendShapeOutputMap.toBlendShapeMap(output)
        result.values.forEach { weight ->
            assertTrue(weight <= 1f, "Weights should be clamped to 1.0")
        }
    }

    @Test
    fun negativeWeightsAreClamped() {
        val output = FloatArray(15) { -0.5f }
        val result = BlendShapeOutputMap.toBlendShapeMap(output)
        assertTrue(result.isEmpty(), "Negative weights should be clamped to 0 and omitted")
    }

    @Test
    fun firstOutputMapsToJawOpen() {
        val output = FloatArray(15)
        output[0] = 0.7f
        val result = BlendShapeOutputMap.toBlendShapeMap(output)
        assertTrue(BlendShape.JAW_OPEN in result)
        assertEquals(0.7f, result[BlendShape.JAW_OPEN])
    }

    @Test
    fun outputShorterThanMapSizeHandled() {
        val output = floatArrayOf(0.5f, 0.3f) // Only 2 values
        val result = BlendShapeOutputMap.toBlendShapeMap(output)
        assertEquals(2, result.size)
        assertTrue(BlendShape.JAW_OPEN in result)
        assertTrue(BlendShape.JAW_FORWARD in result)
    }

    @Test
    fun outputOrderHasFifteenEntries() {
        assertEquals(15, BlendShapeOutputMap.outputOrder.size)
    }

    @Test
    fun outputOrderStartsWithJawOpen() {
        assertEquals(BlendShape.JAW_OPEN, BlendShapeOutputMap.outputOrder.first())
    }
}
