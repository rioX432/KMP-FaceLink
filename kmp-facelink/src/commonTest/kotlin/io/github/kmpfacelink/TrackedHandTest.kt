package io.github.kmpfacelink

import io.github.kmpfacelink.model.HandGesture
import io.github.kmpfacelink.model.HandJoint
import io.github.kmpfacelink.model.HandLandmarkPoint
import io.github.kmpfacelink.model.Handedness
import io.github.kmpfacelink.model.TrackedHand
import kotlin.test.Test
import kotlin.test.assertEquals

class TrackedHandTest {

    private fun makeLandmarks(): List<HandLandmarkPoint> =
        HandJoint.entries.map { joint ->
            HandLandmarkPoint(joint = joint, x = 0.1f, y = 0.2f, z = 0f)
        }

    @Test
    fun constructorAssignsAllProperties() {
        val landmarks = makeLandmarks()
        val hand = TrackedHand(
            handedness = Handedness.RIGHT,
            landmarks = landmarks,
            gesture = HandGesture.OPEN_PALM,
            gestureConfidence = 0.9f,
        )
        assertEquals(Handedness.RIGHT, hand.handedness)
        assertEquals(landmarks, hand.landmarks)
        assertEquals(HandGesture.OPEN_PALM, hand.gesture)
        assertEquals(0.9f, hand.gestureConfidence)
    }

    @Test
    fun defaultGestureIsNone() {
        val hand = TrackedHand(
            handedness = Handedness.LEFT,
            landmarks = makeLandmarks(),
        )
        assertEquals(HandGesture.NONE, hand.gesture)
        assertEquals(0f, hand.gestureConfidence)
    }

    @Test
    fun landmarkCountIs21() {
        val hand = TrackedHand(handedness = Handedness.LEFT, landmarks = makeLandmarks())
        assertEquals(21, hand.landmarks.size)
    }

    @Test
    fun copyModifiesHandedness() {
        val hand = TrackedHand(handedness = Handedness.LEFT, landmarks = makeLandmarks())
        val copied = hand.copy(handedness = Handedness.RIGHT)
        assertEquals(Handedness.RIGHT, copied.handedness)
        assertEquals(hand.landmarks, copied.landmarks)
    }

    @Test
    fun equalityHoldsForIdenticalData() {
        val landmarks = makeLandmarks()
        val a = TrackedHand(Handedness.RIGHT, landmarks, HandGesture.VICTORY, 0.8f)
        val b = TrackedHand(Handedness.RIGHT, landmarks, HandGesture.VICTORY, 0.8f)
        assertEquals(a, b)
    }

    @Test
    fun gestureConfidenceStoredCorrectly() {
        val hand = TrackedHand(
            handedness = Handedness.RIGHT,
            landmarks = emptyList(),
            gesture = HandGesture.THUMB_UP,
            gestureConfidence = 0.75f,
        )
        assertEquals(0.75f, hand.gestureConfidence)
    }
}
