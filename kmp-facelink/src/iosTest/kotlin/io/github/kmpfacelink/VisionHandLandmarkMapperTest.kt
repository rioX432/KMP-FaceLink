package io.github.kmpfacelink

import io.github.kmpfacelink.internal.VisionHandLandmarkMapper
import io.github.kmpfacelink.model.HandJoint
import io.github.kmpfacelink.model.Handedness
import platform.Vision.VNChiralityLeft
import platform.Vision.VNChiralityRight
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Tests for [VisionHandLandmarkMapper] — the bridge between Apple Vision
 * hand pose observations and KMP-FaceLink model types.
 *
 * mapLandmarks() cannot be tested here because VNHumanHandPoseObservation
 * is an opaque Objective-C class that cannot be constructed outside the
 * Vision framework. These tests cover the pure-logic portions:
 * handedness mapping, and joint mapping metadata verification.
 */
class VisionHandLandmarkMapperTest {

    // -- mapHandedness --

    @Test
    fun mapHandednessLeftChiralityReturnsLeft() {
        val result = VisionHandLandmarkMapper.mapHandedness(VNChiralityLeft)
        assertEquals(Handedness.LEFT, result)
    }

    @Test
    fun mapHandednessRightChiralityReturnsRight() {
        val result = VisionHandLandmarkMapper.mapHandedness(VNChiralityRight)
        assertEquals(Handedness.RIGHT, result)
    }

    @Test
    fun mapHandednessUnknownChiralityReturnsUnknown() {
        // Any value that is neither VNChiralityLeft nor VNChiralityRight
        val result = VisionHandLandmarkMapper.mapHandedness(999L)
        assertEquals(Handedness.UNKNOWN, result)
    }

    @Test
    fun mapHandednessZeroReturnsUnknown() {
        // VNChirality: unknown=0, left=-1, right=1
        // 0 is VNChiralityUnknown, which falls to the else branch
        val result = VisionHandLandmarkMapper.mapHandedness(0L)
        assertEquals(Handedness.UNKNOWN, result)
    }

    @Test
    fun mapHandednessNegativeValueReturnsUnknown() {
        // VNChiralityLeft is -1, so use -99 to test an out-of-range negative value
        val result = VisionHandLandmarkMapper.mapHandedness(-99L)
        assertEquals(Handedness.UNKNOWN, result)
    }

    // -- Joint mapping metadata --

    @Test
    fun handJointEnumHas21Entries() {
        // Vision maps all 21 MediaPipe hand joints
        assertEquals(21, HandJoint.entries.size)
    }

    @Test
    fun handJointIndicesAreContiguousZeroToTwenty() {
        // MediaPipe indices must be 0..20 with no gaps
        val indices = HandJoint.entries.map { it.index }.sorted()
        assertEquals((0..20).toList(), indices)
    }

    @Test
    fun handJointOrdinalMatchesMediaPipeIndex() {
        // Enum ordinals must match MediaPipe landmark indices
        for (joint in HandJoint.entries) {
            assertEquals(
                joint.ordinal,
                joint.index,
                "HandJoint.${joint.name} ordinal (${joint.ordinal}) != index (${joint.index})",
            )
        }
    }

    @Test
    fun wristIsFirstJoint() {
        assertEquals(0, HandJoint.WRIST.index)
    }

    @Test
    fun pinkyTipIsLastJoint() {
        assertEquals(20, HandJoint.PINKY_TIP.index)
    }

    @Test
    fun fingerTipIndicesFollowMediaPipeConvention() {
        // MediaPipe defines finger tips at indices 4, 8, 12, 16, 20
        assertEquals(4, HandJoint.THUMB_TIP.index)
        assertEquals(8, HandJoint.INDEX_FINGER_TIP.index)
        assertEquals(12, HandJoint.MIDDLE_FINGER_TIP.index)
        assertEquals(16, HandJoint.RING_FINGER_TIP.index)
        assertEquals(20, HandJoint.PINKY_TIP.index)
    }

    // -- Handedness enum completeness --

    @Test
    fun handednessEnumHasThreeValues() {
        assertEquals(3, Handedness.entries.size)
    }

    @Test
    fun handednessContainsLeftRightUnknown() {
        val names = Handedness.entries.map { it.name }.toSet()
        assertEquals(setOf("LEFT", "RIGHT", "UNKNOWN"), names)
    }
}
