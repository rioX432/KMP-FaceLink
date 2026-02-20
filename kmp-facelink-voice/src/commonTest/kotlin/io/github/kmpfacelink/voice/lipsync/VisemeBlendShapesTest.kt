package io.github.kmpfacelink.voice.lipsync

import io.github.kmpfacelink.model.BlendShape
import kotlin.test.Test
import kotlin.test.assertTrue

class VisemeBlendShapesTest {

    @Test
    fun silenceVisemeReturnsEmptyMap() {
        val result = VisemeBlendShapes.blendShapesFor(Viseme.SIL)
        assertTrue(result.isEmpty())
    }

    @Test
    fun allVisemesHaveMapping() {
        Viseme.entries.forEach { viseme ->
            // Should not throw — every viseme has a mapping
            VisemeBlendShapes.blendShapesFor(viseme)
        }
    }

    @Test
    fun aaVisemeHasJawOpen() {
        val shapes = VisemeBlendShapes.blendShapesFor(Viseme.AA)
        assertTrue(shapes.containsKey(BlendShape.JAW_OPEN))
        assertTrue(shapes[BlendShape.JAW_OPEN]!! > 0f)
    }

    @Test
    fun ouVisemeHasMouthFunnel() {
        val shapes = VisemeBlendShapes.blendShapesFor(Viseme.OU)
        assertTrue(shapes.containsKey(BlendShape.MOUTH_FUNNEL))
        assertTrue(shapes[BlendShape.MOUTH_FUNNEL]!! > 0f)
    }

    @Test
    fun ppVisemeHasMouthClose() {
        val shapes = VisemeBlendShapes.blendShapesFor(Viseme.PP)
        assertTrue(shapes.containsKey(BlendShape.MOUTH_CLOSE))
    }

    @Test
    fun mouthBlendShapesContainsExpectedShapes() {
        val mouthShapes = VisemeBlendShapes.mouthBlendShapes
        assertTrue(mouthShapes.contains(BlendShape.JAW_OPEN))
        assertTrue(mouthShapes.contains(BlendShape.MOUTH_FUNNEL))
        assertTrue(mouthShapes.contains(BlendShape.MOUTH_CLOSE))
    }

    @Test
    fun blendShapeValuesAreInRange() {
        Viseme.entries.forEach { viseme ->
            VisemeBlendShapes.blendShapesFor(viseme).forEach { (_, value) ->
                assertTrue(value in 0f..1f, "Value $value out of range for $viseme")
            }
        }
    }

    @Test
    fun nonMouthBlendShapesAreNotUsed() {
        val eyeAndBrowShapes = setOf(
            BlendShape.EYE_BLINK_LEFT,
            BlendShape.EYE_BLINK_RIGHT,
            BlendShape.BROW_DOWN_LEFT,
            BlendShape.BROW_DOWN_RIGHT,
        )
        val mouthShapes = VisemeBlendShapes.mouthBlendShapes
        eyeAndBrowShapes.forEach { shape ->
            assertTrue(!mouthShapes.contains(shape), "$shape should not be in mouth shapes")
        }
    }
}
