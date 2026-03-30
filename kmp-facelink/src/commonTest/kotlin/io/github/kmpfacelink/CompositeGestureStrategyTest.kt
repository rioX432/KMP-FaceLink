package io.github.kmpfacelink

import io.github.kmpfacelink.model.GestureClassificationResult
import io.github.kmpfacelink.model.HandGesture
import io.github.kmpfacelink.model.HandJoint
import io.github.kmpfacelink.model.HandLandmarkPoint
import io.github.kmpfacelink.util.CompositeGestureStrategy
import io.github.kmpfacelink.util.RuleBasedGestureStrategy
import io.github.kmpfacelink.util.TemplateMatchingGestureStrategy
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class CompositeGestureStrategyTest {

    private fun createAllExtendedLandmarks(): List<HandLandmarkPoint> {
        val wrist = HandLandmarkPoint(HandJoint.WRIST, 0.5f, 0.8f, 0f)
        val thumbCmc = HandLandmarkPoint(HandJoint.THUMB_CMC, 0.35f, 0.7f, 0f)
        val thumbMcp = HandLandmarkPoint(HandJoint.THUMB_MCP, 0.3f, 0.6f, 0f)
        val thumbIp = HandLandmarkPoint(HandJoint.THUMB_IP, 0.25f, 0.5f, 0f)
        val thumbTip = HandLandmarkPoint(HandJoint.THUMB_TIP, 0.15f, 0.3f, 0f)
        val indexMcp = HandLandmarkPoint(HandJoint.INDEX_FINGER_MCP, 0.4f, 0.6f, 0f)
        val indexPip = HandLandmarkPoint(HandJoint.INDEX_FINGER_PIP, 0.4f, 0.45f, 0f)
        val indexDip = HandLandmarkPoint(HandJoint.INDEX_FINGER_DIP, 0.4f, 0.35f, 0f)
        val indexTip = HandLandmarkPoint(HandJoint.INDEX_FINGER_TIP, 0.4f, 0.2f, 0f)
        val middleMcp = HandLandmarkPoint(HandJoint.MIDDLE_FINGER_MCP, 0.5f, 0.58f, 0f)
        val middlePip = HandLandmarkPoint(HandJoint.MIDDLE_FINGER_PIP, 0.5f, 0.43f, 0f)
        val middleDip = HandLandmarkPoint(HandJoint.MIDDLE_FINGER_DIP, 0.5f, 0.33f, 0f)
        val middleTip = HandLandmarkPoint(HandJoint.MIDDLE_FINGER_TIP, 0.5f, 0.18f, 0f)
        val ringMcp = HandLandmarkPoint(HandJoint.RING_FINGER_MCP, 0.6f, 0.6f, 0f)
        val ringPip = HandLandmarkPoint(HandJoint.RING_FINGER_PIP, 0.6f, 0.45f, 0f)
        val ringDip = HandLandmarkPoint(HandJoint.RING_FINGER_DIP, 0.6f, 0.35f, 0f)
        val ringTip = HandLandmarkPoint(HandJoint.RING_FINGER_TIP, 0.6f, 0.2f, 0f)
        val pinkyMcp = HandLandmarkPoint(HandJoint.PINKY_MCP, 0.7f, 0.62f, 0f)
        val pinkyPip = HandLandmarkPoint(HandJoint.PINKY_PIP, 0.7f, 0.48f, 0f)
        val pinkyDip = HandLandmarkPoint(HandJoint.PINKY_DIP, 0.7f, 0.38f, 0f)
        val pinkyTip = HandLandmarkPoint(HandJoint.PINKY_TIP, 0.7f, 0.23f, 0f)

        return listOf(
            wrist, thumbCmc, thumbMcp, thumbIp, thumbTip,
            indexMcp, indexPip, indexDip, indexTip,
            middleMcp, middlePip, middleDip, middleTip,
            ringMcp, ringPip, ringDip, ringTip,
            pinkyMcp, pinkyPip, pinkyDip, pinkyTip,
        )
    }

    @Test
    fun compositeTriesStrategiesInOrder() {
        val template = TemplateMatchingGestureStrategy(confidenceThreshold = 0.7f)
        val ruleBased = RuleBasedGestureStrategy()
        val composite = CompositeGestureStrategy(listOf(template, ruleBased))

        // No templates registered, so template strategy returns NONE.
        // Rule-based should classify OPEN_PALM.
        val landmarks = createAllExtendedLandmarks()
        val result = composite.classify(landmarks)

        assertEquals(HandGesture.OPEN_PALM.name, result.gestureName)
        assertEquals(HandGesture.OPEN_PALM, result.builtInGesture)
        assertTrue(result.confidence > 0f)
    }

    @Test
    fun compositeReturnsTemplateMatchWhenAvailable() {
        val template = TemplateMatchingGestureStrategy(confidenceThreshold = 0.7f)
        val ruleBased = RuleBasedGestureStrategy()
        val composite = CompositeGestureStrategy(listOf(template, ruleBased))

        val landmarks = createAllExtendedLandmarks()
        template.registerGesture("wave", listOf(landmarks))

        val result = composite.classify(landmarks)
        // Template match should win because it's first and has confidence
        assertEquals("wave", result.gestureName)
    }

    @Test
    fun ruleBasedStrategyClassifiesBuiltInGestures() {
        val strategy = RuleBasedGestureStrategy()
        val landmarks = createAllExtendedLandmarks()
        val result = strategy.classify(landmarks)

        assertEquals(HandGesture.OPEN_PALM.name, result.gestureName)
        assertEquals(HandGesture.OPEN_PALM, result.builtInGesture)
        assertTrue(result.confidence > 0.5f)
    }

    @Test
    fun ruleBasedStrategyReturnsNoneForInsufficientLandmarks() {
        val strategy = RuleBasedGestureStrategy()
        val result = strategy.classify(listOf(HandLandmarkPoint(HandJoint.WRIST, 0.5f, 0.5f)))
        assertEquals(GestureClassificationResult.NONE, result)
    }

    @Test
    fun compositeRequiresAtLeastOneStrategy() {
        assertFailsWith<IllegalArgumentException> {
            CompositeGestureStrategy(emptyList())
        }
    }
}
