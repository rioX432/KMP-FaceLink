package io.github.kmpfacelink

import io.github.kmpfacelink.model.BodyJoint
import io.github.kmpfacelink.model.BodyLandmarkPoint
import io.github.kmpfacelink.model.BodyTrackingData
import io.github.kmpfacelink.model.TrackedBody
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BodyLandmarkTest {

    @Test
    fun bodyJointHas33Entries() {
        assertEquals(33, BodyJoint.entries.size)
    }

    @Test
    fun bodyJointOrdinalsMatchMediaPipeIndices() {
        assertEquals(0, BodyJoint.NOSE.ordinal)
        assertEquals(2, BodyJoint.LEFT_EYE.ordinal)
        assertEquals(5, BodyJoint.RIGHT_EYE.ordinal)
        assertEquals(11, BodyJoint.LEFT_SHOULDER.ordinal)
        assertEquals(12, BodyJoint.RIGHT_SHOULDER.ordinal)
        assertEquals(15, BodyJoint.LEFT_WRIST.ordinal)
        assertEquals(16, BodyJoint.RIGHT_WRIST.ordinal)
        assertEquals(23, BodyJoint.LEFT_HIP.ordinal)
        assertEquals(24, BodyJoint.RIGHT_HIP.ordinal)
        assertEquals(27, BodyJoint.LEFT_ANKLE.ordinal)
        assertEquals(28, BodyJoint.RIGHT_ANKLE.ordinal)
        assertEquals(32, BodyJoint.RIGHT_FOOT_INDEX.ordinal)
    }

    @Test
    fun bodyJointIndexMatchesOrdinal() {
        for (joint in BodyJoint.entries) {
            assertEquals(joint.ordinal, joint.index)
        }
    }

    @Test
    fun bodyLandmarkPointDefaults() {
        val point = BodyLandmarkPoint(joint = BodyJoint.NOSE, x = 0.5f, y = 0.5f)
        assertEquals(0f, point.z)
        assertEquals(0f, point.visibility)
    }

    @Test
    fun bodyLandmarkPointWithVisibility() {
        val point = BodyLandmarkPoint(
            joint = BodyJoint.LEFT_SHOULDER,
            x = 0.3f,
            y = 0.7f,
            z = -0.1f,
            visibility = 0.95f,
        )
        assertEquals(0.95f, point.visibility)
    }

    @Test
    fun trackedBodyHoldsLandmarks() {
        val landmarks = listOf(
            BodyLandmarkPoint(joint = BodyJoint.NOSE, x = 0.5f, y = 0.3f),
            BodyLandmarkPoint(joint = BodyJoint.LEFT_SHOULDER, x = 0.4f, y = 0.6f),
        )
        val body = TrackedBody(landmarks = landmarks)
        assertEquals(2, body.landmarks.size)
    }

    @Test
    fun bodyTrackingDataNotTracking() {
        val data = BodyTrackingData.notTracking(timestampMs = 1000L)
        assertFalse(data.isTracking)
        assertTrue(data.bodies.isEmpty())
        assertEquals(1000L, data.timestampMs)
    }

    @Test
    fun bodyTrackingDataDefaultImageSize() {
        val data = BodyTrackingData.notTracking()
        assertEquals(0, data.sourceImageWidth)
        assertEquals(0, data.sourceImageHeight)
    }
}
