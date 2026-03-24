package io.github.kmpfacelink.vmc.osc

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class OscEncoderDecoderTest {

    @Test
    fun encodeAndDecodeSimpleMessage() {
        val original = OscMessage(
            address = "/test/path",
            arguments = listOf(
                OscArgument.IntArg(42),
                OscArgument.FloatArg(3.14f),
                OscArgument.StringArg("hello"),
            ),
        )

        val encoded = OscEncoder.encode(original)
        val decoded = OscDecoder.decode(encoded)

        assertEquals(1, decoded.size)
        val result = decoded[0]
        assertEquals("/test/path", result.address)
        assertEquals(3, result.arguments.size)
        assertEquals(42, (result.arguments[0] as OscArgument.IntArg).value)
        assertEquals(3.14f, (result.arguments[1] as OscArgument.FloatArg).value)
        assertEquals("hello", (result.arguments[2] as OscArgument.StringArg).value)
    }

    @Test
    fun encodeAndDecodeEmptyArguments() {
        val original = OscMessage(address = "/VMC/Ext/Blend/Apply", arguments = emptyList())

        val encoded = OscEncoder.encode(original)
        val decoded = OscDecoder.decode(encoded)

        assertEquals(1, decoded.size)
        assertEquals("/VMC/Ext/Blend/Apply", decoded[0].address)
        assertTrue(decoded[0].arguments.isEmpty())
    }

    @Test
    fun encodeAndDecodeBlendValMessage() {
        val original = OscMessage(
            address = "/VMC/Ext/Blend/Val",
            arguments = listOf(
                OscArgument.StringArg("eyeBlinkLeft"),
                OscArgument.FloatArg(0.75f),
            ),
        )

        val encoded = OscEncoder.encode(original)
        val decoded = OscDecoder.decode(encoded)

        assertEquals(1, decoded.size)
        val msg = decoded[0]
        assertEquals("/VMC/Ext/Blend/Val", msg.address)
        assertEquals("eyeBlinkLeft", (msg.arguments[0] as OscArgument.StringArg).value)
        assertEquals(0.75f, (msg.arguments[1] as OscArgument.FloatArg).value)
    }

    @Test
    fun encodeAndDecodeBonePosMessage() {
        val original = OscMessage(
            address = "/VMC/Ext/Bone/Pos",
            arguments = listOf(
                OscArgument.StringArg("Head"),
                OscArgument.FloatArg(0.1f),
                OscArgument.FloatArg(0.2f),
                OscArgument.FloatArg(0.3f),
                OscArgument.FloatArg(0.0f),
                OscArgument.FloatArg(0.0f),
                OscArgument.FloatArg(0.0f),
                OscArgument.FloatArg(1.0f),
            ),
        )

        val encoded = OscEncoder.encode(original)
        val decoded = OscDecoder.decode(encoded)

        assertEquals(1, decoded.size)
        val msg = decoded[0]
        assertEquals("/VMC/Ext/Bone/Pos", msg.address)
        assertEquals(8, msg.arguments.size)
        assertEquals("Head", (msg.arguments[0] as OscArgument.StringArg).value)
        assertEquals(0.1f, (msg.arguments[1] as OscArgument.FloatArg).value)
        assertEquals(1.0f, (msg.arguments[7] as OscArgument.FloatArg).value)
    }

    @Test
    fun encodeAndDecodeBundle() {
        val msg1 = OscMessage(
            address = "/VMC/Ext/Blend/Val",
            arguments = listOf(
                OscArgument.StringArg("jawOpen"),
                OscArgument.FloatArg(0.5f),
            ),
        )
        val msg2 = OscMessage(
            address = "/VMC/Ext/Blend/Apply",
            arguments = emptyList(),
        )

        val bundleBytes = OscEncoder.encodeBundle(listOf(msg1, msg2))
        val decoded = OscDecoder.decode(bundleBytes)

        assertEquals(2, decoded.size)
        assertEquals("/VMC/Ext/Blend/Val", decoded[0].address)
        assertEquals("/VMC/Ext/Blend/Apply", decoded[1].address)
    }

    @Test
    fun allEncodedMessagesAre4ByteAligned() {
        val messages = listOf(
            OscMessage("/a", listOf(OscArgument.IntArg(1))),
            OscMessage("/ab", listOf(OscArgument.FloatArg(1.0f))),
            OscMessage("/abc", listOf(OscArgument.StringArg("x"))),
            OscMessage("/abcd", emptyList()),
        )

        for (msg in messages) {
            val encoded = OscEncoder.encode(msg)
            assertEquals(
                0,
                encoded.size % 4,
                "Message ${msg.address} encoded to ${encoded.size} bytes (not 4-byte aligned)",
            )
        }
    }

    @Test
    fun unsupportedTypeTagsAreSkippedGracefully() {
        // Build a raw OSC message with type tags ",Tif" (True + int + float)
        // The decoder should skip the unsupported 'T' tag (zero-size) and decode the rest
        val buffer = OscByteBuffer()
        writeString(buffer, "/test")
        writeString(buffer, ",Tif")
        // 'T' has no data bytes; 'i' reads 4 bytes; 'f' reads 4 bytes
        writeInt(buffer, 99)
        writeFloat(buffer, 1.5f)
        val raw = buffer.toByteArray()

        val decoded = OscDecoder.decode(raw)

        assertEquals(1, decoded.size)
        val msg = decoded[0]
        assertEquals("/test", msg.address)
        // 'T' is skipped, so we get 2 arguments: int, float
        assertEquals(2, msg.arguments.size)
        assertEquals(99, (msg.arguments[0] as OscArgument.IntArg).value)
        assertEquals(1.5f, (msg.arguments[1] as OscArgument.FloatArg).value)
    }

    @Test
    fun unknownTypeTagDoesNotCrash() {
        // Build a message where the only argument has an unknown tag 'X'
        // The decoder should return an empty argument list without throwing
        val buffer = OscByteBuffer()
        writeString(buffer, "/unknown")
        writeString(buffer, ",X")
        val raw = buffer.toByteArray()

        val decoded = OscDecoder.decode(raw)

        assertEquals(1, decoded.size)
        assertEquals("/unknown", decoded[0].address)
        assertTrue(decoded[0].arguments.isEmpty())
    }

    @Test
    fun paddingCalculation() {
        // Content length 0: total with null = 1, pad to 4 -> 4 null bytes
        assertEquals(4, oscStringPadding(0))
        // Content length 1: total with null = 2, pad to 4 -> 3 null bytes
        assertEquals(3, oscStringPadding(1))
        // Content length 2: total with null = 3, pad to 4 -> 2 null bytes
        assertEquals(2, oscStringPadding(2))
        // Content length 3: total with null = 4, already aligned -> 1 null byte
        assertEquals(1, oscStringPadding(3))
        // Content length 4: total with null = 5, pad to 8 -> 4 null bytes
        assertEquals(4, oscStringPadding(4))
        // Content length 7 ("#bundle"): total with null = 8, aligned -> 1 null byte
        assertEquals(1, oscStringPadding(7))
    }
}
