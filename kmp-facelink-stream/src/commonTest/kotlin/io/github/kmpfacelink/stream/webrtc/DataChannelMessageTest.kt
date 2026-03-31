package io.github.kmpfacelink.stream.webrtc

import io.github.kmpfacelink.model.BlendShape
import io.github.kmpfacelink.stream.FaceFrame
import io.github.kmpfacelink.stream.HandFrame
import io.github.kmpfacelink.stream.HeadPose
import io.github.kmpfacelink.stream.StreamFrame
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class DataChannelMessageTest {

    @Test
    fun faceOnlyRoundTrip() {
        val blendShapes = BlendShape.entries.associate { it.arKitName to 0f }
            .toMutableMap()
            .apply { put("jawOpen", 0.8f) }

        val frame = StreamFrame(
            timestampMs = 12345L,
            face = FaceFrame(
                blendShapes = blendShapes,
                head = HeadPose(1f, 2f, 3f, 0.1f, 0.2f, 0.3f),
            ),
        )

        val encoded = DataChannelMessage.encode(frame, sequenceNumber = 42, sendTimeNs = 999_000L)
        val decoded = DataChannelMessage.decode(encoded)

        assertEquals(42, decoded.sequenceNumber)
        assertEquals(999_000L, decoded.sendTimeNs)
        assertEquals(12345L, decoded.frame.timestampMs)
        assertNotNull(decoded.frame.face)
        assertNull(decoded.frame.hands)
        assertEquals(0.8f, decoded.frame.face!!.blendShapes["jawOpen"])
        assertEquals(1f, decoded.frame.face!!.head.pitch)
    }

    @Test
    fun handsOnlyRoundTrip() {
        val landmarks = List(63) { it.toFloat() * 0.01f }
        val frame = StreamFrame(
            timestampMs = 500L,
            hands = listOf(HandFrame(side = "left", landmarks = landmarks)),
        )

        val encoded = DataChannelMessage.encode(frame, sequenceNumber = 100, sendTimeNs = 1_000_000L)
        val decoded = DataChannelMessage.decode(encoded)

        assertEquals(100, decoded.sequenceNumber)
        assertEquals(1_000_000L, decoded.sendTimeNs)
        assertNull(decoded.frame.face)
        assertNotNull(decoded.frame.hands)
        assertEquals(1, decoded.frame.hands!!.size)
        assertEquals("left", decoded.frame.hands!![0].side)
    }

    @Test
    fun emptyFrameRoundTrip() {
        val frame = StreamFrame(timestampMs = 0L)
        val encoded = DataChannelMessage.encode(frame, sequenceNumber = 0, sendTimeNs = 0L)
        val decoded = DataChannelMessage.decode(encoded)

        assertEquals(0, decoded.sequenceNumber)
        assertEquals(0L, decoded.sendTimeNs)
        assertEquals(0L, decoded.frame.timestampMs)
        assertNull(decoded.frame.face)
        assertNull(decoded.frame.hands)
    }

    @Test
    fun envelopeAddsFixedOverhead() {
        val frame = StreamFrame(timestampMs = 0L)
        val encoded = DataChannelMessage.encode(frame, sequenceNumber = 0, sendTimeNs = 0L)
        // Envelope(14) + BinaryFrame header(10) = 24 bytes for empty frame
        assertEquals(24, encoded.size)
    }

    @Test
    fun faceOnlyMessageSize() {
        val blendShapes = BlendShape.entries.associate { it.arKitName to 0f }
        val frame = StreamFrame(
            timestampMs = 1L,
            face = FaceFrame(
                blendShapes = blendShapes,
                head = HeadPose(0f, 0f, 0f),
            ),
        )
        val encoded = DataChannelMessage.encode(frame, sequenceNumber = 1, sendTimeNs = 1L)
        // Envelope(14) + BinaryFrame face-only(242) = 256 bytes
        assertEquals(256, encoded.size)
    }

    @Test
    fun rejectsTooShortMessage() {
        assertFailsWith<IllegalArgumentException> {
            DataChannelMessage.decode(ByteArray(5))
        }
    }

    @Test
    fun rejectsInvalidMagicByte() {
        val data = ByteArray(24)
        data[0] = 0x00 // wrong magic
        assertFailsWith<IllegalArgumentException> {
            DataChannelMessage.decode(data)
        }
    }

    @Test
    fun rejectsUnsupportedVersion() {
        val data = ByteArray(24)
        data[0] = DataChannelMessage.MAGIC
        data[1] = 99 // unsupported version
        assertFailsWith<IllegalArgumentException> {
            DataChannelMessage.decode(data)
        }
    }

    @Test
    fun largeSequenceNumberRoundTrip() {
        val frame = StreamFrame(timestampMs = 1L)
        val encoded = DataChannelMessage.encode(
            frame,
            sequenceNumber = Int.MAX_VALUE,
            sendTimeNs = Long.MAX_VALUE,
        )
        val decoded = DataChannelMessage.decode(encoded)

        assertEquals(Int.MAX_VALUE, decoded.sequenceNumber)
        assertEquals(Long.MAX_VALUE, decoded.sendTimeNs)
    }
}
