package io.github.kmpfacelink.pipeline.expression

import io.github.kmpfacelink.actions.emotion.Emotion
import io.github.kmpfacelink.model.BlendShape

/**
 * Maps an emotion and intensity to blend shape weights for avatar expressions.
 */
public fun interface ExpressionMapper {
    /**
     * Produces blend shape weights for the given emotion at the specified intensity.
     *
     * @param emotion The emotion to express.
     * @param intensity Expression strength (0.0–1.0).
     * @return Blend shape weights, each clamped to 0.0–1.0.
     */
    public fun map(emotion: Emotion, intensity: Float): Map<BlendShape, Float>
}
