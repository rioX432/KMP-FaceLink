package io.github.kmpfacelink.ifm

import io.github.kmpfacelink.model.BlendShape
import io.github.kmpfacelink.model.GazeData
import io.github.kmpfacelink.model.HeadTransform
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Stress and edge-case tests for the iFacialMocap protocol.
 *
 * Verifies parsing and serialization correctness under rapid bursts,
 * malformed input, and boundary values.
 */
class IfmStressTest {

    @Test
    fun rapidFrameBurstPreservesAllValues() {
        val frameCount = 1000
        for (i in 0 until frameCount) {
            val value = (i % 101).toFloat() / 100f
            val blendShapes = mapOf(
                BlendShape.JAW_OPEN to value,
                BlendShape.EYE_BLINK_LEFT to value,
            )
            val serialized = IfmMessageSerializer.serialize(
                blendShapes = blendShapes,
                headTransform = HeadTransform(pitch = i.toFloat()),
                gazeData = null,
                dataVersion = DataVersion.V1,
            )
            val parsed = IfmMessageParser.parse(serialized)
            val expected = (value * 100).toInt()
            val actual = (parsed.blendShapes[BlendShape.JAW_OPEN]!! * 100).toInt()
            assertEquals(expected, actual, "Frame $i: value mismatch")
        }
    }

    @Test
    fun malformedFieldsAreSkippedGracefully() {
        // Missing value after separator
        val msg1 = "eyeBlink_L-|jawOpen-60|"
        val result1 = IfmMessageParser.parse(msg1)
        assertEquals(1, result1.blendShapes.size)
        assertEquals(0.6f, result1.blendShapes[BlendShape.JAW_OPEN]!!, 0.01f)
    }

    @Test
    fun corruptedHeadDataFallsBackToDefaults() {
        val msg = "=head#not,a,number,0.1,0.2,0.3|eyeBlink_L-50|"
        val result = IfmMessageParser.parse(msg)
        assertEquals(0f, result.headTransform.pitch)
        assertEquals(0f, result.headTransform.yaw)
        assertEquals(0.1f, result.headTransform.positionX, 0.01f)
        assertEquals(1, result.blendShapes.size)
    }

    @Test
    fun truncatedHeadFieldReturnsDefaultTransform() {
        val msg = "=head#10.0,-5.0|"
        val result = IfmMessageParser.parse(msg)
        assertEquals(0f, result.headTransform.pitch)
    }

    @Test
    fun all52BlendShapesRoundTripV1() {
        val allShapes = BlendShape.entries.associateWith { shape ->
            (shape.ordinal % 101).toFloat() / 100f
        }
        val serialized = IfmMessageSerializer.serialize(
            blendShapes = allShapes,
            headTransform = HeadTransform(),
            gazeData = null,
            dataVersion = DataVersion.V1,
        )
        val parsed = IfmMessageParser.parse(serialized)
        assertEquals(BlendShape.entries.size, parsed.blendShapes.size)
    }

    @Test
    fun all52BlendShapesRoundTripV2() {
        val allShapes = BlendShape.entries.associateWith { shape ->
            (shape.ordinal % 101).toFloat() / 100f
        }
        val serialized = IfmMessageSerializer.serialize(
            blendShapes = allShapes,
            headTransform = HeadTransform(),
            gazeData = null,
            dataVersion = DataVersion.V2,
        )
        val parsed = IfmMessageParser.parse(serialized)
        assertEquals(BlendShape.entries.size, parsed.blendShapes.size)
    }

    @Test
    fun extremeHeadRotationValues() {
        val head = HeadTransform(
            pitch = 90f,
            yaw = -180f,
            roll = 360f,
            positionX = -999f,
            positionY = 999f,
            positionZ = 0f,
        )
        val serialized = IfmMessageSerializer.serialize(
            blendShapes = emptyMap(),
            headTransform = head,
            gazeData = null,
            dataVersion = DataVersion.V1,
        )
        val parsed = IfmMessageParser.parse(serialized)
        assertEquals(90f, parsed.headTransform.pitch, 0.01f)
        assertEquals(-180f, parsed.headTransform.yaw, 0.01f)
        assertEquals(360f, parsed.headTransform.roll, 0.01f)
    }

    @Test
    fun extremeEyeGazeValues() {
        val gaze = GazeData(
            rightEyePitch = 45f,
            rightEyeYaw = -45f,
            leftEyePitch = 45f,
            leftEyeYaw = -45f,
        )
        val serialized = IfmMessageSerializer.serialize(
            blendShapes = emptyMap(),
            headTransform = HeadTransform(),
            gazeData = gaze,
            dataVersion = DataVersion.V1,
        )
        val parsed = IfmMessageParser.parse(serialized)
        assertEquals(45f, parsed.gazeData.rightEyePitch, 0.01f)
        assertEquals(-45f, parsed.gazeData.rightEyeYaw, 0.01f)
    }

    @Test
    fun multipleConsecutiveSeparatorsHandled() {
        val msg = "eyeBlink_L-50||jawOpen-30||"
        val result = IfmMessageParser.parse(msg)
        assertTrue(result.blendShapes.isNotEmpty())
    }

    @Test
    fun v2NegativeValueDoesNotConfuseParser() {
        // In V2 format, negative values use minus sign which should not
        // be confused with V1 separator
        val msg = "eyeBlink_L&50|=head#-10.5,-20.3,5.0,0.0,0.0,0.0|sendDataVersion=v2|"
        val result = IfmMessageParser.parse(msg)
        assertEquals(0.5f, result.blendShapes[BlendShape.EYE_BLINK_LEFT]!!, 0.01f)
        assertEquals(-10.5f, result.headTransform.pitch, 0.01f)
        assertEquals(-20.3f, result.headTransform.yaw, 0.01f)
    }
}
