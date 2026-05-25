package io.github.kmpfacelink.actions.effect

import io.github.kmpfacelink.actions.ActionEvent
import io.github.kmpfacelink.interact.model.AvatarAnchor
import io.github.kmpfacelink.interact.model.EffectParameter
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals

class EffectBindingTest {

    private val throwEffect = EffectParameter.ThrowEffect(
        durationMs = 1000L,
        intensity = 0.8f,
        anchor = AvatarAnchor.HEAD,
        objectName = "tomato",
        count = 3,
    )

    private val screenShake = EffectParameter.ScreenShake(
        durationMs = 500L,
        intensity = 1.0f,
        amplitude = 0.5f,
    )

    // --- EffectFactory.Static ---

    @Test
    fun staticFactoryReturnsSameEffectForStarted() {
        val factory = EffectFactory.Static(throwEffect)
        val event = ActionEvent.Started(actionId = "test", timestampMs = 100L)

        val result = factory.create(event)

        assertEquals(throwEffect, result)
    }

    @Test
    fun staticFactoryReturnsSameEffectForHeld() {
        val factory = EffectFactory.Static(throwEffect)
        val event = ActionEvent.Held(
            actionId = "test",
            timestampMs = 200L,
            durationMs = 1500L,
        )

        val result = factory.create(event)

        assertEquals(throwEffect, result)
    }

    @Test
    fun staticFactoryReturnsSameEffectForReleased() {
        val factory = EffectFactory.Static(throwEffect)
        val event = ActionEvent.Released(
            actionId = "test",
            timestampMs = 300L,
            totalDurationMs = 2000L,
        )

        val result = factory.create(event)

        assertEquals(throwEffect, result)
    }

    // --- EffectFactory.Scaled ---

    @Test
    fun scaledFactoryUsesFullIntensityForStarted() {
        val factory = EffectFactory.Scaled(
            baseEffect = screenShake,
            maxDurationMs = 5000L,
        )
        val event = ActionEvent.Started(actionId = "test", timestampMs = 100L)

        val result = factory.create(event)

        assertEquals(screenShake.intensity, result.intensity)
    }

    @Test
    fun scaledFactoryScalesIntensityByHoldDuration() {
        val factory = EffectFactory.Scaled(
            baseEffect = screenShake,
            maxDurationMs = 4000L,
        )
        val event = ActionEvent.Held(
            actionId = "test",
            timestampMs = 200L,
            durationMs = 2000L,
        )

        val result = factory.create(event)

        // scale = 2000 / 4000 = 0.5, intensity = 1.0 * 0.5 = 0.5
        assertEquals(0.5f, result.intensity)
    }

    @Test
    fun scaledFactoryScalesIntensityByReleaseDuration() {
        val factory = EffectFactory.Scaled(
            baseEffect = screenShake,
            maxDurationMs = 2000L,
        )
        val event = ActionEvent.Released(
            actionId = "test",
            timestampMs = 300L,
            totalDurationMs = 1000L,
        )

        val result = factory.create(event)

        // scale = 1000 / 2000 = 0.5, intensity = 1.0 * 0.5 = 0.5
        assertEquals(0.5f, result.intensity)
    }

    @Test
    fun scaledFactoryClampsIntensityAtOne() {
        val factory = EffectFactory.Scaled(
            baseEffect = screenShake,
            maxDurationMs = 1000L,
        )
        val event = ActionEvent.Held(
            actionId = "test",
            timestampMs = 200L,
            durationMs = 9999L,
        )

        val result = factory.create(event)

        // scale = coerceIn(0, 1) = 1.0, intensity = 1.0 * 1.0 = 1.0
        assertEquals(1.0f, result.intensity)
    }

    @Test
    fun scaledFactoryClampsIntensityAtZero() {
        val factory = EffectFactory.Scaled(
            baseEffect = screenShake,
            maxDurationMs = 5000L,
        )
        val event = ActionEvent.Held(
            actionId = "test",
            timestampMs = 200L,
            durationMs = 0L,
        )

        val result = factory.create(event)

        // scale = 0 / 5000 = 0.0, intensity = 1.0 * 0.0 = 0.0
        assertEquals(0.0f, result.intensity)
    }

    // --- Serialization ---

    @Test
    fun effectFactoryStaticSerializationRoundTrip() {
        val factory: EffectFactory = EffectFactory.Static(throwEffect)
        val json = Json.encodeToString(EffectFactory.serializer(), factory)
        val decoded = Json.decodeFromString(EffectFactory.serializer(), json)

        assertEquals(factory, decoded)
    }

    @Test
    fun effectFactoryScaledSerializationRoundTrip() {
        val factory: EffectFactory = EffectFactory.Scaled(
            baseEffect = screenShake,
            maxDurationMs = 3000L,
        )
        val json = Json.encodeToString(EffectFactory.serializer(), factory)
        val decoded = Json.decodeFromString(EffectFactory.serializer(), json)

        assertEquals(factory, decoded)
    }

    @Test
    fun effectEmissionSerializationRoundTrip() {
        val emission = EffectEmission(
            bindingId = "binding-1",
            effect = throwEffect,
            timestampMs = 12345L,
            state = EmissionState.STARTED,
        )
        val json = Json.encodeToString(EffectEmission.serializer(), emission)
        val decoded = Json.decodeFromString(EffectEmission.serializer(), json)

        assertEquals(emission, decoded)
    }
}
