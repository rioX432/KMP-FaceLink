package io.github.kmpfacelink

import io.github.kmpfacelink.internal.ArmFusionProcessor
import io.github.kmpfacelink.model.ArmSource
import io.github.kmpfacelink.model.BodyJoint
import io.github.kmpfacelink.model.BodyLandmarkPoint
import io.github.kmpfacelink.model.BodyTrackingData
import io.github.kmpfacelink.model.HandJoint
import io.github.kmpfacelink.model.HandLandmarkPoint
import io.github.kmpfacelink.model.HandTrackingData
import io.github.kmpfacelink.model.Handedness
import io.github.kmpfacelink.model.TrackedBody
import io.github.kmpfacelink.model.TrackedHand
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ArmFusionProcessorTest {

    private val processor = ArmFusionProcessor()

    // -- Helpers --

    private fun makeBodyData(
        leftVisibility: Float = 1f,
        rightVisibility: Float = 1f,
    ): BodyTrackingData {
        val landmarks = BodyJoint.entries.map { joint ->
            val vis = when (joint) {
                BodyJoint.LEFT_SHOULDER, BodyJoint.LEFT_ELBOW, BodyJoint.LEFT_WRIST ->
                    leftVisibility
                BodyJoint.RIGHT_SHOULDER, BodyJoint.RIGHT_ELBOW, BodyJoint.RIGHT_WRIST ->
                    rightVisibility
                else -> 1f
            }
            BodyLandmarkPoint(joint = joint, x = 0.5f, y = 0.5f, z = 0f, visibility = vis)
        }
        return BodyTrackingData(
            bodies = listOf(TrackedBody(landmarks = landmarks)),
            timestampMs = 100L,
            isTracking = true,
        )
    }

    private fun makeHandData(
        handedness: Handedness = Handedness.LEFT,
    ): HandTrackingData {
        val landmarks = HandJoint.entries.map { joint ->
            HandLandmarkPoint(joint = joint, x = 0.6f, y = 0.7f, z = 0.1f)
        }
        return HandTrackingData(
            hands = listOf(TrackedHand(handedness = handedness, landmarks = landmarks)),
            timestampMs = 100L,
            isTracking = true,
        )
    }

    private fun makeBothHandsData(): HandTrackingData {
        val leftLandmarks = HandJoint.entries.map { joint ->
            HandLandmarkPoint(joint = joint, x = 0.3f, y = 0.4f, z = 0f)
        }
        val rightLandmarks = HandJoint.entries.map { joint ->
            HandLandmarkPoint(joint = joint, x = 0.8f, y = 0.9f, z = 0f)
        }
        return HandTrackingData(
            hands = listOf(
                TrackedHand(handedness = Handedness.LEFT, landmarks = leftLandmarks),
                TrackedHand(handedness = Handedness.RIGHT, landmarks = rightLandmarks),
            ),
            timestampMs = 100L,
            isTracking = true,
        )
    }

    // -- Tests --

    @Test
    fun returnsNullWhenBothInputsAreNull() {
        val result = processor.process(null, null)
        assertNull(result)
    }

    @Test
    fun returnsNullWhenBodyNotTracking() {
        val body = BodyTrackingData.notTracking(100L)
        val result = processor.process(body, null)
        assertNull(result)
    }

    @Test
    fun returnsNullWhenHandNotTracking() {
        val hand = HandTrackingData.notTracking(100L)
        val result = processor.process(null, hand)
        assertNull(result)
    }

    @Test
    fun bodyOnlyProducesBodyOnlySource() {
        val body = makeBodyData()
        val result = processor.process(body, null)

        assertNotNull(result)
        assertNotNull(result.leftArm)
        assertNotNull(result.rightArm)
        assertEquals(ArmSource.BODY_ONLY, result.leftArm.source)
        assertEquals(ArmSource.BODY_ONLY, result.rightArm.source)
    }

    @Test
    fun handOnlyProducesHandOnlySource() {
        val hand = makeHandData(Handedness.LEFT)
        val result = processor.process(null, hand)

        assertNotNull(result)
        assertNotNull(result.leftArm)
        assertEquals(ArmSource.HAND_ONLY, result.leftArm.source)
        // Right arm not detected because only left hand provided
        assertNull(result.rightArm)
    }

    @Test
    fun fusedModeUsesBodyShoulderElbowAndHandWrist() {
        val body = makeBodyData()
        val hand = makeHandData(Handedness.LEFT)
        val result = processor.process(body, hand)

        assertNotNull(result)
        // Left arm should be fused (body + hand)
        assertNotNull(result.leftArm)
        assertEquals(ArmSource.FUSED, result.leftArm.source)
        // Right arm should be body-only (no right hand)
        assertNotNull(result.rightArm)
        assertEquals(ArmSource.BODY_ONLY, result.rightArm.source)
    }

    @Test
    fun fusedWristUsesHandTrackerPosition() {
        val body = makeBodyData()
        val hand = makeHandData(Handedness.LEFT)
        // First call to establish (no smoothing effect)
        processor.reset()
        val result = processor.process(body, hand)

        assertNotNull(result)
        val leftArm = result.leftArm
        assertNotNull(leftArm)
        // Shoulder/elbow come from body (0.5, 0.5)
        assertEquals(0.5f, leftArm.shoulder.x)
        assertEquals(0.5f, leftArm.shoulder.y)
        assertEquals(0.5f, leftArm.elbow.x)
        // Wrist comes from hand tracker (0.6, 0.7)
        assertEquals(0.6f, leftArm.wrist.x)
        assertEquals(0.7f, leftArm.wrist.y)
    }

    @Test
    fun bothHandsFusedWithBody() {
        val body = makeBodyData()
        val hand = makeBothHandsData()
        processor.reset()
        val result = processor.process(body, hand)

        assertNotNull(result)
        assertNotNull(result.leftArm)
        assertNotNull(result.rightArm)
        assertEquals(ArmSource.FUSED, result.leftArm.source)
        assertEquals(ArmSource.FUSED, result.rightArm.source)
    }

    @Test
    fun lowVisibilityBodyArmIsIgnored() {
        // Left arm has very low visibility, right is normal
        val body = makeBodyData(leftVisibility = 0.1f, rightVisibility = 1f)
        val result = processor.process(body, null)

        assertNotNull(result)
        // Left arm should be null due to low visibility
        assertNull(result.leftArm)
        // Right arm should be present
        assertNotNull(result.rightArm)
        assertEquals(ArmSource.BODY_ONLY, result.rightArm.source)
    }

    @Test
    fun smoothingBlendsPreviousAndCurrentValues() {
        val body = makeBodyData()
        processor.reset()

        // First frame establishes baseline
        processor.process(body, null)

        // Second frame with shifted body position
        val shiftedLandmarks = BodyJoint.entries.map { joint ->
            BodyLandmarkPoint(joint = joint, x = 0.8f, y = 0.8f, z = 0f, visibility = 1f)
        }
        val shiftedBody = BodyTrackingData(
            bodies = listOf(TrackedBody(landmarks = shiftedLandmarks)),
            timestampMs = 200L,
            isTracking = true,
        )
        val result = processor.process(shiftedBody, null)

        assertNotNull(result)
        val leftArm = result.leftArm
        assertNotNull(leftArm)
        // Smoothed value should be between 0.5 (previous) and 0.8 (current)
        assertTrue(leftArm.shoulder.x > 0.5f)
        assertTrue(leftArm.shoulder.x < 0.8f)
    }

    @Test
    fun resetClearsSmoothingState() {
        val body = makeBodyData()
        processor.process(body, null)
        processor.reset()

        // After reset, first frame should not be smoothed
        val result = processor.process(body, null)
        assertNotNull(result)
        val leftArm = result.leftArm
        assertNotNull(leftArm)
        assertEquals(0.5f, leftArm.shoulder.x)
    }

    @Test
    fun isTrackingReturnsTrueWhenAnyArmPresent() {
        val body = makeBodyData()
        val result = processor.process(body, null)
        assertNotNull(result)
        assertTrue(result.isTracking)
    }

    @Test
    fun notTrackingFactoryHasNullArms() {
        val data = io.github.kmpfacelink.model.ArmTrackingData.notTracking()
        assertNull(data.leftArm)
        assertNull(data.rightArm)
        assertTrue(!data.isTracking)
    }

    @Test
    fun handOnlyArmHasZeroConfidenceForShoulderElbow() {
        val hand = makeHandData(Handedness.RIGHT)
        processor.reset()
        val result = processor.process(null, hand)

        assertNotNull(result)
        val rightArm = result.rightArm
        assertNotNull(rightArm)
        assertEquals(0f, rightArm.shoulder.confidence)
        assertEquals(0f, rightArm.elbow.confidence)
        assertEquals(1f, rightArm.wrist.confidence)
    }

    @Test
    fun emptyBodiesListReturnsNull() {
        val body = BodyTrackingData(
            bodies = emptyList(),
            timestampMs = 100L,
            isTracking = true,
        )
        val result = processor.process(body, null)
        assertNull(result)
    }

    @Test
    fun emptyHandsListReturnsNull() {
        val hand = HandTrackingData(
            hands = emptyList(),
            timestampMs = 100L,
            isTracking = true,
        )
        val result = processor.process(null, hand)
        assertNull(result)
    }
}
