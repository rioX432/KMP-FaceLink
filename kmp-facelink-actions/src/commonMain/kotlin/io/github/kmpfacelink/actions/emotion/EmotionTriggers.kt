package io.github.kmpfacelink.actions.emotion

import io.github.kmpfacelink.actions.ActionTrigger
import io.github.kmpfacelink.actions.ThresholdDirection
import io.github.kmpfacelink.model.BlendShape

private const val DEFAULT_HAPPY_THRESHOLD = 0.5f
private const val DEFAULT_SAD_FROWN_THRESHOLD = 0.4f
private const val DEFAULT_SAD_BROW_THRESHOLD = 0.3f
private const val DEFAULT_ANGRY_BROW_THRESHOLD = 0.5f
private const val DEFAULT_ANGRY_SNEER_THRESHOLD = 0.3f
private const val DEFAULT_SURPRISED_EYE_THRESHOLD = 0.5f
private const val DEFAULT_SURPRISED_BROW_THRESHOLD = 0.4f
private const val DEFAULT_SURPRISED_JAW_THRESHOLD = 0.3f
private const val DEFAULT_DISGUSTED_SNEER_THRESHOLD = 0.5f
private const val DEFAULT_DISGUSTED_LIP_THRESHOLD = 0.3f
private const val DEFAULT_FEAR_EYE_THRESHOLD = 0.5f
private const val DEFAULT_FEAR_BROW_UP_THRESHOLD = 0.4f
private const val DEFAULT_FEAR_BROW_DOWN_THRESHOLD = 0.2f
private const val DEFAULT_FEAR_MOUTH_THRESHOLD = 0.3f

/**
 * Triggers when user is happy (both mouth corners smile + cheek squint).
 *
 * Use with [ActionSystem.register] to detect happy expressions:
 * ```
 * actionSystem.register(
 *     ActionBinding("happy", happyTrigger(), cooldownMs = 2000)
 * )
 * ```
 */
public fun happyTrigger(
    threshold: Float = DEFAULT_HAPPY_THRESHOLD,
): ActionTrigger = ActionTrigger.CombinedTrigger(
    listOf(
        ActionTrigger.ExpressionTrigger(BlendShape.MOUTH_SMILE_LEFT, threshold),
        ActionTrigger.ExpressionTrigger(BlendShape.MOUTH_SMILE_RIGHT, threshold),
    ),
)

/**
 * Triggers when user looks sad (frown + inner brow raised).
 */
public fun sadTrigger(
    frownThreshold: Float = DEFAULT_SAD_FROWN_THRESHOLD,
    browThreshold: Float = DEFAULT_SAD_BROW_THRESHOLD,
): ActionTrigger = ActionTrigger.CombinedTrigger(
    listOf(
        ActionTrigger.ExpressionTrigger(BlendShape.MOUTH_FROWN_LEFT, frownThreshold),
        ActionTrigger.ExpressionTrigger(BlendShape.MOUTH_FROWN_RIGHT, frownThreshold),
        ActionTrigger.ExpressionTrigger(BlendShape.BROW_INNER_UP, browThreshold),
    ),
)

/**
 * Triggers when user looks angry (brows down + nose sneer).
 */
public fun angryTrigger(
    browThreshold: Float = DEFAULT_ANGRY_BROW_THRESHOLD,
    sneerThreshold: Float = DEFAULT_ANGRY_SNEER_THRESHOLD,
): ActionTrigger = ActionTrigger.CombinedTrigger(
    listOf(
        ActionTrigger.ExpressionTrigger(BlendShape.BROW_DOWN_LEFT, browThreshold),
        ActionTrigger.ExpressionTrigger(BlendShape.BROW_DOWN_RIGHT, browThreshold),
        ActionTrigger.ExpressionTrigger(BlendShape.NOSE_SNEER_LEFT, sneerThreshold),
    ),
)

/**
 * Triggers when user looks surprised (eyes wide + brows up + jaw open).
 */
public fun surprisedEmotionTrigger(
    eyeThreshold: Float = DEFAULT_SURPRISED_EYE_THRESHOLD,
    browThreshold: Float = DEFAULT_SURPRISED_BROW_THRESHOLD,
    jawThreshold: Float = DEFAULT_SURPRISED_JAW_THRESHOLD,
): ActionTrigger = ActionTrigger.CombinedTrigger(
    listOf(
        ActionTrigger.ExpressionTrigger(BlendShape.EYE_WIDE_LEFT, eyeThreshold),
        ActionTrigger.ExpressionTrigger(BlendShape.EYE_WIDE_RIGHT, eyeThreshold),
        ActionTrigger.ExpressionTrigger(BlendShape.BROW_INNER_UP, browThreshold),
        ActionTrigger.ExpressionTrigger(BlendShape.JAW_OPEN, jawThreshold),
    ),
)

/**
 * Triggers when user looks disgusted (nose sneer + upper lip raise).
 */
public fun disgustedTrigger(
    sneerThreshold: Float = DEFAULT_DISGUSTED_SNEER_THRESHOLD,
    lipThreshold: Float = DEFAULT_DISGUSTED_LIP_THRESHOLD,
): ActionTrigger = ActionTrigger.CombinedTrigger(
    listOf(
        ActionTrigger.ExpressionTrigger(BlendShape.NOSE_SNEER_LEFT, sneerThreshold),
        ActionTrigger.ExpressionTrigger(BlendShape.NOSE_SNEER_RIGHT, sneerThreshold),
        ActionTrigger.ExpressionTrigger(BlendShape.MOUTH_UPPER_UP_LEFT, lipThreshold),
    ),
)

/**
 * Triggers when user looks fearful (eyes wide + brows up and drawn together + mouth stretch).
 */
public fun fearTrigger(
    eyeThreshold: Float = DEFAULT_FEAR_EYE_THRESHOLD,
    browUpThreshold: Float = DEFAULT_FEAR_BROW_UP_THRESHOLD,
    browDownThreshold: Float = DEFAULT_FEAR_BROW_DOWN_THRESHOLD,
    mouthThreshold: Float = DEFAULT_FEAR_MOUTH_THRESHOLD,
): ActionTrigger = ActionTrigger.CombinedTrigger(
    listOf(
        ActionTrigger.ExpressionTrigger(BlendShape.EYE_WIDE_LEFT, eyeThreshold),
        ActionTrigger.ExpressionTrigger(BlendShape.EYE_WIDE_RIGHT, eyeThreshold),
        ActionTrigger.ExpressionTrigger(BlendShape.BROW_INNER_UP, browUpThreshold),
        ActionTrigger.ExpressionTrigger(
            BlendShape.BROW_DOWN_LEFT,
            browDownThreshold,
            ThresholdDirection.ABOVE,
        ),
        ActionTrigger.ExpressionTrigger(BlendShape.MOUTH_STRETCH_LEFT, mouthThreshold),
    ),
)
