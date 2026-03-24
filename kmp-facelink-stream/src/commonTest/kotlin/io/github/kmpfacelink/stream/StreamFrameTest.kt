package io.github.kmpfacelink.stream

import io.github.kmpfacelink.model.BlendShape
import io.github.kmpfacelink.model.FaceTrackingData
import io.github.kmpfacelink.model.HandJoint
import io.github.kmpfacelink.model.HandLandmarkPoint
import io.github.kmpfacelink.model.HandTrackingData
import io.github.kmpfacelink.model.Handedness
import io.github.kmpfacelink.model.HeadTransform
import io.github.kmpfacelink.model.TrackedHand
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class StreamFrameTest {

    @Test
    fun buildStreamFrameWithFaceData() {
        val blendShapes = mapOf(
            BlendShape.JAW_OPEN to 0.5f,
            BlendShape.EYE_BLINK_LEFT to 0.8f,
        )
        val headTransform = HeadTransform(
            pitch = 10f,
            yaw = -5f,
            roll = 2f,
            positionX = 0.1f,
            positionY = 0.2f,
            positionZ = -0.3f,
        )
        val faceData = FaceTrackingData(
            blendShapes = blendShapes,
            headTransform = headTransform,
            timestampMs = 12345L,
            isTracking = true,
        )

        val frame = buildStreamFrame(faceData, null, 12345L)

        assertEquals(StreamFrame.FRAME_TYPE_TRACKING, frame.type)
        assertEquals(12345L, frame.timestampMs)
        assertNotNull(frame.face)
        assertNull(frame.hands)

        val face = frame.face!!
        assertEquals(0.5f, face.blendShapes["jawOpen"])
        assertEquals(0.8f, face.blendShapes["eyeBlinkLeft"])
        assertEquals(10f, face.head.pitch)
        assertEquals(-5f, face.head.yaw)
        assertEquals(2f, face.head.roll)
    }

    @Test
    fun buildStreamFrameWithHandData() {
        val landmarks = HandJoint.entries.map { joint ->
            HandLandmarkPoint(joint = joint, x = 0.5f, y = 0.5f, z = 0.1f)
        }
        val handData = HandTrackingData(
            hands = listOf(TrackedHand(handedness = Handedness.LEFT, landmarks = landmarks)),
            timestampMs = 99L,
            isTracking = true,
        )

        val frame = buildStreamFrame(null, handData, 99L)

        assertNull(frame.face)
        assertNotNull(frame.hands)
        assertEquals(1, frame.hands!!.size)
        assertEquals("left", frame.hands!![0].side)
        assertEquals(63, frame.hands!![0].landmarks.size)
    }

    @Test
    fun buildStreamFrameNotTrackingReturnsNullFace() {
        val faceData = FaceTrackingData.notTracking(timestampMs = 100L)
        val frame = buildStreamFrame(faceData, null, 100L)
        assertNull(frame.face)
    }

    @Test
    fun faceFrameToBlendShapeDataRoundTrip() {
        val original = mapOf(
            BlendShape.JAW_OPEN to 0.7f,
            BlendShape.MOUTH_SMILE_LEFT to 0.3f,
        )
        val faceFrame = FaceFrame(
            blendShapes = original.mapKeys { (k, _) -> k.arKitName },
            head = HeadPose(0f, 0f, 0f),
        )
        val recovered = faceFrame.toBlendShapeData()
        assertEquals(0.7f, recovered[BlendShape.JAW_OPEN])
        assertEquals(0.3f, recovered[BlendShape.MOUTH_SMILE_LEFT])
    }
}
