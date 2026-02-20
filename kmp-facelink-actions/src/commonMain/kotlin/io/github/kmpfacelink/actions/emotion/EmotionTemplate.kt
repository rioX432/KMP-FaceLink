package io.github.kmpfacelink.actions.emotion

import io.github.kmpfacelink.model.BlendShape

/**
 * Defines the expected blend shape activation pattern for an emotion.
 *
 * Each weight entry maps a [BlendShape] to its expected activation level (0.0–1.0).
 * Only blend shapes relevant to the emotion need to be specified;
 * unspecified shapes are treated as 0.0 during similarity scoring.
 */
public data class EmotionTemplate(
    public val emotion: Emotion,
    public val weights: Map<BlendShape, Float>,
)

/**
 * Built-in emotion templates based on FACS (Facial Action Coding System) mappings
 * to ARKit blend shapes.
 *
 * References:
 * - DZNE research: cosine similarity classification from ARKit blend shapes (68.3% accuracy)
 * - Ekman & Friesen FACS Action Unit definitions
 */
@Suppress("MagicNumber")
public val DEFAULT_EMOTION_TEMPLATES: List<EmotionTemplate> = listOf(
    EmotionTemplate(
        emotion = Emotion.HAPPY,
        weights = mapOf(
            BlendShape.MOUTH_SMILE_LEFT to 0.7f,
            BlendShape.MOUTH_SMILE_RIGHT to 0.7f,
            BlendShape.CHEEK_SQUINT_LEFT to 0.4f,
            BlendShape.CHEEK_SQUINT_RIGHT to 0.4f,
            BlendShape.MOUTH_DIMPLE_LEFT to 0.3f,
            BlendShape.MOUTH_DIMPLE_RIGHT to 0.3f,
        ),
    ),
    EmotionTemplate(
        emotion = Emotion.SAD,
        weights = mapOf(
            BlendShape.MOUTH_FROWN_LEFT to 0.5f,
            BlendShape.MOUTH_FROWN_RIGHT to 0.5f,
            BlendShape.BROW_INNER_UP to 0.5f,
            BlendShape.MOUTH_PRESS_LEFT to 0.3f,
            BlendShape.MOUTH_PRESS_RIGHT to 0.3f,
            BlendShape.MOUTH_LOWER_DOWN_LEFT to 0.2f,
            BlendShape.MOUTH_LOWER_DOWN_RIGHT to 0.2f,
        ),
    ),
    EmotionTemplate(
        emotion = Emotion.ANGRY,
        weights = mapOf(
            BlendShape.BROW_DOWN_LEFT to 0.7f,
            BlendShape.BROW_DOWN_RIGHT to 0.7f,
            BlendShape.NOSE_SNEER_LEFT to 0.4f,
            BlendShape.NOSE_SNEER_RIGHT to 0.4f,
            BlendShape.MOUTH_PRESS_LEFT to 0.4f,
            BlendShape.MOUTH_PRESS_RIGHT to 0.4f,
            BlendShape.JAW_FORWARD to 0.3f,
        ),
    ),
    EmotionTemplate(
        emotion = Emotion.SURPRISED,
        weights = mapOf(
            BlendShape.EYE_WIDE_LEFT to 0.7f,
            BlendShape.EYE_WIDE_RIGHT to 0.7f,
            BlendShape.BROW_INNER_UP to 0.6f,
            BlendShape.BROW_OUTER_UP_LEFT to 0.5f,
            BlendShape.BROW_OUTER_UP_RIGHT to 0.5f,
            BlendShape.JAW_OPEN to 0.5f,
        ),
    ),
    EmotionTemplate(
        emotion = Emotion.DISGUSTED,
        weights = mapOf(
            BlendShape.NOSE_SNEER_LEFT to 0.7f,
            BlendShape.NOSE_SNEER_RIGHT to 0.7f,
            BlendShape.MOUTH_UPPER_UP_LEFT to 0.5f,
            BlendShape.MOUTH_UPPER_UP_RIGHT to 0.5f,
            BlendShape.MOUTH_FROWN_LEFT to 0.3f,
            BlendShape.MOUTH_FROWN_RIGHT to 0.3f,
            BlendShape.BROW_DOWN_LEFT to 0.3f,
            BlendShape.BROW_DOWN_RIGHT to 0.3f,
        ),
    ),
    EmotionTemplate(
        emotion = Emotion.FEAR,
        weights = mapOf(
            BlendShape.EYE_WIDE_LEFT to 0.7f,
            BlendShape.EYE_WIDE_RIGHT to 0.7f,
            BlendShape.BROW_INNER_UP to 0.6f,
            BlendShape.BROW_DOWN_LEFT to 0.3f,
            BlendShape.BROW_DOWN_RIGHT to 0.3f,
            BlendShape.MOUTH_STRETCH_LEFT to 0.4f,
            BlendShape.MOUTH_STRETCH_RIGHT to 0.4f,
        ),
    ),
)
