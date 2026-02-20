package io.github.kmpfacelink

import io.github.kmpfacelink.model.HandGesture
import io.github.kmpfacelink.model.HandJoint
import io.github.kmpfacelink.model.HandLandmarkPoint
import io.github.kmpfacelink.model.HandTrackingData
import io.github.kmpfacelink.model.Handedness
import io.github.kmpfacelink.model.TrackedHand
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class HandLandmarkTest {

    @Test
    fun handJointHas21Entries() {
        assertEquals(21, HandJoint.entries.size)
    }

    @Test
    fun handJointOrdinalsMatchMediaPipeIndices() {
        assertEquals(0, HandJoint.WRIST.ordinal)
        assertEquals(1, HandJoint.THUMB_CMC.ordinal)
        assertEquals(4, HandJoint.THUMB_TIP.ordinal)
        assertEquals(5, HandJoint.INDEX_FINGER_MCP.ordinal)
        assertEquals(8, HandJoint.INDEX_FINGER_TIP.ordinal)
        assertEquals(9, HandJoint.MIDDLE_FINGER_MCP.ordinal)
        assertEquals(12, HandJoint.MIDDLE_FINGER_TIP.ordinal)
        assertEquals(13, HandJoint.RING_FINGER_MCP.ordinal)
        assertEquals(16, HandJoint.RING_FINGER_TIP.ordinal)
        assertEquals(17, HandJoint.PINKY_MCP.ordinal)
        assertEquals(20, HandJoint.PINKY_TIP.ordinal)
    }

    @Test
    fun handGestureHas16Entries() {
        assertEquals(16, HandGesture.entries.size)
    }

    @Test
    fun handednessHas3Entries() {
        assertEquals(3, Handedness.entries.size)
    }

    @Test
    fun handLandmarkPointDefaultZ() {
        val point = HandLandmarkPoint(joint = HandJoint.WRIST, x = 0.5f, y = 0.5f)
        assertEquals(0f, point.z)
    }

    @Test
    fun trackedHandDefaultGesture() {
        val hand = TrackedHand(
            handedness = Handedness.RIGHT,
            landmarks = emptyList(),
        )
        assertEquals(HandGesture.NONE, hand.gesture)
        assertEquals(0f, hand.gestureConfidence)
    }

    @Test
    fun handTrackingDataNotTracking() {
        val data = HandTrackingData.notTracking(timestampMs = 1000L)
        assertFalse(data.isTracking)
        assertTrue(data.hands.isEmpty())
        assertEquals(1000L, data.timestampMs)
    }

    @Test
    fun handTrackingDataDefaultImageSize() {
        val data = HandTrackingData.notTracking()
        assertEquals(0, data.sourceImageWidth)
        assertEquals(0, data.sourceImageHeight)
    }
}
