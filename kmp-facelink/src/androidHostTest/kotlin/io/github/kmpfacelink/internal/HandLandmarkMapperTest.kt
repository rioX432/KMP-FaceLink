package io.github.kmpfacelink.internal

import io.github.kmpfacelink.model.HandJoint
import io.github.kmpfacelink.model.Handedness
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class HandLandmarkMapperTest {

    // -- mapJoint tests --

    @Test
    fun mapJointWrist() {
        assertEquals(HandJoint.WRIST, HandLandmarkMapper.mapJoint(0))
    }

    @Test
    fun mapJointThumbTip() {
        assertEquals(HandJoint.THUMB_TIP, HandLandmarkMapper.mapJoint(4))
    }

    @Test
    fun mapJointIndexFingerTip() {
        assertEquals(HandJoint.INDEX_FINGER_TIP, HandLandmarkMapper.mapJoint(8))
    }

    @Test
    fun mapJointPinkyTip() {
        assertEquals(HandJoint.PINKY_TIP, HandLandmarkMapper.mapJoint(20))
    }

    @Test
    fun mapJointWithAllIndices() {
        for (joint in HandJoint.entries) {
            assertEquals(joint, HandLandmarkMapper.mapJoint(joint.index))
        }
    }

    @Test
    fun mapJointWithNegativeIndex() {
        assertNull(HandLandmarkMapper.mapJoint(-1))
    }

    @Test
    fun mapJointWithIndexAboveRange() {
        assertNull(HandLandmarkMapper.mapJoint(21))
        assertNull(HandLandmarkMapper.mapJoint(100))
    }

    // -- mapHandedness tests --

    @Test
    fun mapHandednessLeft() {
        assertEquals(Handedness.LEFT, HandLandmarkMapper.mapHandedness("Left"))
    }

    @Test
    fun mapHandednessRight() {
        assertEquals(Handedness.RIGHT, HandLandmarkMapper.mapHandedness("Right"))
    }

    @Test
    fun mapHandednessUnknownString() {
        assertEquals(Handedness.UNKNOWN, HandLandmarkMapper.mapHandedness("Unknown"))
    }

    @Test
    fun mapHandednessEmptyString() {
        assertEquals(Handedness.UNKNOWN, HandLandmarkMapper.mapHandedness(""))
    }

    @Test
    fun mapHandednessCaseSensitive() {
        // MediaPipe always uses capitalized "Left"/"Right"
        assertEquals(Handedness.UNKNOWN, HandLandmarkMapper.mapHandedness("left"))
        assertEquals(Handedness.UNKNOWN, HandLandmarkMapper.mapHandedness("right"))
        assertEquals(Handedness.UNKNOWN, HandLandmarkMapper.mapHandedness("LEFT"))
    }

    // mapLandmarks() depends on com.google.mediapipe.tasks.components.containers.NormalizedLandmark
    // which is an AAR class with a builder pattern. Skipping mapLandmarks tests here;
    // they should be covered by integration tests on a real device.
}
