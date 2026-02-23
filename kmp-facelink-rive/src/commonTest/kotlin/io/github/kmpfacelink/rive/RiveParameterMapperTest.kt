package io.github.kmpfacelink.rive

import io.github.kmpfacelink.model.BlendShape
import io.github.kmpfacelink.model.FaceTrackingData
import io.github.kmpfacelink.model.HeadTransform
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RiveParameterMapperTest {

    private val trackingData = FaceTrackingData(
        blendShapes = BlendShape.entries.associateWith { 0.5f },
        headTransform = HeadTransform(pitch = 15f, yaw = -10f, roll = 5f),
        timestampMs = 1000L,
        isTracking = true,
    )

    @Test
    fun defaultMapperProducesAllBlendShapeInputs() {
        val mapper = RiveDefaultMappings.createMapper()
        val result = mapper.map(trackingData)

        // All 52 blend shapes + 3 head rotation = 55 inputs
        assertEquals(BlendShape.entries.size + 3, result.size)

        // Verify all blend shapes present as Number
        for (shape in BlendShape.entries) {
            val input = result[shape.arKitName]
            assertTrue(input is RiveInput.Number, "Missing input for ${shape.arKitName}")
            assertEquals(0.5f, input.value)
        }
    }

    @Test
    fun defaultMapperIncludesHeadRotation() {
        val mapper = RiveDefaultMappings.createMapper()
        val result = mapper.map(trackingData)

        val yaw = result[RiveDefaultMappings.HEAD_YAW] as RiveInput.Number
        val pitch = result[RiveDefaultMappings.HEAD_PITCH] as RiveInput.Number
        val roll = result[RiveDefaultMappings.HEAD_ROLL] as RiveInput.Number

        assertEquals(-10f / 30f, yaw.value)
        assertEquals(15f / 30f, pitch.value)
        assertEquals(5f / 30f, roll.value)
    }

    @Test
    fun headRotationClampedToNormalizedRange() {
        val extremeData = trackingData.copy(
            headTransform = HeadTransform(pitch = 90f, yaw = -90f, roll = 90f),
        )
        val mapper = RiveDefaultMappings.createMapper()
        val result = mapper.map(extremeData)

        val yaw = result[RiveDefaultMappings.HEAD_YAW] as RiveInput.Number
        val pitch = result[RiveDefaultMappings.HEAD_PITCH] as RiveInput.Number
        assertEquals(-1f, yaw.value)
        assertEquals(1f, pitch.value)
    }

    @Test
    fun notTrackingReturnsEmptyMap() {
        val notTracking = FaceTrackingData.notTracking(timestampMs = 0L)
        val mapper = RiveDefaultMappings.createMapper()
        val result = mapper.map(notTracking)

        assertTrue(result.isEmpty())
    }

    @Test
    fun excludedInputsAreOmitted() {
        val config = RiveMapperConfig(
            excludedInputs = setOf("eyeBlinkLeft", "jawOpen", RiveDefaultMappings.HEAD_YAW),
        )
        val mapper = RiveDefaultMappings.createMapper(config)
        val result = mapper.map(trackingData)

        assertTrue("eyeBlinkLeft" !in result)
        assertTrue("jawOpen" !in result)
        assertTrue(RiveDefaultMappings.HEAD_YAW !in result)
        // Other shapes still present
        assertTrue("eyeBlinkRight" in result)
    }

    @Test
    fun inputNameOverridesWork() {
        val config = RiveMapperConfig(
            inputNameOverrides = mapOf("eyeBlinkLeft" to "leftEyeBlink"),
        )
        val mapper = RiveDefaultMappings.createMapper(config)
        val result = mapper.map(trackingData)

        assertTrue("eyeBlinkLeft" !in result)
        assertTrue("leftEyeBlink" in result)
        assertEquals(0.5f, (result["leftEyeBlink"] as RiveInput.Number).value)
    }

    @Test
    fun disableHeadRotation() {
        val config = RiveMapperConfig(includeHeadRotation = false)
        val mapper = RiveDefaultMappings.createMapper(config)
        val result = mapper.map(trackingData)

        assertEquals(BlendShape.entries.size, result.size)
        assertTrue(RiveDefaultMappings.HEAD_YAW !in result)
        assertTrue(RiveDefaultMappings.HEAD_PITCH !in result)
        assertTrue(RiveDefaultMappings.HEAD_ROLL !in result)
    }

    @Test
    fun customMapperImplementation() {
        val mapper = RiveParameterMapper { data ->
            if (!data.isTracking) {
                emptyMap()
            } else {
                mapOf("custom" to RiveInput.BooleanInput(true))
            }
        }
        val result = mapper.map(trackingData)

        assertEquals(1, result.size)
        assertTrue(result["custom"] is RiveInput.BooleanInput)
    }
}
