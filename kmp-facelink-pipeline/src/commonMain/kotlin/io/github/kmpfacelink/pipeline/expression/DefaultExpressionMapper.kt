package io.github.kmpfacelink.pipeline.expression

import io.github.kmpfacelink.actions.emotion.DEFAULT_EMOTION_TEMPLATES
import io.github.kmpfacelink.actions.emotion.Emotion
import io.github.kmpfacelink.actions.emotion.EmotionTemplate
import io.github.kmpfacelink.model.BlendShape

/**
 * Default [ExpressionMapper] that uses [EmotionTemplate] weights scaled by intensity.
 *
 * For each emotion, looks up the corresponding template and multiplies all
 * blend shape weights by the given intensity, clamping results to 0.0–1.0.
 * Returns an empty map for [Emotion.NEUTRAL] or unknown emotions.
 *
 * @param templates Emotion templates to use. Defaults to [DEFAULT_EMOTION_TEMPLATES].
 */
public class DefaultExpressionMapper(
    private val templates: List<EmotionTemplate> = DEFAULT_EMOTION_TEMPLATES,
) : ExpressionMapper {

    private val templateMap: Map<Emotion, Map<BlendShape, Float>> =
        templates.associate { it.emotion to it.weights }

    override fun map(emotion: Emotion, intensity: Float): Map<BlendShape, Float> {
        val weights = templateMap[emotion] ?: return emptyMap()
        val clampedIntensity = intensity.coerceIn(0f, 1f)
        return weights.mapValues { (_, weight) ->
            (weight * clampedIntensity).coerceIn(0f, 1f)
        }
    }
}
