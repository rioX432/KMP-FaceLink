package io.github.kmpfacelink.actions.emotion

import io.github.kmpfacelink.model.BlendShape

// FACS-derived blend shape activation weights for each emotion template
private const val WEIGHT_HIGH = 0.7f
private const val WEIGHT_RAISED = 0.6f
private const val WEIGHT_MED = 0.5f
private const val WEIGHT_LOW = 0.4f
private const val WEIGHT_SLIGHT = 0.3f
private const val WEIGHT_MINIMAL = 0.2f

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
public val DEFAULT_EMOTION_TEMPLATES: List<EmotionTemplate> = listOf(
    EmotionTemplate(
        emotion = Emotion.HAPPY,
        weights = mapOf(
            BlendShape.MOUTH_SMILE_LEFT to WEIGHT_HIGH,
            BlendShape.MOUTH_SMILE_RIGHT to WEIGHT_HIGH,
            BlendShape.CHEEK_SQUINT_LEFT to WEIGHT_LOW,
            BlendShape.CHEEK_SQUINT_RIGHT to WEIGHT_LOW,
            BlendShape.MOUTH_DIMPLE_LEFT to WEIGHT_SLIGHT,
            BlendShape.MOUTH_DIMPLE_RIGHT to WEIGHT_SLIGHT,
        ),
    ),
    EmotionTemplate(
        emotion = Emotion.SAD,
        weights = mapOf(
            BlendShape.MOUTH_FROWN_LEFT to WEIGHT_MED,
            BlendShape.MOUTH_FROWN_RIGHT to WEIGHT_MED,
            BlendShape.BROW_INNER_UP to WEIGHT_MED,
            BlendShape.MOUTH_PRESS_LEFT to WEIGHT_SLIGHT,
            BlendShape.MOUTH_PRESS_RIGHT to WEIGHT_SLIGHT,
            BlendShape.MOUTH_LOWER_DOWN_LEFT to WEIGHT_MINIMAL,
            BlendShape.MOUTH_LOWER_DOWN_RIGHT to WEIGHT_MINIMAL,
        ),
    ),
    EmotionTemplate(
        emotion = Emotion.ANGRY,
        weights = mapOf(
            BlendShape.BROW_DOWN_LEFT to WEIGHT_HIGH,
            BlendShape.BROW_DOWN_RIGHT to WEIGHT_HIGH,
            BlendShape.NOSE_SNEER_LEFT to WEIGHT_LOW,
            BlendShape.NOSE_SNEER_RIGHT to WEIGHT_LOW,
            BlendShape.MOUTH_PRESS_LEFT to WEIGHT_LOW,
            BlendShape.MOUTH_PRESS_RIGHT to WEIGHT_LOW,
            BlendShape.JAW_FORWARD to WEIGHT_SLIGHT,
        ),
    ),
    EmotionTemplate(
        emotion = Emotion.SURPRISED,
        weights = mapOf(
            BlendShape.EYE_WIDE_LEFT to WEIGHT_HIGH,
            BlendShape.EYE_WIDE_RIGHT to WEIGHT_HIGH,
            BlendShape.BROW_INNER_UP to WEIGHT_RAISED,
            BlendShape.BROW_OUTER_UP_LEFT to WEIGHT_MED,
            BlendShape.BROW_OUTER_UP_RIGHT to WEIGHT_MED,
            BlendShape.JAW_OPEN to WEIGHT_MED,
        ),
    ),
    EmotionTemplate(
        emotion = Emotion.DISGUSTED,
        weights = mapOf(
            BlendShape.NOSE_SNEER_LEFT to WEIGHT_HIGH,
            BlendShape.NOSE_SNEER_RIGHT to WEIGHT_HIGH,
            BlendShape.MOUTH_UPPER_UP_LEFT to WEIGHT_MED,
            BlendShape.MOUTH_UPPER_UP_RIGHT to WEIGHT_MED,
            BlendShape.MOUTH_FROWN_LEFT to WEIGHT_SLIGHT,
            BlendShape.MOUTH_FROWN_RIGHT to WEIGHT_SLIGHT,
            BlendShape.BROW_DOWN_LEFT to WEIGHT_SLIGHT,
            BlendShape.BROW_DOWN_RIGHT to WEIGHT_SLIGHT,
        ),
    ),
    EmotionTemplate(
        emotion = Emotion.FEAR,
        weights = mapOf(
            BlendShape.EYE_WIDE_LEFT to WEIGHT_HIGH,
            BlendShape.EYE_WIDE_RIGHT to WEIGHT_HIGH,
            BlendShape.BROW_INNER_UP to WEIGHT_RAISED,
            BlendShape.BROW_DOWN_LEFT to WEIGHT_SLIGHT,
            BlendShape.BROW_DOWN_RIGHT to WEIGHT_SLIGHT,
            BlendShape.MOUTH_STRETCH_LEFT to WEIGHT_LOW,
            BlendShape.MOUTH_STRETCH_RIGHT to WEIGHT_LOW,
        ),
    ),
)
