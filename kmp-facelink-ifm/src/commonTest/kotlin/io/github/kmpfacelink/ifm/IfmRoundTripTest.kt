package io.github.kmpfacelink.ifm

import io.github.kmpfacelink.model.BlendShape
import io.github.kmpfacelink.model.GazeData
import io.github.kmpfacelink.model.HeadTransform
import kotlin.test.Test
import kotlin.test.assertEquals

class IfmRoundTripTest {

    @Test
    fun roundTripV1PreservesBlendShapeValues() {
        val blendShapes = mapOf(
            BlendShape.JAW_OPEN to 0.75f,
            BlendShape.EYE_BLINK_LEFT to 0.5f,
            BlendShape.MOUTH_SMILE_LEFT to 0.3f,
        )
        val serialized = IfmMessageSerializer.serialize(
            blendShapes = blendShapes,
            headTransform = HeadTransform(),
            gazeData = null,
            dataVersion = DataVersion.V1,
        )
        val parsed = IfmMessageParser.parse(serialized)

        assertEquals(75, (parsed.blendShapes[BlendShape.JAW_OPEN]!! * 100).toInt())
        assertEquals(50, (parsed.blendShapes[BlendShape.EYE_BLINK_LEFT]!! * 100).toInt())
        assertEquals(30, (parsed.blendShapes[BlendShape.MOUTH_SMILE_LEFT]!! * 100).toInt())
    }

    @Test
    fun roundTripV2PreservesBlendShapeValues() {
        val blendShapes = mapOf(BlendShape.JAW_OPEN to 0.42f)
        val serialized = IfmMessageSerializer.serialize(
            blendShapes = blendShapes,
            headTransform = HeadTransform(),
            gazeData = null,
            dataVersion = DataVersion.V2,
        )
        val parsed = IfmMessageParser.parse(serialized)

        assertEquals(42, (parsed.blendShapes[BlendShape.JAW_OPEN]!! * 100).toInt())
    }

    @Test
    fun roundTripPreservesHeadTransform() {
        val head = HeadTransform(
            pitch = 15.0f,
            yaw = -10.0f,
            roll = 5.0f,
            positionX = 0.05f,
            positionY = 0.1f,
            positionZ = 0.5f,
        )
        val serialized = IfmMessageSerializer.serialize(
            blendShapes = emptyMap(),
            headTransform = head,
            gazeData = null,
            dataVersion = DataVersion.V1,
        )
        val parsed = IfmMessageParser.parse(serialized)

        assertEquals(15.0f, parsed.headTransform.pitch, 0.01f)
        assertEquals(-10.0f, parsed.headTransform.yaw, 0.01f)
        assertEquals(5.0f, parsed.headTransform.roll, 0.01f)
        assertEquals(0.05f, parsed.headTransform.positionX, 0.01f)
    }

    @Test
    fun roundTripPreservesEyeRotation() {
        val gaze = GazeData(
            rightEyePitch = 8.0f,
            rightEyeYaw = -4.0f,
            leftEyePitch = 7.0f,
            leftEyeYaw = -3.0f,
        )
        val serialized = IfmMessageSerializer.serialize(
            blendShapes = emptyMap(),
            headTransform = HeadTransform(),
            gazeData = gaze,
            dataVersion = DataVersion.V1,
        )
        val parsed = IfmMessageParser.parse(serialized)

        assertEquals(8.0f, parsed.gazeData.rightEyePitch, 0.01f)
        assertEquals(-4.0f, parsed.gazeData.rightEyeYaw, 0.01f)
        assertEquals(7.0f, parsed.gazeData.leftEyePitch, 0.01f)
        assertEquals(-3.0f, parsed.gazeData.leftEyeYaw, 0.01f)
    }
}
