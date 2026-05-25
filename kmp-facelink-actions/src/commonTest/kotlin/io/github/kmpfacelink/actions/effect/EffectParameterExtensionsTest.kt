package io.github.kmpfacelink.actions.effect

import io.github.kmpfacelink.interact.model.AvatarAnchor
import io.github.kmpfacelink.interact.model.EffectParameter
import io.github.kmpfacelink.model.BlendShape
import kotlin.test.Test
import kotlin.test.assertEquals

class EffectParameterExtensionsTest {

    // --- ThrowEffect ---

    @Test
    fun withIntensityOnThrowEffectReturnsNewIntensity() {
        val original = throwEffect(intensity = 0.5f)

        val result = original.withIntensity(0.9f)

        assertEquals(0.9f, (result as EffectParameter.ThrowEffect).intensity)
    }

    @Test
    fun withIntensityOnThrowEffectPreservesAllOtherFields() {
        val original = throwEffect(intensity = 0.5f)

        val result = original.withIntensity(0.9f) as EffectParameter.ThrowEffect

        assertEquals(original.durationMs, result.durationMs)
        assertEquals(original.anchor, result.anchor)
        assertEquals(original.objectName, result.objectName)
        assertEquals(original.count, result.count)
    }

    // --- ExpressionOverride ---

    @Test
    fun withIntensityOnExpressionOverrideReturnsNewIntensity() {
        val original = expressionOverride(intensity = 0.6f)

        val result = original.withIntensity(0.3f)

        assertEquals(0.3f, (result as EffectParameter.ExpressionOverride).intensity)
    }

    @Test
    fun withIntensityOnExpressionOverridePreservesBlendShapes() {
        val blendShapes = mapOf(
            BlendShape.MOUTH_SMILE_LEFT to 0.8f,
            BlendShape.MOUTH_SMILE_RIGHT to 0.7f,
        )
        val original = EffectParameter.ExpressionOverride(
            durationMs = 800L,
            intensity = 0.6f,
            blendShapes = blendShapes,
        )

        val result = original.withIntensity(1.0f) as EffectParameter.ExpressionOverride

        assertEquals(original.durationMs, result.durationMs)
        assertEquals(blendShapes, result.blendShapes)
    }

    // --- ScreenShake ---

    @Test
    fun withIntensityOnScreenShakeReturnsNewIntensity() {
        val original = screenShake(intensity = 0.7f)

        val result = original.withIntensity(0.2f)

        assertEquals(0.2f, (result as EffectParameter.ScreenShake).intensity)
    }

    @Test
    fun withIntensityOnScreenShakePreservesAmplitude() {
        val original = screenShake(intensity = 0.7f)

        val result = original.withIntensity(0.2f) as EffectParameter.ScreenShake

        assertEquals(original.durationMs, result.durationMs)
        assertEquals(original.amplitude, result.amplitude)
    }

    // --- Edge cases ---

    @Test
    fun withIntensityZero() {
        val original = throwEffect(intensity = 0.8f)

        val result = original.withIntensity(0.0f)

        assertEquals(0.0f, (result as EffectParameter.ThrowEffect).intensity)
    }

    @Test
    fun withIntensityOne() {
        val original = screenShake(intensity = 0.3f)

        val result = original.withIntensity(1.0f)

        assertEquals(1.0f, (result as EffectParameter.ScreenShake).intensity)
    }

    @Test
    fun withIntensityAboveOnePassesThrough() {
        val original = expressionOverride(intensity = 0.5f)

        val result = original.withIntensity(2.5f)

        assertEquals(2.5f, (result as EffectParameter.ExpressionOverride).intensity)
    }

    @Test
    fun withIntensityNegativePassesThrough() {
        val original = throwEffect(intensity = 0.5f)

        val result = original.withIntensity(-0.3f)

        assertEquals(-0.3f, (result as EffectParameter.ThrowEffect).intensity)
    }

    // --- Helpers ---

    private fun throwEffect(intensity: Float) = EffectParameter.ThrowEffect(
        durationMs = 1000L,
        intensity = intensity,
        anchor = AvatarAnchor.HEAD,
        objectName = "tomato",
        count = 3,
    )

    private fun expressionOverride(intensity: Float) = EffectParameter.ExpressionOverride(
        durationMs = 800L,
        intensity = intensity,
        blendShapes = mapOf(BlendShape.JAW_OPEN to 0.5f),
    )

    private fun screenShake(intensity: Float) = EffectParameter.ScreenShake(
        durationMs = 500L,
        intensity = intensity,
        amplitude = 0.4f,
    )
}
