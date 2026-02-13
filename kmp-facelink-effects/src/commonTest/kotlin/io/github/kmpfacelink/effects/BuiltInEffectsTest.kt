package io.github.kmpfacelink.effects

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class BuiltInEffectsTest {

    @Test
    fun catEarsIsAnchorEffect() {
        val effect = catEarsEffect()
        assertIs<Effect.AnchorEffect>(effect)
        assertEquals("catEars", effect.id)
        assertEquals(AnchorPoint.FOREHEAD, effect.anchor)
        assertEquals(RotationSource.HEAD_TRANSFORM, effect.rotationSource)
        assertTrue(effect.enabled)
    }

    @Test
    fun glassesIsAnchorEffect() {
        val effect = glassesEffect()
        assertIs<Effect.AnchorEffect>(effect)
        assertEquals("glasses", effect.id)
        assertEquals(AnchorPoint.NOSE_BRIDGE, effect.anchor)
    }

    @Test
    fun smileHeartsIsExpressionEffect() {
        val effect = smileHeartsEffect()
        assertIs<Effect.ExpressionEffect>(effect)
        assertEquals("smileHearts", effect.id)
        assertEquals(2, effect.blendShapes.size)
        assertIs<IntensityMapping.Linear>(effect.mapping)
    }

    @Test
    fun winkSparkleIsExpressionEffect() {
        val effect = winkSparkleEffect()
        assertIs<Effect.ExpressionEffect>(effect)
        assertEquals("winkSparkle", effect.id)
        assertEquals(1, effect.blendShapes.size)
        assertIs<IntensityMapping.Step>(effect.mapping)
    }

    @Test
    fun openPalmParticlesIsHandEffect() {
        val effect = openPalmParticlesEffect()
        assertIs<Effect.HandEffect>(effect)
        assertEquals("openPalmParticles", effect.id)
    }

    @Test
    fun cartoonEyesIsTransformEffect() {
        val effect = cartoonEyesEffect()
        assertIs<Effect.TransformEffect>(effect)
        assertEquals("cartoonEyes", effect.id)
    }

    @Test
    fun customIdsWork() {
        assertEquals("myEars", catEarsEffect("myEars").id)
        assertEquals("myGlasses", glassesEffect("myGlasses").id)
        assertEquals("myHearts", smileHeartsEffect("myHearts").id)
        assertEquals("mySparkle", winkSparkleEffect("mySparkle").id)
        assertEquals("myPalm", openPalmParticlesEffect("myPalm").id)
        assertEquals("myEyes", cartoonEyesEffect("myEyes").id)
    }
}
