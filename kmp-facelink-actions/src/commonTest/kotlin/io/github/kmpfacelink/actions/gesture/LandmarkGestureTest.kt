package io.github.kmpfacelink.actions.gesture

import io.github.kmpfacelink.model.HandJoint
import io.github.kmpfacelink.model.HandLandmarkPoint
import io.github.kmpfacelink.model.HandTrackingData
import io.github.kmpfacelink.model.Handedness
import io.github.kmpfacelink.model.TrackedHand
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LandmarkGestureTest {

    @Test
    fun distanceLessThanMatchesWhenClose() {
        val gesture = landmarkGesture("pinch") {
            distanceLessThan(HandJoint.THUMB_TIP, HandJoint.INDEX_FINGER_TIP, maxDistance = 0.1f)
        }
        val landmarks = listOf(
            point(HandJoint.THUMB_TIP, 0.5f, 0.5f),
            point(HandJoint.INDEX_FINGER_TIP, 0.52f, 0.5f),
        )
        val score = gesture.evaluate(landmarks)
        assertTrue(score > 0f)
    }

    @Test
    fun distanceLessThanFailsWhenFar() {
        val gesture = landmarkGesture("pinch") {
            distanceLessThan(HandJoint.THUMB_TIP, HandJoint.INDEX_FINGER_TIP, maxDistance = 0.1f)
        }
        val landmarks = listOf(
            point(HandJoint.THUMB_TIP, 0.1f, 0.1f),
            point(HandJoint.INDEX_FINGER_TIP, 0.9f, 0.9f),
        )
        val score = gesture.evaluate(landmarks)
        assertEquals(0f, score)
    }

    @Test
    fun distanceGreaterThanMatchesWhenFar() {
        val gesture = landmarkGesture("spread") {
            distanceGreaterThan(HandJoint.INDEX_FINGER_TIP, HandJoint.PINKY_TIP, minDistance = 0.2f)
        }
        val landmarks = listOf(
            point(HandJoint.INDEX_FINGER_TIP, 0.2f, 0.5f),
            point(HandJoint.PINKY_TIP, 0.8f, 0.5f),
        )
        assertTrue(gesture.evaluate(landmarks) > 0f)
    }

    @Test
    fun aboveConditionWorks() {
        val gesture = landmarkGesture("raised") {
            above(HandJoint.INDEX_FINGER_TIP, reference = HandJoint.WRIST)
        }
        val landmarks = listOf(
            point(HandJoint.INDEX_FINGER_TIP, 0.5f, 0.2f),
            point(HandJoint.WRIST, 0.5f, 0.8f),
        )
        assertTrue(gesture.evaluate(landmarks) > 0f)
    }

    @Test
    fun belowConditionWorks() {
        val gesture = landmarkGesture("lowered") {
            below(HandJoint.INDEX_FINGER_TIP, reference = HandJoint.WRIST)
        }
        val landmarks = listOf(
            point(HandJoint.INDEX_FINGER_TIP, 0.5f, 0.9f),
            point(HandJoint.WRIST, 0.5f, 0.5f),
        )
        assertTrue(gesture.evaluate(landmarks) > 0f)
    }

    @Test
    fun multipleConditionsRequireAll() {
        val gesture = landmarkGesture("complex") {
            distanceLessThan(HandJoint.THUMB_TIP, HandJoint.INDEX_FINGER_TIP, maxDistance = 0.1f)
            above(HandJoint.THUMB_TIP, reference = HandJoint.WRIST)
        }
        // Both conditions met
        val matchingLandmarks = listOf(
            point(HandJoint.THUMB_TIP, 0.5f, 0.3f),
            point(HandJoint.INDEX_FINGER_TIP, 0.52f, 0.3f),
            point(HandJoint.WRIST, 0.5f, 0.8f),
        )
        assertTrue(gesture.evaluate(matchingLandmarks) > 0f)

        // Distance condition fails
        val failingLandmarks = listOf(
            point(HandJoint.THUMB_TIP, 0.1f, 0.3f),
            point(HandJoint.INDEX_FINGER_TIP, 0.9f, 0.3f),
            point(HandJoint.WRIST, 0.5f, 0.8f),
        )
        assertEquals(0f, gesture.evaluate(failingLandmarks))
    }

    @Test
    fun emptyConditionsReturnZero() {
        val gesture = LandmarkGesture("empty", emptyList())
        assertEquals(0f, gesture.evaluate(emptyList()))
    }

    @Test
    fun detectorFindsMatches() {
        val gesture = landmarkGesture("pinch") {
            distanceLessThan(HandJoint.THUMB_TIP, HandJoint.INDEX_FINGER_TIP, maxDistance = 0.1f)
        }
        val detector = LandmarkGestureDetector(listOf(gesture))
        val hand = TrackedHand(
            handedness = Handedness.RIGHT,
            landmarks = listOf(
                point(HandJoint.THUMB_TIP, 0.5f, 0.5f),
                point(HandJoint.INDEX_FINGER_TIP, 0.52f, 0.5f),
            ),
        )
        val data = HandTrackingData(
            hands = listOf(hand),
            timestampMs = 0L,
            isTracking = true,
        )
        val matches = detector.detect(data)
        assertEquals(1, matches.size)
        assertEquals("pinch", matches[0].gestureName)
    }

    @Test
    fun detectorReturnsEmptyWhenNotTracking() {
        val gesture = landmarkGesture("any") {
            distanceLessThan(HandJoint.THUMB_TIP, HandJoint.INDEX_FINGER_TIP, maxDistance = 1f)
        }
        val detector = LandmarkGestureDetector(listOf(gesture))
        val data = HandTrackingData.notTracking()
        assertEquals(emptyList(), detector.detect(data))
    }

    private fun point(joint: HandJoint, x: Float, y: Float): HandLandmarkPoint =
        HandLandmarkPoint(joint, x, y, 0f)
}
