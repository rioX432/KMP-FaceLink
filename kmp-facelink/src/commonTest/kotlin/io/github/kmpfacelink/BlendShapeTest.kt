package io.github.kmpfacelink

import io.github.kmpfacelink.model.ALL_BLEND_SHAPES
import io.github.kmpfacelink.model.BlendShape
import io.github.kmpfacelink.model.emptyBlendShapeData
import io.github.kmpfacelink.model.filterByActive
import io.github.kmpfacelink.model.valueOf
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class BlendShapeTest {

    @Test
    fun blendShapeEnumHas52Entries() {
        assertEquals(52, BlendShape.entries.size)
    }

    @Test
    fun arKitNameConversion() {
        assertEquals("eyeBlinkLeft", BlendShape.EYE_BLINK_LEFT.arKitName)
        assertEquals("jawOpen", BlendShape.JAW_OPEN.arKitName)
        assertEquals("mouthSmileLeft", BlendShape.MOUTH_SMILE_LEFT.arKitName)
        assertEquals("browInnerUp", BlendShape.BROW_INNER_UP.arKitName)
        assertEquals("cheekPuff", BlendShape.CHEEK_PUFF.arKitName)
        assertEquals("tongueOut", BlendShape.TONGUE_OUT.arKitName)
        assertEquals("noseSneerLeft", BlendShape.NOSE_SNEER_LEFT.arKitName)
    }

    @Test
    fun fromArKitNameReturnsCorrectBlendShape() {
        assertEquals(BlendShape.EYE_BLINK_LEFT, BlendShape.fromArKitName("eyeBlinkLeft"))
        assertEquals(BlendShape.JAW_OPEN, BlendShape.fromArKitName("jawOpen"))
        assertEquals(BlendShape.TONGUE_OUT, BlendShape.fromArKitName("tongueOut"))
    }

    @Test
    fun fromArKitNameReturnsNullForUnknown() {
        assertNull(BlendShape.fromArKitName("unknownShape"))
        assertNull(BlendShape.fromArKitName("_neutral"))
        assertNull(BlendShape.fromArKitName(""))
    }

    @Test
    fun emptyBlendShapeDataHasAllEntries() {
        val data = emptyBlendShapeData()
        assertEquals(52, data.size)
        for (shape in BlendShape.entries) {
            assertEquals(0f, data[shape])
        }
    }

    @Test
    fun valueOfReturnsDefaultForMissing() {
        val data = mapOf(BlendShape.JAW_OPEN to 0.8f)
        assertEquals(0.8f, data.valueOf(BlendShape.JAW_OPEN))
        assertEquals(0f, data.valueOf(BlendShape.EYE_BLINK_LEFT))
    }

    @Test
    fun allArKitNamesAreUnique() {
        val names = BlendShape.entries.map { it.arKitName }
        assertEquals(names.size, names.toSet().size)
    }

    @Test
    fun roundTripArKitName() {
        for (shape in BlendShape.entries) {
            val name = shape.arKitName
            val restored = BlendShape.fromArKitName(name)
            assertNotNull(restored, "Failed to round-trip: $shape → $name")
            assertEquals(shape, restored)
        }
    }

    @Test
    fun allBlendShapesContainsAll52() {
        assertEquals(52, ALL_BLEND_SHAPES.size)
        for (shape in BlendShape.entries) {
            assertTrue(shape in ALL_BLEND_SHAPES)
        }
    }

    @Test
    fun filterByActiveReturnsIdentityWhenAllActive() {
        val data = emptyBlendShapeData()
        val result = data.filterByActive(ALL_BLEND_SHAPES)
        assertSame(data, result)
    }

    @Test
    fun filterByActiveReturnsSubsetOnly() {
        val data = mapOf(
            BlendShape.EYE_BLINK_LEFT to 0.5f,
            BlendShape.JAW_OPEN to 0.8f,
            BlendShape.MOUTH_SMILE_LEFT to 0.3f,
        )
        val active = setOf(BlendShape.EYE_BLINK_LEFT, BlendShape.JAW_OPEN)
        val result = data.filterByActive(active)
        assertEquals(2, result.size)
        assertEquals(0.5f, result[BlendShape.EYE_BLINK_LEFT])
        assertEquals(0.8f, result[BlendShape.JAW_OPEN])
        assertNull(result[BlendShape.MOUTH_SMILE_LEFT])
    }

    @Test
    fun filterByActiveDefaultsMissingToZero() {
        val data = mapOf(BlendShape.EYE_BLINK_LEFT to 0.5f)
        val active = setOf(BlendShape.EYE_BLINK_LEFT, BlendShape.JAW_OPEN)
        val result = data.filterByActive(active)
        assertEquals(2, result.size)
        assertEquals(0.5f, result[BlendShape.EYE_BLINK_LEFT])
        assertEquals(0f, result[BlendShape.JAW_OPEN])
    }
}
