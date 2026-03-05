package io.github.kmpfacelink

import io.github.kmpfacelink.model.BodyJoint
import io.github.kmpfacelink.model.BodyLandmarkPoint
import io.github.kmpfacelink.model.TrackedBody
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TrackedBodyTest {

    private fun makeLandmarks(): List<BodyLandmarkPoint> =
        BodyJoint.entries.map { joint ->
            BodyLandmarkPoint(joint = joint, x = 0.5f, y = 0.5f, z = 0f, visibility = 1f)
        }

    @Test
    fun constructorAssignsLandmarks() {
        val landmarks = makeLandmarks()
        val body = TrackedBody(landmarks = landmarks)
        assertEquals(landmarks, body.landmarks)
    }

    @Test
    fun landmarkCountIs33() {
        val body = TrackedBody(landmarks = makeLandmarks())
        assertEquals(33, body.landmarks.size)
    }

    @Test
    fun emptyLandmarksAllowed() {
        val body = TrackedBody(landmarks = emptyList())
        assertTrue(body.landmarks.isEmpty())
    }

    @Test
    fun copyModifiesLandmarks() {
        val original = TrackedBody(landmarks = makeLandmarks())
        val newLandmarks = listOf(
            BodyLandmarkPoint(joint = BodyJoint.NOSE, x = 0.3f, y = 0.1f),
        )
        val copied = original.copy(landmarks = newLandmarks)
        assertEquals(1, copied.landmarks.size)
        assertEquals(BodyJoint.NOSE, copied.landmarks[0].joint)
    }

    @Test
    fun equalityHoldsForIdenticalLandmarks() {
        val landmarks = makeLandmarks()
        val a = TrackedBody(landmarks = landmarks)
        val b = TrackedBody(landmarks = landmarks)
        assertEquals(a, b)
    }

    @Test
    fun specificLandmarkValuesArePreserved() {
        val lm = BodyLandmarkPoint(
            joint = BodyJoint.LEFT_SHOULDER,
            x = 0.25f,
            y = 0.4f,
            z = -0.1f,
            visibility = 0.9f,
        )
        val body = TrackedBody(landmarks = listOf(lm))
        val stored = body.landmarks[0]
        assertEquals(BodyJoint.LEFT_SHOULDER, stored.joint)
        assertEquals(0.25f, stored.x)
        assertEquals(0.4f, stored.y)
        assertEquals(-0.1f, stored.z)
        assertEquals(0.9f, stored.visibility)
    }
}
