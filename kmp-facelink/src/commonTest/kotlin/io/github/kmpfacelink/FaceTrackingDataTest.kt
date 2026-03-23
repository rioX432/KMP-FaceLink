package io.github.kmpfacelink

import io.github.kmpfacelink.model.BlendShape
import io.github.kmpfacelink.model.FaceLandmark
import io.github.kmpfacelink.model.FaceTrackingData
import io.github.kmpfacelink.model.GazeData
import io.github.kmpfacelink.model.HeadTransform
import io.github.kmpfacelink.model.TrackedFace
import io.github.kmpfacelink.model.emptyBlendShapeData
import io.github.kmpfacelink.model.valueOf
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FaceTrackingDataTest {

    @Test
    fun notTrackingHasCorrectDefaults() {
        val data = FaceTrackingData.notTracking()
        assertFalse(data.isTracking)
        assertEquals(0L, data.timestampMs)
        assertTrue(data.landmarks.isEmpty())
        assertNull(data.gazeData)
        assertEquals(0, data.sourceImageWidth)
        assertEquals(0, data.sourceImageHeight)
    }

    @Test
    fun notTrackingPreservesTimestamp() {
        val data = FaceTrackingData.notTracking(timestampMs = 12345L)
        assertEquals(12345L, data.timestampMs)
        assertFalse(data.isTracking)
    }

    @Test
    fun notTrackingBlendShapesAreAllZero() {
        val data = FaceTrackingData.notTracking()
        assertEquals(52, data.blendShapes.size)
        for (shape in BlendShape.entries) {
            assertEquals(0f, data.blendShapes.valueOf(shape))
        }
    }

    @Test
    fun constructorAssignsAllProperties() {
        val blendShapes = emptyBlendShapeData().toMutableMap()
            .also { it[BlendShape.JAW_OPEN] = 0.5f }
        val headTransform = HeadTransform(pitch = 5f, yaw = -10f)
        val gazeData = GazeData(leftEyeYaw = 2f, leftEyePitch = -1f)

        val data = FaceTrackingData(
            blendShapes = blendShapes,
            headTransform = headTransform,
            gazeData = gazeData,
            landmarks = emptyList(),
            sourceImageWidth = 1280,
            sourceImageHeight = 720,
            timestampMs = 999L,
            isTracking = true,
        )

        assertEquals(0.5f, data.blendShapes.valueOf(BlendShape.JAW_OPEN))
        assertEquals(5f, data.headTransform.pitch)
        assertNotNull(data.gazeData)
        assertEquals(2f, data.gazeData!!.leftEyeYaw)
        assertEquals(1280, data.sourceImageWidth)
        assertEquals(720, data.sourceImageHeight)
        assertEquals(999L, data.timestampMs)
        assertTrue(data.isTracking)
    }

    @Test
    fun isTrackingFalseByDefault() {
        val data = FaceTrackingData(
            blendShapes = emptyBlendShapeData(),
            headTransform = HeadTransform(),
            timestampMs = 0L,
            isTracking = false,
        )
        assertFalse(data.isTracking)
    }

    @Test
    fun headTransformDefaultsToZero() {
        val data = FaceTrackingData.notTracking()
        assertEquals(0f, data.headTransform.pitch)
        assertEquals(0f, data.headTransform.yaw)
        assertEquals(0f, data.headTransform.roll)
    }

    @Test
    fun notTrackingHasEmptyFaces() {
        val data = FaceTrackingData.notTracking()
        assertTrue(data.faces.isEmpty())
    }

    @Test
    fun facesDefaultsToEmptyList() {
        val data = FaceTrackingData(
            blendShapes = emptyBlendShapeData(),
            headTransform = HeadTransform(),
            timestampMs = 0L,
            isTracking = false,
        )
        assertTrue(data.faces.isEmpty())
    }

    @Test
    fun facesPropertyStoresTrackedFaces() {
        val blendShapes = emptyBlendShapeData()
        val headTransform = HeadTransform(pitch = 3f)
        val trackedFace = TrackedFace(
            faceIndex = 0,
            blendShapes = blendShapes,
            headTransform = headTransform,
        )

        val data = FaceTrackingData(
            blendShapes = blendShapes,
            headTransform = headTransform,
            timestampMs = 100L,
            isTracking = true,
            faces = listOf(trackedFace),
        )

        assertEquals(1, data.faces.size)
        assertEquals(0, data.faces[0].faceIndex)
        assertEquals(3f, data.faces[0].headTransform.pitch)
    }

    @Test
    fun trackedFaceDefaultConfidenceIsOne() {
        val face = TrackedFace(
            faceIndex = 0,
            blendShapes = emptyBlendShapeData(),
            headTransform = HeadTransform(),
        )
        assertEquals(1.0f, face.confidence)
        assertNull(face.gazeData)
        assertTrue(face.landmarks.isEmpty())
    }

    @Test
    fun trackedFaceIndexIsPreserved() {
        val face = TrackedFace(
            faceIndex = 2,
            blendShapes = emptyBlendShapeData(),
            headTransform = HeadTransform(),
            confidence = 0.8f,
        )
        assertEquals(2, face.faceIndex)
        assertEquals(0.8f, face.confidence)
    }

    @Test
    fun faceLandmarkVisibilityAndPresenceDefaultToZero() {
        val landmark = FaceLandmark(x = 0.5f, y = 0.5f, z = 0.1f)
        assertEquals(0f, landmark.visibility)
        assertEquals(0f, landmark.presence)
    }

    @Test
    fun faceLandmarkStoresVisibilityAndPresence() {
        val landmark = FaceLandmark(
            x = 0.5f,
            y = 0.5f,
            z = 0.1f,
            visibility = 0.95f,
            presence = 0.88f,
        )
        assertEquals(0.95f, landmark.visibility)
        assertEquals(0.88f, landmark.presence)
    }
}
