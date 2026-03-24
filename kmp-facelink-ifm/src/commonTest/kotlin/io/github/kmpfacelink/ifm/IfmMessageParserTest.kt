package io.github.kmpfacelink.ifm

import io.github.kmpfacelink.model.BlendShape
import kotlin.test.Test
import kotlin.test.assertEquals

class IfmMessageParserTest {

    @Test
    fun parseV1BlendShapeValues() {
        val message = "eyeBlink_L-50|jawOpen-100|mouthSmile_L-0|"
        val result = IfmMessageParser.parse(message)
        assertEquals(0.5f, result.blendShapes[BlendShape.EYE_BLINK_LEFT])
        assertEquals(1.0f, result.blendShapes[BlendShape.JAW_OPEN])
        assertEquals(0.0f, result.blendShapes[BlendShape.MOUTH_SMILE_LEFT])
    }

    @Test
    fun parseV2BlendShapeValues() {
        val message = "eyeBlink_L&75|jawOpen&25|sendDataVersion=v2|"
        val result = IfmMessageParser.parse(message)
        assertEquals(0.75f, result.blendShapes[BlendShape.EYE_BLINK_LEFT])
        assertEquals(0.25f, result.blendShapes[BlendShape.JAW_OPEN])
    }

    @Test
    fun parseHeadTransform() {
        val message = "=head#10.500000,-5.200000,3.000000,0.100000,0.200000,0.300000|"
        val result = IfmMessageParser.parse(message)
        assertEquals(10.5f, result.headTransform.pitch)
        assertEquals(-5.2f, result.headTransform.yaw, 0.01f)
        assertEquals(3.0f, result.headTransform.roll)
        assertEquals(0.1f, result.headTransform.positionX, 0.01f)
        assertEquals(0.2f, result.headTransform.positionY, 0.01f)
        assertEquals(0.3f, result.headTransform.positionZ, 0.01f)
    }

    @Test
    fun parseEyeRotations() {
        val message = "rightEye#4.000000,-2.000000,0.000000|leftEye#5.000000,-3.000000,0.000000|"
        val result = IfmMessageParser.parse(message)
        assertEquals(4.0f, result.gazeData.rightEyePitch)
        assertEquals(-2.0f, result.gazeData.rightEyeYaw)
        assertEquals(5.0f, result.gazeData.leftEyePitch)
        assertEquals(-3.0f, result.gazeData.leftEyeYaw)
    }

    @Test
    fun parseCompleteMessage() {
        val message = buildString {
            append("eyeBlink_L-80|jawOpen-60|")
            append("=head#10.0,-5.0,3.0,0.1,0.2,0.3|")
            append("rightEye#4.0,-2.0,0.0|")
            append("leftEye#5.0,-3.0,0.0|")
        }
        val result = IfmMessageParser.parse(message)
        assertEquals(0.8f, result.blendShapes.getValue(BlendShape.EYE_BLINK_LEFT), 0.01f)
        assertEquals(0.6f, result.blendShapes.getValue(BlendShape.JAW_OPEN), 0.01f)
        assertEquals(10.0f, result.headTransform.pitch)
        assertEquals(4.0f, result.gazeData.rightEyePitch)
    }

    @Test
    fun parseEmptyMessageReturnsDefaults() {
        val result = IfmMessageParser.parse("")
        assertEquals(0, result.blendShapes.size)
        assertEquals(0f, result.headTransform.pitch)
        assertEquals(0f, result.gazeData.leftEyePitch)
    }

    @Test
    fun parseUnknownBlendShapeNamesAreSkipped() {
        val message = "unknownShape-50|eyeBlink_L-30|"
        val result = IfmMessageParser.parse(message)
        assertEquals(1, result.blendShapes.size)
        assertEquals(0.3f, result.blendShapes.getValue(BlendShape.EYE_BLINK_LEFT), 0.01f)
    }
}
