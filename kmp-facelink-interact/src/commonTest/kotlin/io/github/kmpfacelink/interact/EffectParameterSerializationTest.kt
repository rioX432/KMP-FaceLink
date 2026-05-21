package io.github.kmpfacelink.interact

import io.github.kmpfacelink.interact.model.AvatarAnchor
import io.github.kmpfacelink.interact.model.EffectParameter
import io.github.kmpfacelink.model.BlendShape
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals

class EffectParameterSerializationTest {

    private val json = Json { encodeDefaults = true }

    @Test
    fun throwEffectRoundTrip() {
        val original: EffectParameter = EffectParameter.ThrowEffect(
            durationMs = THROW_DURATION,
            intensity = THROW_INTENSITY,
            anchor = AvatarAnchor.HEAD,
            objectName = "tomato",
            count = THROW_COUNT,
        )

        val encoded = json.encodeToString(EffectParameter.serializer(), original)
        val decoded = json.decodeFromString(EffectParameter.serializer(), encoded)

        assertEquals(original, decoded)
    }

    @Test
    fun throwEffectWithDefaultCountRoundTrip() {
        val original: EffectParameter = EffectParameter.ThrowEffect(
            durationMs = THROW_DURATION,
            intensity = THROW_INTENSITY,
            anchor = AvatarAnchor.LEFT_HAND,
            objectName = "heart",
        )

        val encoded = json.encodeToString(EffectParameter.serializer(), original)
        val decoded = json.decodeFromString(EffectParameter.serializer(), encoded)

        assertEquals(original, decoded)
    }

    @Test
    fun expressionOverrideRoundTrip() {
        val original: EffectParameter = EffectParameter.ExpressionOverride(
            durationMs = EXPRESSION_DURATION,
            intensity = EXPRESSION_INTENSITY,
            blendShapes = mapOf(
                BlendShape.MOUTH_SMILE_LEFT to SMILE_VALUE,
                BlendShape.MOUTH_SMILE_RIGHT to SMILE_VALUE,
            ),
        )

        val encoded = json.encodeToString(EffectParameter.serializer(), original)
        val decoded = json.decodeFromString(EffectParameter.serializer(), encoded)

        assertEquals(original, decoded)
    }

    @Test
    fun screenShakeRoundTrip() {
        val original: EffectParameter = EffectParameter.ScreenShake(
            durationMs = SHAKE_DURATION,
            intensity = SHAKE_INTENSITY,
            amplitude = SHAKE_AMPLITUDE,
        )

        val encoded = json.encodeToString(EffectParameter.serializer(), original)
        val decoded = json.decodeFromString(EffectParameter.serializer(), encoded)

        assertEquals(original, decoded)
    }

    @Test
    fun polymorphicDeserializationPreservesType() {
        val throwEffect: EffectParameter = EffectParameter.ThrowEffect(
            durationMs = THROW_DURATION,
            intensity = THROW_INTENSITY,
            anchor = AvatarAnchor.SCREEN,
            objectName = "confetti",
        )
        val shake: EffectParameter = EffectParameter.ScreenShake(
            durationMs = SHAKE_DURATION,
            intensity = SHAKE_INTENSITY,
            amplitude = SHAKE_AMPLITUDE,
        )

        val throwJson = json.encodeToString(EffectParameter.serializer(), throwEffect)
        val shakeJson = json.encodeToString(EffectParameter.serializer(), shake)

        val decodedThrow = json.decodeFromString(EffectParameter.serializer(), throwJson)
        val decodedShake = json.decodeFromString(EffectParameter.serializer(), shakeJson)

        assertEquals(true, decodedThrow is EffectParameter.ThrowEffect)
        assertEquals(true, decodedShake is EffectParameter.ScreenShake)
    }

    companion object {
        private const val THROW_DURATION = 2000L
        private const val THROW_INTENSITY = 0.8f
        private const val THROW_COUNT = 5
        private const val EXPRESSION_DURATION = 5000L
        private const val EXPRESSION_INTENSITY = 1.0f
        private const val SMILE_VALUE = 0.9f
        private const val SHAKE_DURATION = 1500L
        private const val SHAKE_INTENSITY = 0.7f
        private const val SHAKE_AMPLITUDE = 0.05f
    }
}
