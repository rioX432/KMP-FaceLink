package io.github.kmpfacelink.vmc

import io.github.kmpfacelink.model.BlendShape
import io.github.kmpfacelink.model.HeadTransform
import io.github.kmpfacelink.vmc.osc.OscArgument
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class VmcMessageBuilderTest {

    @Test
    fun buildBlendShapeMessagesCreatesOnePerShapePlusApply() {
        val blendShapes = mapOf(
            BlendShape.EYE_BLINK_LEFT to 0.5f,
            BlendShape.JAW_OPEN to 0.8f,
        )

        val messages = VmcMessageBuilder.buildBlendShapeMessages(blendShapes)

        // 2 Blend/Val messages + 1 Blend/Apply
        assertEquals(3, messages.size)
        assertEquals(VmcAddress.BLEND_VAL, messages[0].address)
        assertEquals(VmcAddress.BLEND_VAL, messages[1].address)
        assertEquals(VmcAddress.BLEND_APPLY, messages[2].address)
        assertTrue(messages[2].arguments.isEmpty())
    }

    @Test
    fun buildBlendShapeMessageUsesArKitNames() {
        val blendShapes = mapOf(BlendShape.MOUTH_SMILE_LEFT to 0.9f)

        val messages = VmcMessageBuilder.buildBlendShapeMessages(blendShapes)

        val nameArg = messages[0].arguments[0] as OscArgument.StringArg
        val valueArg = messages[0].arguments[1] as OscArgument.FloatArg
        assertEquals("mouthSmileLeft", nameArg.value)
        assertEquals(0.9f, valueArg.value)
    }

    @Test
    fun buildHeadBoneMessageHasCorrectFormat() {
        val head = HeadTransform(
            pitch = 0f,
            yaw = 0f,
            roll = 0f,
            positionX = 1.0f,
            positionY = 2.0f,
            positionZ = 3.0f,
        )

        val message = VmcMessageBuilder.buildHeadBoneMessage(head)

        assertEquals(VmcAddress.BONE_POS, message.address)
        assertEquals(8, message.arguments.size)
        assertEquals("Head", (message.arguments[0] as OscArgument.StringArg).value)
        assertEquals(1.0f, (message.arguments[1] as OscArgument.FloatArg).value)
        assertEquals(2.0f, (message.arguments[2] as OscArgument.FloatArg).value)
        assertEquals(3.0f, (message.arguments[3] as OscArgument.FloatArg).value)
    }

    @Test
    fun buildHeadBoneMessageIdentityQuaternionForZeroRotation() {
        val head = HeadTransform(pitch = 0f, yaw = 0f, roll = 0f)

        val message = VmcMessageBuilder.buildHeadBoneMessage(head)

        // Identity quaternion: (0, 0, 0, 1)
        val qx = (message.arguments[4] as OscArgument.FloatArg).value
        val qy = (message.arguments[5] as OscArgument.FloatArg).value
        val qz = (message.arguments[6] as OscArgument.FloatArg).value
        val qw = (message.arguments[7] as OscArgument.FloatArg).value
        assertEquals(0f, qx, "qx should be 0 for identity rotation")
        assertEquals(0f, qy, "qy should be 0 for identity rotation")
        assertEquals(0f, qz, "qz should be 0 for identity rotation")
        assertEquals(1f, qw, "qw should be 1 for identity rotation")
    }

    @Test
    fun buildStatusMessage() {
        val message = VmcMessageBuilder.buildStatusMessage(loaded = 1, calibrationState = 2, calibrationMode = 0)

        assertEquals(VmcAddress.OK, message.address)
        assertEquals(3, message.arguments.size)
        assertEquals(1, (message.arguments[0] as OscArgument.IntArg).value)
        assertEquals(2, (message.arguments[1] as OscArgument.IntArg).value)
        assertEquals(0, (message.arguments[2] as OscArgument.IntArg).value)
    }

    @Test
    fun buildTimestampMessage() {
        val message = VmcMessageBuilder.buildTimestampMessage(1.5f)

        assertEquals(VmcAddress.TIME, message.address)
        assertEquals(1, message.arguments.size)
        assertEquals(1.5f, (message.arguments[0] as OscArgument.FloatArg).value)
    }
}
