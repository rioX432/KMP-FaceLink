package io.github.kmpfacelink

import io.github.kmpfacelink.model.BodyJoint
import io.github.kmpfacelink.model.BodyLandmarkPoint
import io.github.kmpfacelink.model.BodyTrackingData
import io.github.kmpfacelink.model.TrackedBody
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BodyTrackingDataTest {

    private fun makeBody(): TrackedBody = TrackedBody(
        landmarks = BodyJoint.entries.map { j ->
            BodyLandmarkPoint(joint = j, x = 0.5f, y = 0.5f, visibility = 1f)
        },
    )

    @Test
    fun notTrackingHasCorrectDefaults() {
        val data = BodyTrackingData.notTracking()
        assertTrue(data.bodies.isEmpty())
        assertFalse(data.isTracking)
        assertEquals(0L, data.timestampMs)
        assertEquals(0, data.sourceImageWidth)
        assertEquals(0, data.sourceImageHeight)
    }

    @Test
    fun notTrackingPreservesTimestamp() {
        val data = BodyTrackingData.notTracking(timestampMs = 7777L)
        assertEquals(7777L, data.timestampMs)
    }

    @Test
    fun constructorAssignsAllProperties() {
        val bodies = listOf(makeBody())
        val data = BodyTrackingData(
            bodies = bodies,
            sourceImageWidth = 1280,
            sourceImageHeight = 720,
            timestampMs = 300L,
            isTracking = true,
        )
        assertEquals(1, data.bodies.size)
        assertEquals(33, data.bodies[0].landmarks.size)
        assertEquals(1280, data.sourceImageWidth)
        assertEquals(720, data.sourceImageHeight)
        assertEquals(300L, data.timestampMs)
        assertTrue(data.isTracking)
    }

    @Test
    fun bodyLandmarkPointsAreAccessible() {
        val body = makeBody()
        val data = BodyTrackingData(
            bodies = listOf(body),
            timestampMs = 0L,
            isTracking = true,
        )
        val nose = data.bodies[0].landmarks.first { it.joint == BodyJoint.NOSE }
        assertEquals(BodyJoint.NOSE, nose.joint)
        assertEquals(0.5f, nose.x)
        assertEquals(1f, nose.visibility)
    }

    @Test
    fun isTrackingFalseWhenNoBodies() {
        val data = BodyTrackingData(
            bodies = emptyList(),
            timestampMs = 0L,
            isTracking = false,
        )
        assertFalse(data.isTracking)
    }

    @Test
    fun multipleBodyTracking() {
        val data = BodyTrackingData(
            bodies = listOf(makeBody(), makeBody()),
            timestampMs = 100L,
            isTracking = true,
        )
        assertEquals(2, data.bodies.size)
    }
}
