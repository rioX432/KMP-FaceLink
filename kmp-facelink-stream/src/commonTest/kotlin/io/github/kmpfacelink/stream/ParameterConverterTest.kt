package io.github.kmpfacelink.stream

import io.github.kmpfacelink.model.BlendShape
import io.github.kmpfacelink.model.FaceTrackingData
import io.github.kmpfacelink.model.HeadTransform
import io.github.kmpfacelink.model.emptyBlendShapeData
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ParameterConverterTest {

    @Test
    fun convertProduces55Parameters() {
        val data = FaceTrackingData(
            blendShapes = emptyBlendShapeData(),
            headTransform = HeadTransform(),
            timestampMs = 0L,
            isTracking = true,
        )
        val params = ParameterConverter.convert(data)
        assertEquals(55, params.size)
    }

    @Test
    fun convertIncludesAllBlendShapes() {
        val data = FaceTrackingData(
            blendShapes = emptyBlendShapeData(),
            headTransform = HeadTransform(),
            timestampMs = 0L,
            isTracking = true,
        )
        val params = ParameterConverter.convert(data)

        for (shape in BlendShape.entries) {
            assertTrue(
                params.containsKey(shape.arKitName),
                "Missing blend shape: ${shape.arKitName}",
            )
        }
    }

    @Test
    fun convertIncludesHeadAngles() {
        val data = FaceTrackingData(
            blendShapes = emptyBlendShapeData(),
            headTransform = HeadTransform(pitch = 10f, yaw = -5f, roll = 3f),
            timestampMs = 0L,
            isTracking = true,
        )
        val params = ParameterConverter.convert(data)

        assertEquals(10f, params["FaceAngleX"])
        assertEquals(-5f, params["FaceAngleY"])
        assertEquals(3f, params["FaceAngleZ"])
    }

    @Test
    fun convertPreservesBlendShapeValues() {
        val blendShapes = BlendShape.entries.associateWith { 0f }.toMutableMap()
        blendShapes[BlendShape.EYE_BLINK_LEFT] = 0.8f
        blendShapes[BlendShape.MOUTH_SMILE_LEFT] = 0.6f

        val data = FaceTrackingData(
            blendShapes = blendShapes,
            headTransform = HeadTransform(),
            timestampMs = 0L,
            isTracking = true,
        )
        val params = ParameterConverter.convert(data)

        assertEquals(0.8f, params["eyeBlinkLeft"])
        assertEquals(0.6f, params["mouthSmileLeft"])
    }

    @Test
    fun convertNotTrackingProducesZeroes() {
        val data = FaceTrackingData.notTracking()
        val params = ParameterConverter.convert(data)

        assertEquals(55, params.size)
        params.forEach { (key, value) ->
            assertEquals(0f, value, "Expected 0 for $key")
        }
    }
}
