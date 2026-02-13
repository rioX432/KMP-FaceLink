package io.github.kmpfacelink.actions

import io.github.kmpfacelink.model.BlendShape
import io.github.kmpfacelink.model.HandGesture

private const val DEFAULT_EXPRESSION_THRESHOLD = 0.6f
private const val DEFAULT_GESTURE_CONFIDENCE = 0.5f
private const val WINK_OPEN_EYE_THRESHOLD = 0.3f

/** Left wink: left eye closed AND right eye open. */
public fun winkLeftTrigger(
    blinkThreshold: Float = DEFAULT_EXPRESSION_THRESHOLD,
): ActionTrigger = ActionTrigger.CombinedTrigger(
    listOf(
        ActionTrigger.ExpressionTrigger(BlendShape.EYE_BLINK_LEFT, blinkThreshold),
        ActionTrigger.ExpressionTrigger(
            BlendShape.EYE_BLINK_RIGHT,
            WINK_OPEN_EYE_THRESHOLD,
            ThresholdDirection.BELOW,
        ),
    ),
)

/** Right wink: right eye closed AND left eye open. */
public fun winkRightTrigger(
    blinkThreshold: Float = DEFAULT_EXPRESSION_THRESHOLD,
): ActionTrigger = ActionTrigger.CombinedTrigger(
    listOf(
        ActionTrigger.ExpressionTrigger(BlendShape.EYE_BLINK_RIGHT, blinkThreshold),
        ActionTrigger.ExpressionTrigger(
            BlendShape.EYE_BLINK_LEFT,
            WINK_OPEN_EYE_THRESHOLD,
            ThresholdDirection.BELOW,
        ),
    ),
)

/** Smile: both mouth corners raised. */
public fun smileTrigger(
    threshold: Float = DEFAULT_EXPRESSION_THRESHOLD,
): ActionTrigger = ActionTrigger.CombinedTrigger(
    listOf(
        ActionTrigger.ExpressionTrigger(BlendShape.MOUTH_SMILE_LEFT, threshold),
        ActionTrigger.ExpressionTrigger(BlendShape.MOUTH_SMILE_RIGHT, threshold),
    ),
)

/** Tongue sticking out. */
public fun tongueOutTrigger(
    threshold: Float = DEFAULT_EXPRESSION_THRESHOLD,
): ActionTrigger = ActionTrigger.ExpressionTrigger(BlendShape.TONGUE_OUT, threshold)

/** Surprised expression: both brows raised and eyes wide. */
public fun surprisedTrigger(
    threshold: Float = DEFAULT_EXPRESSION_THRESHOLD,
): ActionTrigger = ActionTrigger.CombinedTrigger(
    listOf(
        ActionTrigger.ExpressionTrigger(BlendShape.BROW_INNER_UP, threshold),
        ActionTrigger.ExpressionTrigger(BlendShape.EYE_WIDE_LEFT, threshold),
        ActionTrigger.ExpressionTrigger(BlendShape.EYE_WIDE_RIGHT, threshold),
    ),
)

/** Thumbs up gesture. */
public fun thumbsUpTrigger(
    minConfidence: Float = DEFAULT_GESTURE_CONFIDENCE,
): ActionTrigger = ActionTrigger.GestureTrigger(HandGesture.THUMB_UP, minConfidence = minConfidence)

/** Victory / peace sign gesture. */
public fun victoryTrigger(
    minConfidence: Float = DEFAULT_GESTURE_CONFIDENCE,
): ActionTrigger = ActionTrigger.GestureTrigger(HandGesture.VICTORY, minConfidence = minConfidence)

/** Open palm gesture. */
public fun openPalmTrigger(
    minConfidence: Float = DEFAULT_GESTURE_CONFIDENCE,
): ActionTrigger = ActionTrigger.GestureTrigger(HandGesture.OPEN_PALM, minConfidence = minConfidence)

/** Closed fist gesture. */
public fun closedFistTrigger(
    minConfidence: Float = DEFAULT_GESTURE_CONFIDENCE,
): ActionTrigger = ActionTrigger.GestureTrigger(HandGesture.CLOSED_FIST, minConfidence = minConfidence)
