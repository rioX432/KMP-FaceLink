package io.github.kmpfacelink

import io.github.kmpfacelink.model.BlendShape
import io.github.kmpfacelink.model.BodyTrackingData
import io.github.kmpfacelink.model.FaceLandmark
import io.github.kmpfacelink.model.FaceTrackingData
import io.github.kmpfacelink.model.GazeData
import io.github.kmpfacelink.model.HandTrackingData
import io.github.kmpfacelink.model.HeadTransform
import io.github.kmpfacelink.model.HolisticTrackingData
import io.github.kmpfacelink.model.ThermalState
import io.github.kmpfacelink.model.allShapesAtRest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Tests for edge cases in tracking data models.
 *
 * Verifies that data classes handle unusual input values (NaN, empty lists,
 * extreme floats) without crashing.
 */
class InputEdgeCaseTest {

    // -- BlendShapeData with NaN values --

    @Test
    fun blendShapeDataWithNaNValuesIsConstructible() {
        val data = mapOf(
            BlendShape.JAW_OPEN to Float.NaN,
            BlendShape.EYE_BLINK_LEFT to Float.NaN,
        )
        // Should not crash when accessing values
        assertTrue(data[BlendShape.JAW_OPEN]!!.isNaN())
    }

    @Test
    fun faceTrackingDataWithNaNBlendShapesIsAccessible() {
        val blendShapes = BlendShape.entries.associateWith { Float.NaN }
        val data = FaceTrackingData(
            blendShapes = blendShapes,
            headTransform = HeadTransform(),
            timestampMs = 0L,
            isTracking = true,
        )
        assertTrue(data.isTracking)
        assertTrue(data.blendShapes[BlendShape.JAW_OPEN]!!.isNaN())
    }

    // -- Empty landmark list --

    @Test
    fun faceTrackingDataWithEmptyLandmarks() {
        val data = FaceTrackingData(
            blendShapes = allShapesAtRest(),
            headTransform = HeadTransform(),
            landmarks = emptyList(),
            timestampMs = 0L,
            isTracking = true,
        )
        assertTrue(data.landmarks.isEmpty())
        assertTrue(data.isTracking)
    }

    // -- HeadTransform with extreme values --

    @Test
    fun headTransformWithExtremeRotationValues() {
        val transform = HeadTransform(
            pitch = 360f,
            yaw = -360f,
            roll = Float.MAX_VALUE,
        )
        assertEquals(360f, transform.pitch)
        assertEquals(-360f, transform.yaw)
        assertEquals(Float.MAX_VALUE, transform.roll)
    }

    @Test
    fun headTransformWithNaNValues() {
        val transform = HeadTransform(
            pitch = Float.NaN,
            yaw = Float.NaN,
            roll = Float.NaN,
            positionX = Float.NaN,
            positionY = Float.NaN,
            positionZ = Float.NaN,
        )
        assertTrue(transform.pitch.isNaN())
        assertTrue(transform.positionX.isNaN())
    }

    @Test
    fun headTransformWithInfiniteValues() {
        val transform = HeadTransform(
            pitch = Float.POSITIVE_INFINITY,
            yaw = Float.NEGATIVE_INFINITY,
        )
        assertTrue(transform.pitch.isInfinite())
        assertTrue(transform.yaw.isInfinite())
    }

    @Test
    fun headTransformWithNullTransformMatrix() {
        val transform = HeadTransform(transformMatrix = null)
        assertNull(transform.transformMatrix)
    }

    @Test
    fun headTransformWithEmptyTransformMatrix() {
        val transform = HeadTransform(transformMatrix = FloatArray(0))
        assertEquals(0, transform.transformMatrix!!.size)
    }

    // -- HandTrackingData with 0 hands --

    @Test
    fun handTrackingDataWithZeroHands() {
        val data = HandTrackingData(
            hands = emptyList(),
            timestampMs = 100L,
            isTracking = false,
        )
        assertTrue(data.hands.isEmpty())
        assertFalse(data.isTracking)
    }

    // -- notTracking() factory methods --

    @Test
    fun faceNotTrackingPropertiesAreSafe() {
        val data = FaceTrackingData.notTracking()
        assertFalse(data.isTracking)
        assertEquals(0L, data.timestampMs)
        assertTrue(data.blendShapes.isEmpty(), "nothing was measured, so nothing is reported")
        assertTrue(data.landmarks.isEmpty())
        assertTrue(data.faces.isEmpty())
        assertNull(data.gazeData)
    }

    @Test
    fun faceNotTrackingWithCustomTimestamp() {
        val data = FaceTrackingData.notTracking(timestampMs = 12345L)
        assertEquals(12345L, data.timestampMs)
        assertFalse(data.isTracking)
    }

    @Test
    fun handNotTrackingPropertiesAreSafe() {
        val data = HandTrackingData.notTracking()
        assertFalse(data.isTracking)
        assertEquals(0L, data.timestampMs)
        assertTrue(data.hands.isEmpty())
    }

    @Test
    fun bodyNotTrackingPropertiesAreSafe() {
        val data = BodyTrackingData.notTracking()
        assertFalse(data.isTracking)
        assertEquals(0L, data.timestampMs)
        assertTrue(data.bodies.isEmpty())
    }

    @Test
    fun holisticNotTrackingPropertiesAreSafe() {
        val data = HolisticTrackingData.notTracking()
        assertFalse(data.isTracking)
        assertEquals(0L, data.timestampMs)
        assertNull(data.face)
        assertNull(data.hand)
        assertNull(data.body)
        assertTrue(data.activeModalities.isEmpty())
    }

    // -- GazeData edge cases --

    @Test
    fun gazeDataWithNaNValues() {
        val gaze = GazeData(
            leftEyeYaw = Float.NaN,
            leftEyePitch = Float.NaN,
            rightEyeYaw = Float.NaN,
            rightEyePitch = Float.NaN,
        )
        assertTrue(gaze.combinedYaw.isNaN())
        assertTrue(gaze.combinedPitch.isNaN())
    }

    @Test
    fun gazeDataDefaultsToZero() {
        val gaze = GazeData()
        assertEquals(0f, gaze.leftEyeYaw)
        assertEquals(0f, gaze.combinedYaw)
        assertEquals(0f, gaze.combinedPitch)
    }

    // -- FaceLandmark edge cases --

    @Test
    fun faceLandmarkWithNaNCoordinates() {
        val landmark = FaceLandmark(
            x = Float.NaN,
            y = Float.NaN,
            relativeDepth = Float.NaN,
        )
        assertTrue(landmark.x.isNaN())
    }

    @Test
    fun faceLandmarkWithExtremeCoordinates() {
        val landmark = FaceLandmark(
            x = Float.MAX_VALUE,
            y = Float.MIN_VALUE,
            relativeDepth = Float.NEGATIVE_INFINITY,
        )
        assertEquals(Float.MAX_VALUE, landmark.x)
        assertEquals(Float.MIN_VALUE, landmark.y)
        assertTrue(checkNotNull(landmark.relativeDepth).isInfinite())
    }

    // -- ThermalState in tracking data --

    @Test
    fun faceTrackingDataDefaultsThermalNominal() {
        val data = FaceTrackingData.notTracking()
        assertEquals(ThermalState.NOMINAL, data.thermalState)
    }

    @Test
    fun handTrackingDataDefaultsThermalNominal() {
        val data = HandTrackingData.notTracking()
        assertEquals(ThermalState.NOMINAL, data.thermalState)
    }

    @Test
    fun bodyTrackingDataDefaultsThermalNominal() {
        val data = BodyTrackingData.notTracking()
        assertEquals(ThermalState.NOMINAL, data.thermalState)
    }

    // -- allShapesAtRest --

    @Test
    fun allShapesAtRestContainsAllShapes() {
        val data = allShapesAtRest()
        assertEquals(BlendShape.entries.size, data.size)
        for (shape in BlendShape.entries) {
            assertEquals(0f, data[shape])
        }
    }

    @Test
    fun allShapesAtRestIsSameInstance() {
        val a = allShapesAtRest()
        val b = allShapesAtRest()
        // Should be the same cached instance
        assertTrue(a === b)
    }
}
