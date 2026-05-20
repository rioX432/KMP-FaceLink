package io.github.kmpfacelink

import io.github.kmpfacelink.model.BodyJoint
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Tests for Vision body landmark mapping metadata and body joint coverage.
 *
 * VisionBodyLandmarkMapper.mapLandmarks() and VisionBodyLandmarkMapper3D.mapLandmarks()
 * cannot be tested here because VNHumanBodyPoseObservation and
 * VNHumanBodyPose3DObservation are opaque Objective-C classes. These tests
 * verify the joint mapping structure and coverage expectations.
 */
class VisionBodyLandmarkMapperTest {

    // -- BodyJoint enum metadata --

    @Test
    fun bodyJointEnumHas33Entries() {
        // MediaPipe Pose Landmarker defines 33 landmarks
        assertEquals(33, BodyJoint.entries.size)
    }

    @Test
    fun bodyJointIndicesAreContiguousZeroToThirtyTwo() {
        val indices = BodyJoint.entries.map { it.index }.sorted()
        assertEquals((0..32).toList(), indices)
    }

    @Test
    fun bodyJointOrdinalMatchesMediaPipeIndex() {
        for (joint in BodyJoint.entries) {
            assertEquals(
                joint.ordinal,
                joint.index,
                "BodyJoint.${joint.name} ordinal (${joint.ordinal}) != index (${joint.index})",
            )
        }
    }

    // -- Vision 2D joint coverage --

    /**
     * Vision 2D provides 17 joints that map to BodyJoint. The remaining 16 joints
     * are filled with visibility=0 by the mapper.
     */
    @Test
    fun visionMappedJointsAreSubsetOfBodyJoint() {
        val visionMappedJoints = setOf(
            BodyJoint.NOSE,
            BodyJoint.LEFT_EYE,
            BodyJoint.RIGHT_EYE,
            BodyJoint.LEFT_EAR,
            BodyJoint.RIGHT_EAR,
            BodyJoint.LEFT_SHOULDER,
            BodyJoint.RIGHT_SHOULDER,
            BodyJoint.LEFT_ELBOW,
            BodyJoint.RIGHT_ELBOW,
            BodyJoint.LEFT_WRIST,
            BodyJoint.RIGHT_WRIST,
            BodyJoint.LEFT_HIP,
            BodyJoint.RIGHT_HIP,
            BodyJoint.LEFT_KNEE,
            BodyJoint.RIGHT_KNEE,
            BodyJoint.LEFT_ANKLE,
            BodyJoint.RIGHT_ANKLE,
        )

        assertEquals(17, visionMappedJoints.size)
        assertTrue(
            BodyJoint.entries.toSet().containsAll(visionMappedJoints),
            "All Vision-mapped joints must exist in BodyJoint enum",
        )
    }

    @Test
    fun unmappedJointsCount16() {
        val visionMappedJoints = setOf(
            BodyJoint.NOSE,
            BodyJoint.LEFT_EYE,
            BodyJoint.RIGHT_EYE,
            BodyJoint.LEFT_EAR,
            BodyJoint.RIGHT_EAR,
            BodyJoint.LEFT_SHOULDER,
            BodyJoint.RIGHT_SHOULDER,
            BodyJoint.LEFT_ELBOW,
            BodyJoint.RIGHT_ELBOW,
            BodyJoint.LEFT_WRIST,
            BodyJoint.RIGHT_WRIST,
            BodyJoint.LEFT_HIP,
            BodyJoint.RIGHT_HIP,
            BodyJoint.LEFT_KNEE,
            BodyJoint.RIGHT_KNEE,
            BodyJoint.LEFT_ANKLE,
            BodyJoint.RIGHT_ANKLE,
        )
        val unmapped = BodyJoint.entries.toSet() - visionMappedJoints
        assertEquals(16, unmapped.size)
    }

    @Test
    fun unmappedJointsAreExpectedOnes() {
        // These joints have no direct Vision 2D equivalent
        val expectedUnmapped = setOf(
            BodyJoint.LEFT_EYE_INNER,
            BodyJoint.LEFT_EYE_OUTER,
            BodyJoint.RIGHT_EYE_INNER,
            BodyJoint.RIGHT_EYE_OUTER,
            BodyJoint.MOUTH_LEFT,
            BodyJoint.MOUTH_RIGHT,
            BodyJoint.LEFT_PINKY,
            BodyJoint.RIGHT_PINKY,
            BodyJoint.LEFT_INDEX,
            BodyJoint.RIGHT_INDEX,
            BodyJoint.LEFT_THUMB,
            BodyJoint.RIGHT_THUMB,
            BodyJoint.LEFT_HEEL,
            BodyJoint.RIGHT_HEEL,
            BodyJoint.LEFT_FOOT_INDEX,
            BodyJoint.RIGHT_FOOT_INDEX,
        )

        val visionMappedJoints = setOf(
            BodyJoint.NOSE,
            BodyJoint.LEFT_EYE,
            BodyJoint.RIGHT_EYE,
            BodyJoint.LEFT_EAR,
            BodyJoint.RIGHT_EAR,
            BodyJoint.LEFT_SHOULDER,
            BodyJoint.RIGHT_SHOULDER,
            BodyJoint.LEFT_ELBOW,
            BodyJoint.RIGHT_ELBOW,
            BodyJoint.LEFT_WRIST,
            BodyJoint.RIGHT_WRIST,
            BodyJoint.LEFT_HIP,
            BodyJoint.RIGHT_HIP,
            BodyJoint.LEFT_KNEE,
            BodyJoint.RIGHT_KNEE,
            BodyJoint.LEFT_ANKLE,
            BodyJoint.RIGHT_ANKLE,
        )

        val actualUnmapped = BodyJoint.entries.toSet() - visionMappedJoints
        assertEquals(expectedUnmapped, actualUnmapped)
    }

    // -- Vision 3D joint coverage --

    /**
     * Vision 3D provides 13 joints (fewer than 2D because head sub-joints
     * like eyes/ears are replaced by a single centerHead joint).
     */
    @Test
    fun vision3DMappedJointsCount() {
        val vision3DMappedJoints = setOf(
            BodyJoint.NOSE, // centerHead maps to NOSE
            BodyJoint.LEFT_SHOULDER,
            BodyJoint.RIGHT_SHOULDER,
            BodyJoint.LEFT_ELBOW,
            BodyJoint.RIGHT_ELBOW,
            BodyJoint.LEFT_WRIST,
            BodyJoint.RIGHT_WRIST,
            BodyJoint.LEFT_HIP,
            BodyJoint.RIGHT_HIP,
            BodyJoint.LEFT_KNEE,
            BodyJoint.RIGHT_KNEE,
            BodyJoint.LEFT_ANKLE,
            BodyJoint.RIGHT_ANKLE,
        )
        assertEquals(13, vision3DMappedJoints.size)
    }

    @Test
    fun vision3DMappedJointsAreSubsetOf2D() {
        // Every 3D-mapped joint should also be in the 2D mapping
        val vision2DMapped = setOf(
            BodyJoint.NOSE,
            BodyJoint.LEFT_EYE,
            BodyJoint.RIGHT_EYE,
            BodyJoint.LEFT_EAR,
            BodyJoint.RIGHT_EAR,
            BodyJoint.LEFT_SHOULDER,
            BodyJoint.RIGHT_SHOULDER,
            BodyJoint.LEFT_ELBOW,
            BodyJoint.RIGHT_ELBOW,
            BodyJoint.LEFT_WRIST,
            BodyJoint.RIGHT_WRIST,
            BodyJoint.LEFT_HIP,
            BodyJoint.RIGHT_HIP,
            BodyJoint.LEFT_KNEE,
            BodyJoint.RIGHT_KNEE,
            BodyJoint.LEFT_ANKLE,
            BodyJoint.RIGHT_ANKLE,
        )
        val vision3DMapped = setOf(
            BodyJoint.NOSE,
            BodyJoint.LEFT_SHOULDER,
            BodyJoint.RIGHT_SHOULDER,
            BodyJoint.LEFT_ELBOW,
            BodyJoint.RIGHT_ELBOW,
            BodyJoint.LEFT_WRIST,
            BodyJoint.RIGHT_WRIST,
            BodyJoint.LEFT_HIP,
            BodyJoint.RIGHT_HIP,
            BodyJoint.LEFT_KNEE,
            BodyJoint.RIGHT_KNEE,
            BodyJoint.LEFT_ANKLE,
            BodyJoint.RIGHT_ANKLE,
        )

        assertTrue(
            vision2DMapped.containsAll(vision3DMapped),
            "All 3D-mapped joints should also exist in the 2D mapping",
        )
    }

    // -- Symmetry checks --

    @Test
    fun bodyJointsHaveSymmetricLeftRightPairs() {
        // Verify all left joints have a corresponding right joint
        val leftJoints = BodyJoint.entries.filter { it.name.startsWith("LEFT_") }
        val rightJoints = BodyJoint.entries.filter { it.name.startsWith("RIGHT_") }

        assertEquals(
            leftJoints.size,
            rightJoints.size,
            "Number of LEFT_ and RIGHT_ joints should match",
        )

        for (left in leftJoints) {
            val expectedRightName = left.name.replace("LEFT_", "RIGHT_")
            assertTrue(
                rightJoints.any { it.name == expectedRightName },
                "Missing right counterpart for ${left.name}",
            )
        }
    }

    @Test
    fun noseIsFirstBodyJoint() {
        assertEquals(0, BodyJoint.NOSE.index)
    }

    @Test
    fun rightFootIndexIsLastBodyJoint() {
        assertEquals(32, BodyJoint.RIGHT_FOOT_INDEX.index)
    }
}
