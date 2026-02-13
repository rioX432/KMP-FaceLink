package io.github.kmpfacelink.actions

import io.github.kmpfacelink.model.BlendShape
import io.github.kmpfacelink.model.FaceTrackingData
import io.github.kmpfacelink.model.HandGesture
import io.github.kmpfacelink.model.HandTrackingData
import io.github.kmpfacelink.model.Handedness
import io.github.kmpfacelink.model.TrackedHand
import io.github.kmpfacelink.model.emptyBlendShapeData
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TriggerEvaluatorTest {

    // --- GestureTrigger ---

    @Test
    fun gestureTriggerMatchesWhenGestureAndConfidenceMet() {
        val trigger = ActionTrigger.GestureTrigger(HandGesture.THUMB_UP, minConfidence = 0.5f)
        val data = handData(HandGesture.THUMB_UP, confidence = 0.8f)
        assertTrue(TriggerEvaluator.evaluate(trigger, null, data))
    }

    @Test
    fun gestureTriggerFailsWhenConfidenceTooLow() {
        val trigger = ActionTrigger.GestureTrigger(HandGesture.THUMB_UP, minConfidence = 0.9f)
        val data = handData(HandGesture.THUMB_UP, confidence = 0.5f)
        assertFalse(TriggerEvaluator.evaluate(trigger, null, data))
    }

    @Test
    fun gestureTriggerFailsWhenWrongGesture() {
        val trigger = ActionTrigger.GestureTrigger(HandGesture.VICTORY)
        val data = handData(HandGesture.THUMB_UP, confidence = 0.9f)
        assertFalse(TriggerEvaluator.evaluate(trigger, null, data))
    }

    @Test
    fun gestureTriggerFiltersHandedness() {
        val trigger = ActionTrigger.GestureTrigger(
            HandGesture.THUMB_UP,
            hand = Handedness.LEFT,
        )
        val rightHand = handData(HandGesture.THUMB_UP, confidence = 0.9f, handedness = Handedness.RIGHT)
        assertFalse(TriggerEvaluator.evaluate(trigger, null, rightHand))

        val leftHand = handData(HandGesture.THUMB_UP, confidence = 0.9f, handedness = Handedness.LEFT)
        assertTrue(TriggerEvaluator.evaluate(trigger, null, leftHand))
    }

    @Test
    fun gestureTriggerReturnsFalseWhenNotTracking() {
        val trigger = ActionTrigger.GestureTrigger(HandGesture.THUMB_UP)
        val data = HandTrackingData.notTracking()
        assertFalse(TriggerEvaluator.evaluate(trigger, null, data))
    }

    @Test
    fun gestureTriggerReturnsFalseWhenNoHandData() {
        val trigger = ActionTrigger.GestureTrigger(HandGesture.THUMB_UP)
        assertFalse(TriggerEvaluator.evaluate(trigger, null, null))
    }

    // --- ExpressionTrigger ---

    @Test
    fun expressionTriggerAboveThreshold() {
        val trigger = ActionTrigger.ExpressionTrigger(BlendShape.MOUTH_SMILE_LEFT, threshold = 0.5f)
        val data = faceData(BlendShape.MOUTH_SMILE_LEFT to 0.7f)
        assertTrue(TriggerEvaluator.evaluate(trigger, data, null))
    }

    @Test
    fun expressionTriggerBelowThresholdFails() {
        val trigger = ActionTrigger.ExpressionTrigger(BlendShape.MOUTH_SMILE_LEFT, threshold = 0.5f)
        val data = faceData(BlendShape.MOUTH_SMILE_LEFT to 0.3f)
        assertFalse(TriggerEvaluator.evaluate(trigger, data, null))
    }

    @Test
    fun expressionTriggerBelowDirection() {
        val trigger = ActionTrigger.ExpressionTrigger(
            BlendShape.EYE_BLINK_LEFT,
            threshold = 0.3f,
            direction = ThresholdDirection.BELOW,
        )
        val data = faceData(BlendShape.EYE_BLINK_LEFT to 0.1f)
        assertTrue(TriggerEvaluator.evaluate(trigger, data, null))
    }

    @Test
    fun expressionTriggerReturnsFalseWhenNotTracking() {
        val trigger = ActionTrigger.ExpressionTrigger(BlendShape.TONGUE_OUT)
        val data = FaceTrackingData.notTracking()
        assertFalse(TriggerEvaluator.evaluate(trigger, data, null))
    }

    @Test
    fun expressionTriggerReturnsFalseWhenNoFaceData() {
        val trigger = ActionTrigger.ExpressionTrigger(BlendShape.TONGUE_OUT)
        assertFalse(TriggerEvaluator.evaluate(trigger, null, null))
    }

    @Test
    fun expressionTriggerExactThresholdAbove() {
        val trigger = ActionTrigger.ExpressionTrigger(BlendShape.JAW_OPEN, threshold = 0.5f)
        val data = faceData(BlendShape.JAW_OPEN to 0.5f)
        assertTrue(TriggerEvaluator.evaluate(trigger, data, null))
    }

    @Test
    fun expressionTriggerExactThresholdBelow() {
        val trigger = ActionTrigger.ExpressionTrigger(
            BlendShape.JAW_OPEN,
            threshold = 0.5f,
            direction = ThresholdDirection.BELOW,
        )
        val data = faceData(BlendShape.JAW_OPEN to 0.5f)
        assertTrue(TriggerEvaluator.evaluate(trigger, data, null))
    }

    // --- CombinedTrigger ---

    @Test
    fun combinedTriggerRequiresAllTrue() {
        val trigger = ActionTrigger.CombinedTrigger(
            listOf(
                ActionTrigger.ExpressionTrigger(BlendShape.MOUTH_SMILE_LEFT, threshold = 0.5f),
                ActionTrigger.GestureTrigger(HandGesture.VICTORY),
            ),
        )
        val faceData = faceData(BlendShape.MOUTH_SMILE_LEFT to 0.8f)
        val handData = handData(HandGesture.VICTORY, confidence = 0.9f)
        assertTrue(TriggerEvaluator.evaluate(trigger, faceData, handData))
    }

    @Test
    fun combinedTriggerFailsWhenOneIsFalse() {
        val trigger = ActionTrigger.CombinedTrigger(
            listOf(
                ActionTrigger.ExpressionTrigger(BlendShape.MOUTH_SMILE_LEFT, threshold = 0.5f),
                ActionTrigger.GestureTrigger(HandGesture.VICTORY),
            ),
        )
        val faceData = faceData(BlendShape.MOUTH_SMILE_LEFT to 0.8f)
        // No hand data → gesture fails
        assertFalse(TriggerEvaluator.evaluate(trigger, faceData, null))
    }

    // --- Helpers ---

    private fun faceData(vararg blendShapes: Pair<BlendShape, Float>): FaceTrackingData {
        val data = emptyBlendShapeData().toMutableMap()
        blendShapes.forEach { (shape, value) -> data[shape] = value }
        return FaceTrackingData(
            blendShapes = data,
            headTransform = io.github.kmpfacelink.model.HeadTransform(),
            timestampMs = 0L,
            isTracking = true,
        )
    }

    private fun handData(
        gesture: HandGesture,
        confidence: Float = 0.9f,
        handedness: Handedness = Handedness.RIGHT,
    ): HandTrackingData = HandTrackingData(
        hands = listOf(
            TrackedHand(
                handedness = handedness,
                landmarks = emptyList(),
                gesture = gesture,
                gestureConfidence = confidence,
            ),
        ),
        timestampMs = 0L,
        isTracking = true,
    )
}
