package io.github.kmpfacelink

import io.github.kmpfacelink.model.HandGesture
import io.github.kmpfacelink.model.HandJoint
import io.github.kmpfacelink.model.HandLandmarkPoint
import io.github.kmpfacelink.util.GestureClassifier
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class GestureClassifierTest {

    /**
     * Create synthetic hand landmarks with specified finger positions.
     * Wrist at (0.5, 0.8), MCPs at (0.x, 0.6), tips either extended (y=0.2) or curled (y=0.7).
     */
    @Suppress("LongParameterList", "CyclomaticComplexMethod", "LongMethod")
    private fun createLandmarks(
        thumbExtended: Boolean = false,
        indexExtended: Boolean = false,
        middleExtended: Boolean = false,
        ringExtended: Boolean = false,
        pinkyExtended: Boolean = false,
        thumbTipY: Float? = null,
    ): List<HandLandmarkPoint> {
        val wrist = HandLandmarkPoint(HandJoint.WRIST, 0.5f, 0.8f, 0f)

        // Thumb: CMC near wrist, tip far from indexMCP if extended
        val thumbCmc = HandLandmarkPoint(HandJoint.THUMB_CMC, 0.35f, 0.7f, 0f)
        val thumbMcp = HandLandmarkPoint(HandJoint.THUMB_MCP, 0.3f, 0.6f, 0f)
        val thumbIp = HandLandmarkPoint(HandJoint.THUMB_IP, 0.25f, 0.5f, 0f)
        val actualThumbTipY = thumbTipY ?: if (thumbExtended) 0.3f else 0.65f
        val thumbTip = if (thumbExtended) {
            HandLandmarkPoint(HandJoint.THUMB_TIP, 0.15f, actualThumbTipY, 0f)
        } else {
            HandLandmarkPoint(HandJoint.THUMB_TIP, 0.38f, actualThumbTipY, 0f)
        }

        // Index finger
        val indexMcp = HandLandmarkPoint(HandJoint.INDEX_FINGER_MCP, 0.4f, 0.6f, 0f)
        val indexPip = HandLandmarkPoint(HandJoint.INDEX_FINGER_PIP, 0.4f, if (indexExtended) 0.45f else 0.62f, 0f)
        val indexDip = HandLandmarkPoint(HandJoint.INDEX_FINGER_DIP, 0.4f, if (indexExtended) 0.35f else 0.65f, 0f)
        val indexTip = HandLandmarkPoint(HandJoint.INDEX_FINGER_TIP, 0.4f, if (indexExtended) 0.2f else 0.68f, 0f)

        // Middle finger
        val middleMcp = HandLandmarkPoint(HandJoint.MIDDLE_FINGER_MCP, 0.5f, 0.58f, 0f)
        val middlePip = HandLandmarkPoint(HandJoint.MIDDLE_FINGER_PIP, 0.5f, if (middleExtended) 0.43f else 0.6f, 0f)
        val middleDip = HandLandmarkPoint(HandJoint.MIDDLE_FINGER_DIP, 0.5f, if (middleExtended) 0.33f else 0.63f, 0f)
        val middleTip = HandLandmarkPoint(HandJoint.MIDDLE_FINGER_TIP, 0.5f, if (middleExtended) 0.18f else 0.66f, 0f)

        // Ring finger
        val ringMcp = HandLandmarkPoint(HandJoint.RING_FINGER_MCP, 0.6f, 0.6f, 0f)
        val ringPip = HandLandmarkPoint(HandJoint.RING_FINGER_PIP, 0.6f, if (ringExtended) 0.45f else 0.62f, 0f)
        val ringDip = HandLandmarkPoint(HandJoint.RING_FINGER_DIP, 0.6f, if (ringExtended) 0.35f else 0.65f, 0f)
        val ringTip = HandLandmarkPoint(HandJoint.RING_FINGER_TIP, 0.6f, if (ringExtended) 0.2f else 0.68f, 0f)

        // Pinky
        val pinkyMcp = HandLandmarkPoint(HandJoint.PINKY_MCP, 0.7f, 0.62f, 0f)
        val pinkyPip = HandLandmarkPoint(HandJoint.PINKY_PIP, 0.7f, if (pinkyExtended) 0.48f else 0.64f, 0f)
        val pinkyDip = HandLandmarkPoint(HandJoint.PINKY_DIP, 0.7f, if (pinkyExtended) 0.38f else 0.67f, 0f)
        val pinkyTip = HandLandmarkPoint(HandJoint.PINKY_TIP, 0.7f, if (pinkyExtended) 0.23f else 0.7f, 0f)

        return listOf(
            wrist, thumbCmc, thumbMcp, thumbIp, thumbTip,
            indexMcp, indexPip, indexDip, indexTip,
            middleMcp, middlePip, middleDip, middleTip,
            ringMcp, ringPip, ringDip, ringTip,
            pinkyMcp, pinkyPip, pinkyDip, pinkyTip,
        )
    }

    @Test
    fun classifyClosedFist() {
        val landmarks = createLandmarks()
        val (gesture, confidence) = GestureClassifier.classify(landmarks)
        assertEquals(HandGesture.CLOSED_FIST, gesture)
        assertTrue(confidence > 0.5f)
    }

    @Test
    fun classifyOpenPalm() {
        val landmarks = createLandmarks(
            thumbExtended = true,
            indexExtended = true,
            middleExtended = true,
            ringExtended = true,
            pinkyExtended = true,
        )
        val (gesture, confidence) = GestureClassifier.classify(landmarks)
        assertEquals(HandGesture.OPEN_PALM, gesture)
        assertTrue(confidence > 0.5f)
    }

    @Test
    fun classifyVictory() {
        val landmarks = createLandmarks(
            indexExtended = true,
            middleExtended = true,
        )
        val (gesture, confidence) = GestureClassifier.classify(landmarks)
        assertEquals(HandGesture.VICTORY, gesture)
        assertTrue(confidence > 0.5f)
    }

    @Test
    fun classifyPointingUp() {
        val landmarks = createLandmarks(indexExtended = true)
        val (gesture, confidence) = GestureClassifier.classify(landmarks)
        assertEquals(HandGesture.POINTING_UP, gesture)
        assertTrue(confidence > 0.5f)
    }

    @Test
    fun classifyThumbUp() {
        val landmarks = createLandmarks(thumbExtended = true, thumbTipY = 0.3f)
        val (gesture, confidence) = GestureClassifier.classify(landmarks)
        assertEquals(HandGesture.THUMB_UP, gesture)
        assertTrue(confidence > 0.5f)
    }

    @Test
    fun classifyThumbDown() {
        // thumbTip.y > wrist.y means thumb is pointing down (in image coordinates, lower = larger y)
        val landmarks = createLandmarks(thumbExtended = true, thumbTipY = 0.9f)
        val (gesture, confidence) = GestureClassifier.classify(landmarks)
        assertEquals(HandGesture.THUMB_DOWN, gesture)
        assertTrue(confidence > 0.5f)
    }

    @Test
    fun classifyILoveYou() {
        val landmarks = createLandmarks(
            thumbExtended = true,
            indexExtended = true,
            pinkyExtended = true,
        )
        val (gesture, confidence) = GestureClassifier.classify(landmarks)
        assertEquals(HandGesture.I_LOVE_YOU, gesture)
        assertTrue(confidence > 0.5f)
    }

    @Test
    fun classifyReturnsNoneForInsufficientLandmarks() {
        val landmarks = listOf(
            HandLandmarkPoint(HandJoint.WRIST, 0.5f, 0.5f),
        )
        val (gesture, _) = GestureClassifier.classify(landmarks)
        assertEquals(HandGesture.NONE, gesture)
    }

    @Test
    fun classifyReturnsNoneForUnrecognizedPattern() {
        // Middle + ring + pinky extended, but not thumb and not index → no defined pattern
        val landmarks = createLandmarks(
            middleExtended = true,
            ringExtended = true,
            pinkyExtended = true,
        )
        val (gesture, _) = GestureClassifier.classify(landmarks)
        assertEquals(HandGesture.NONE, gesture)
    }
}
