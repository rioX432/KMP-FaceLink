package io.github.kmpfacelink.actions

import io.github.kmpfacelink.model.FaceTrackingData
import io.github.kmpfacelink.model.HandTrackingData

/**
 * Stateless evaluator that checks whether a trigger condition is met
 * given the latest face and hand tracking snapshots.
 */
internal object TriggerEvaluator {

    fun evaluate(
        trigger: ActionTrigger,
        faceData: FaceTrackingData?,
        handData: HandTrackingData?,
    ): Boolean = when (trigger) {
        is ActionTrigger.GestureTrigger -> evaluateGesture(trigger, handData)
        is ActionTrigger.ExpressionTrigger -> evaluateExpression(trigger, faceData)
        is ActionTrigger.CombinedTrigger -> evaluateCombined(trigger, faceData, handData)
        is ActionTrigger.StreamEventTrigger -> false // Event-driven, not frame-driven
    }

    private fun evaluateGesture(
        trigger: ActionTrigger.GestureTrigger,
        handData: HandTrackingData?,
    ): Boolean {
        if (handData == null || !handData.isTracking) return false
        return handData.hands.any { hand ->
            hand.gesture == trigger.gesture &&
                hand.gestureConfidence >= trigger.minConfidence &&
                (trigger.hand == null || hand.handedness == trigger.hand)
        }
    }

    private fun evaluateExpression(
        trigger: ActionTrigger.ExpressionTrigger,
        faceData: FaceTrackingData?,
    ): Boolean {
        if (faceData == null || !faceData.isTracking) return false
        // A shape the estimator did not produce cannot satisfy a threshold. Reading it as zero
        // would leave every BELOW trigger permanently fired on estimators that never emit it.
        val value = faceData.blendShapes[trigger.blendShape] ?: return false
        return when (trigger.direction) {
            ThresholdDirection.ABOVE -> value >= trigger.threshold
            ThresholdDirection.BELOW -> value <= trigger.threshold
        }
    }

    private fun evaluateCombined(
        trigger: ActionTrigger.CombinedTrigger,
        faceData: FaceTrackingData?,
        handData: HandTrackingData?,
    ): Boolean = trigger.triggers.all { evaluate(it, faceData, handData) }
}
