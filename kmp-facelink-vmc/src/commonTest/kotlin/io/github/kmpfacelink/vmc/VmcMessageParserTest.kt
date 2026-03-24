package io.github.kmpfacelink.vmc

import io.github.kmpfacelink.model.BlendShape
import io.github.kmpfacelink.vmc.osc.OscArgument
import io.github.kmpfacelink.vmc.osc.OscMessage
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class VmcMessageParserTest {

    @Test
    fun parseBlendValMessages() {
        val messages = listOf(
            OscMessage(
                VmcAddress.BLEND_VAL,
                listOf(OscArgument.StringArg("eyeBlinkLeft"), OscArgument.FloatArg(0.5f)),
            ),
            OscMessage(
                VmcAddress.BLEND_VAL,
                listOf(OscArgument.StringArg("jawOpen"), OscArgument.FloatArg(0.8f)),
            ),
            OscMessage(VmcAddress.BLEND_APPLY, emptyList()),
        )

        val result = VmcMessageParser.parse(messages)

        assertEquals(2, result.blendShapes.size)
        assertEquals(0.5f, result.blendShapes[BlendShape.EYE_BLINK_LEFT])
        assertEquals(0.8f, result.blendShapes[BlendShape.JAW_OPEN])
    }

    @Test
    fun parseHeadBonePosMessage() {
        val messages = listOf(
            OscMessage(
                VmcAddress.BONE_POS,
                listOf(
                    OscArgument.StringArg("Head"),
                    OscArgument.FloatArg(0.1f),
                    OscArgument.FloatArg(0.2f),
                    OscArgument.FloatArg(0.3f),
                    OscArgument.FloatArg(0.0f),
                    OscArgument.FloatArg(0.0f),
                    OscArgument.FloatArg(0.0f),
                    OscArgument.FloatArg(1.0f),
                ),
            ),
        )

        val result = VmcMessageParser.parse(messages)

        assertNotNull(result.headPosition)
        assertNotNull(result.headRotation)
        assertEquals(0.1f, result.headPosition!![0])
        assertEquals(0.2f, result.headPosition!![1])
        assertEquals(0.3f, result.headPosition!![2])
        assertEquals(1.0f, result.headRotation!![3])
    }

    @Test
    fun parseIgnoresNonHeadBones() {
        val messages = listOf(
            OscMessage(
                VmcAddress.BONE_POS,
                listOf(
                    OscArgument.StringArg("LeftHand"),
                    OscArgument.FloatArg(0f),
                    OscArgument.FloatArg(0f),
                    OscArgument.FloatArg(0f),
                    OscArgument.FloatArg(0f),
                    OscArgument.FloatArg(0f),
                    OscArgument.FloatArg(0f),
                    OscArgument.FloatArg(1f),
                ),
            ),
        )

        val result = VmcMessageParser.parse(messages)

        assertNull(result.headPosition)
        assertNull(result.headRotation)
    }

    @Test
    fun parseOkStatusLoaded() {
        val messages = listOf(
            OscMessage(VmcAddress.OK, listOf(OscArgument.IntArg(1))),
        )

        val result = VmcMessageParser.parse(messages)

        assertTrue(result.isLoaded)
    }

    @Test
    fun parseTimestamp() {
        val messages = listOf(
            OscMessage(VmcAddress.TIME, listOf(OscArgument.FloatArg(2.5f))),
        )

        val result = VmcMessageParser.parse(messages)

        assertEquals(2.5f, result.senderTime)
    }

    @Test
    fun parseIgnoresUnknownAddresses() {
        val messages = listOf(
            OscMessage("/VMC/Ext/Unknown", listOf(OscArgument.IntArg(99))),
        )

        val result = VmcMessageParser.parse(messages)

        assertTrue(result.blendShapes.isEmpty())
        assertNull(result.headPosition)
    }

    @Test
    fun parseIgnoresUnknownBlendShapeNames() {
        val messages = listOf(
            OscMessage(
                VmcAddress.BLEND_VAL,
                listOf(OscArgument.StringArg("unknownShape"), OscArgument.FloatArg(0.5f)),
            ),
        )

        val result = VmcMessageParser.parse(messages)

        assertTrue(result.blendShapes.isEmpty())
    }
}
