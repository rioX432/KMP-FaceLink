package io.github.kmpfacelink

import io.github.kmpfacelink.model.HandJoint
import io.github.kmpfacelink.model.HandLandmarkPoint
import io.github.kmpfacelink.util.DepthAwareHandLifter
import io.github.kmpfacelink.util.DepthMap
import io.github.kmpfacelink.util.HandDepthMode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertSame

class DepthAwareHandLifterTest {

    private val tolerance = 1e-4f

    // 2x2 map: at(0,0)=0, at(1,0)=10, at(0,1)=20, at(1,1)=30 (row-major).
    private val gradientMap = DepthMap(width = 2, height = 2, values = floatArrayOf(0f, 10f, 20f, 30f))

    private fun point(joint: HandJoint, x: Float, y: Float) = HandLandmarkPoint(joint, x, y, z = 0f)

    @Test
    fun depthMapRejectsMismatchedValueCount() {
        assertFailsWith<IllegalArgumentException> {
            DepthMap(width = 2, height = 2, values = floatArrayOf(1f, 2f, 3f))
        }
    }

    @Test
    fun emptyLandmarksReturnedUnchanged() {
        val lifter = DepthAwareHandLifter()
        val empty = emptyList<HandLandmarkPoint>()
        assertSame(empty, lifter.enhanceDepth(empty, gradientMap))
    }

    @Test
    fun absoluteModeSamplesCornerExactly() {
        val lifter = DepthAwareHandLifter(mode = HandDepthMode.ABSOLUTE)
        val result = lifter.enhanceDepth(listOf(point(HandJoint.WRIST, 1f, 1f)), gradientMap)
        // bottom-right corner = at(1,1) = 30
        assertEquals(30f, result[0].z, tolerance)
    }

    @Test
    fun absoluteModeBilinearCenterIsAverage() {
        val lifter = DepthAwareHandLifter(mode = HandDepthMode.ABSOLUTE)
        val result = lifter.enhanceDepth(listOf(point(HandJoint.WRIST, 0.5f, 0.5f)), gradientMap)
        // center = mean(0,10,20,30) = 15
        assertEquals(15f, result[0].z, tolerance)
    }

    @Test
    fun absoluteModeAppliesScale() {
        val lifter = DepthAwareHandLifter(mode = HandDepthMode.ABSOLUTE, scale = 2f)
        val uniform = DepthMap(2, 2, floatArrayOf(5f, 5f, 5f, 5f))
        val result = lifter.enhanceDepth(listOf(point(HandJoint.WRIST, 0.3f, 0.7f)), uniform)
        assertEquals(10f, result[0].z, tolerance)
    }

    @Test
    fun wristRelativeModeIsZeroAtWrist() {
        val lifter = DepthAwareHandLifter(mode = HandDepthMode.WRIST_RELATIVE)
        val wrist = point(HandJoint.WRIST, 0f, 0f)
        val result = lifter.enhanceDepth(listOf(wrist), gradientMap)
        assertEquals(0f, result[0].z, tolerance)
    }

    @Test
    fun wristRelativeModeOffsetsOtherJoints() {
        val lifter = DepthAwareHandLifter(mode = HandDepthMode.WRIST_RELATIVE)
        val landmarks = listOf(
            point(HandJoint.WRIST, 0f, 0f), // depth 0
            point(HandJoint.INDEX_FINGER_TIP, 1f, 1f), // depth 30
        )
        val result = lifter.enhanceDepth(landmarks, gradientMap)
        assertEquals(0f, result[0].z, tolerance)
        // (wristDepth - sampled) = (0 - 30) = -30
        assertEquals(-30f, result[1].z, tolerance)
    }

    @Test
    fun wristRelativeFallsBackToFirstJointWhenWristAbsent() {
        val lifter = DepthAwareHandLifter(mode = HandDepthMode.WRIST_RELATIVE)
        // No WRIST joint present — anchor falls back to the first landmark (depth 0 at corner 0,0).
        val landmarks = listOf(
            point(HandJoint.INDEX_FINGER_TIP, 0f, 0f), // anchor, depth 0
            point(HandJoint.PINKY_TIP, 1f, 1f), // depth 30
        )
        val result = lifter.enhanceDepth(landmarks, gradientMap)
        assertEquals(0f, result[0].z, tolerance)
        assertEquals(-30f, result[1].z, tolerance)
    }

    @Test
    fun coordinatesAreClampedToBounds() {
        val lifter = DepthAwareHandLifter(mode = HandDepthMode.ABSOLUTE)
        val outOfRange = lifter.enhanceDepth(listOf(point(HandJoint.WRIST, 5f, 5f)), gradientMap)
        val atCorner = lifter.enhanceDepth(listOf(point(HandJoint.WRIST, 1f, 1f)), gradientMap)
        assertEquals(atCorner[0].z, outOfRange[0].z, tolerance)
    }
}
