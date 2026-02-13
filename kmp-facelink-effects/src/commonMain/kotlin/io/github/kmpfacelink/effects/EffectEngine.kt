package io.github.kmpfacelink.effects

import io.github.kmpfacelink.model.FaceTrackingData
import io.github.kmpfacelink.model.HandTrackingData
import io.github.kmpfacelink.model.valueOf
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Main API for the face effects engine.
 *
 * Register [Effect] definitions and feed tracking data via [processFace] / [processHand].
 * Each call returns an [EffectOutput] containing resolved anchor positions, parameters,
 * and active effect metadata for renderers to consume.
 */
public class EffectEngine {

    private val mutex = Mutex()
    private val effects = mutableMapOf<String, Effect>()

    private var latestFace: FaceTrackingData? = null
    private var latestHand: HandTrackingData? = null

    /**
     * Add an effect. Throws if an effect with the same [Effect.id] already exists.
     */
    public suspend fun addEffect(effect: Effect) {
        mutex.withLock {
            require(effect.id !in effects) {
                "Effect '${effect.id}' is already registered"
            }
            effects[effect.id] = effect
        }
    }

    /** Remove an effect by ID. Returns true if it was registered. */
    public suspend fun removeEffect(id: String): Boolean = mutex.withLock {
        effects.remove(id) != null
    }

    /** Remove all effects and reset tracking state. */
    public suspend fun clear() {
        mutex.withLock {
            effects.clear()
            latestFace = null
            latestHand = null
        }
    }

    /**
     * Feed a face tracking frame and compute effect output.
     *
     * @return [EffectOutput] with all resolved anchors, parameters, and active effects.
     */
    public suspend fun processFace(data: FaceTrackingData): EffectOutput = mutex.withLock {
        latestFace = data
        buildOutput(data.timestampMs)
    }

    /**
     * Feed a hand tracking frame and compute effect output.
     *
     * @return [EffectOutput] with all resolved anchors, parameters, and active effects.
     */
    public suspend fun processHand(data: HandTrackingData): EffectOutput = mutex.withLock {
        latestHand = data
        buildOutput(data.timestampMs)
    }

    private fun buildOutput(timestampMs: Long): EffectOutput {
        val anchors = mutableMapOf<String, AnchorData>()
        val parameters = mutableMapOf<String, Float>()
        val activeEffects = mutableListOf<ActiveEffect>()

        for (effect in effects.values) {
            if (!effect.enabled) continue
            when (effect) {
                is Effect.AnchorEffect -> processAnchorEffect(effect, anchors, activeEffects)
                is Effect.ExpressionEffect -> processExpressionEffect(effect, parameters, activeEffects)
                is Effect.HandEffect -> processHandEffect(effect, parameters, activeEffects)
                is Effect.TransformEffect -> processTransformEffect(effect, parameters, activeEffects)
                is Effect.AmbientEffect -> processAmbientEffect(effect, parameters, activeEffects)
            }
        }

        return EffectOutput(
            anchors = anchors,
            parameters = parameters,
            activeEffects = activeEffects,
            timestampMs = timestampMs,
        )
    }

    private fun processAnchorEffect(
        effect: Effect.AnchorEffect,
        anchors: MutableMap<String, AnchorData>,
        activeEffects: MutableList<ActiveEffect>,
    ) {
        val face = latestFace ?: return
        val anchor = AnchorResolver.resolve(
            face.landmarks,
            effect.anchor.landmarkIndex,
            effect.rotationSource,
            face.headTransform,
        ) ?: return
        anchors[effect.id] = anchor
        activeEffects.add(ActiveEffect(effect.id, EffectType.ANCHOR, 1f))
    }

    private fun processExpressionEffect(
        effect: Effect.ExpressionEffect,
        parameters: MutableMap<String, Float>,
        activeEffects: MutableList<ActiveEffect>,
    ) {
        val face = latestFace ?: return
        val intensity = ExpressionEvaluator.evaluate(
            effect.blendShapes,
            face.blendShapes,
            effect.threshold,
            effect.mapping,
        )
        parameters["${effect.id}.intensity"] = intensity
        if (intensity > 0f) {
            activeEffects.add(ActiveEffect(effect.id, EffectType.EXPRESSION, intensity))
        }
    }

    private fun processHandEffect(
        effect: Effect.HandEffect,
        parameters: MutableMap<String, Float>,
        activeEffects: MutableList<ActiveEffect>,
    ) {
        val hand = latestHand ?: return
        val matchingHand = hand.hands.firstOrNull { h ->
            h.gesture == effect.gesture &&
                h.gestureConfidence >= effect.minConfidence &&
                (effect.hand == null || h.handedness == effect.hand)
        }
        val intensity = if (matchingHand != null) matchingHand.gestureConfidence else 0f
        parameters["${effect.id}.intensity"] = intensity
        if (intensity > 0f) {
            activeEffects.add(ActiveEffect(effect.id, EffectType.HAND, intensity))
        }
    }

    private fun processTransformEffect(
        effect: Effect.TransformEffect,
        parameters: MutableMap<String, Float>,
        activeEffects: MutableList<ActiveEffect>,
    ) {
        val face = latestFace ?: return
        val raw = face.blendShapes.valueOf(effect.blendShape)
        val transformed = effect.transform(raw)
        parameters[effect.id] = transformed
        if (transformed != 0f) {
            activeEffects.add(ActiveEffect(effect.id, EffectType.TRANSFORM, transformed))
        }
    }

    private fun processAmbientEffect(
        effect: Effect.AmbientEffect,
        parameters: MutableMap<String, Float>,
        activeEffects: MutableList<ActiveEffect>,
    ) {
        val intensity = effect.compute(latestFace, latestHand).coerceIn(0f, 1f)
        parameters["${effect.id}.intensity"] = intensity
        if (intensity > 0f) {
            activeEffects.add(ActiveEffect(effect.id, EffectType.AMBIENT, intensity))
        }
    }
}
