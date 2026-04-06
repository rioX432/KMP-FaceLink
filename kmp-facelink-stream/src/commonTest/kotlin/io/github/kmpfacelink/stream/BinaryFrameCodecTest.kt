package io.github.kmpfacelink.stream

import io.github.kmpfacelink.model.BlendShape
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class BinaryFrameCodecTest {

    // region Round-trip tests

    @Test
    fun encodeFaceOnlyRoundTrip() {
        val blendShapes = BlendShape.entries.associate { it.arKitName to 0f }
            .toMutableMap()
            .apply {
                put("jawOpen", 0.75f)
                put("eyeBlinkLeft", 0.5f)
            }

        val original = StreamFrame(
            timestampMs = 42L,
            face = FaceFrame(
                blendShapes = blendShapes,
                head = HeadPose(
                    pitch = 10f,
                    yaw = -5f,
                    roll = 3f,
                    x = 0.1f,
                    y = 0.2f,
                    z = -0.3f,
                ),
            ),
        )

        val encoded = BinaryFrameCodec.encode(original)
        val decoded = BinaryFrameCodec.decode(encoded)

        assertEquals(original.timestampMs, decoded.timestampMs)
        assertNotNull(decoded.face)
        assertNull(decoded.hands)

        val face = decoded.face!!
        assertEquals(0.75f, face.blendShapes["jawOpen"])
        assertEquals(0.5f, face.blendShapes["eyeBlinkLeft"])
        assertEquals(10f, face.head.pitch)
        assertEquals(-5f, face.head.yaw)
        assertEquals(3f, face.head.roll)
        assertEquals(0.1f, face.head.x)
        assertEquals(0.2f, face.head.y)
        assertEquals(-0.3f, face.head.z)
    }

    @Test
    fun encodeHandsOnlyRoundTrip() {
        val landmarks = List(63) { it.toFloat() * 0.01f }
        val original = StreamFrame(
            timestampMs = 100L,
            hands = listOf(
                HandFrame(side = "left", landmarks = landmarks),
                HandFrame(side = "right", landmarks = landmarks),
            ),
        )

        val encoded = BinaryFrameCodec.encode(original)
        val decoded = BinaryFrameCodec.decode(encoded)

        assertEquals(100L, decoded.timestampMs)
        assertNull(decoded.face)
        assertNotNull(decoded.hands)
        assertEquals(2, decoded.hands!!.size)
        assertEquals("left", decoded.hands!![0].side)
        assertEquals("right", decoded.hands!![1].side)
        assertEquals(63, decoded.hands!![0].landmarks.size)
        for (i in landmarks.indices) {
            assertEquals(landmarks[i], decoded.hands!![0].landmarks[i])
        }
    }

    @Test
    fun encodeFaceAndHandsRoundTrip() {
        val blendShapes = BlendShape.entries.associate { it.arKitName to 0.5f }
        val landmarks = List(63) { 0.1f }
        val original = StreamFrame(
            timestampMs = 200L,
            face = FaceFrame(
                blendShapes = blendShapes,
                head = HeadPose(1f, 2f, 3f, 4f, 5f, 6f),
            ),
            hands = listOf(HandFrame(side = "right", landmarks = landmarks)),
        )

        val encoded = BinaryFrameCodec.encode(original)
        val decoded = BinaryFrameCodec.decode(encoded)

        assertEquals(200L, decoded.timestampMs)
        assertNotNull(decoded.face)
        assertNotNull(decoded.hands)
        assertEquals(1, decoded.hands!!.size)
        assertEquals("right", decoded.hands!![0].side)
    }

    // endregion

    // region Empty / minimal data

    @Test
    fun emptyFrameRoundTrip() {
        val original = StreamFrame(timestampMs = 0L)
        val encoded = BinaryFrameCodec.encode(original)
        val decoded = BinaryFrameCodec.decode(encoded)

        assertEquals(0L, decoded.timestampMs)
        assertNull(decoded.face)
        assertNull(decoded.hands)
        // Header only: version(1) + flags(1) + timestamp(8) = 10 bytes
        assertEquals(10, encoded.size)
    }

    @Test
    fun emptyHandsListTreatedAsNoHands() {
        // An empty hands list should produce no hands block (same as null)
        val original = StreamFrame(timestampMs = 1L, hands = emptyList())
        val encoded = BinaryFrameCodec.encode(original)
        val decoded = BinaryFrameCodec.decode(encoded)

        assertNull(decoded.hands)
        // Should be header-only size
        assertEquals(10, encoded.size)
    }

    // endregion

    // region Size verification

    @Test
    fun faceOnlyFrameSize() {
        val blendShapes = BlendShape.entries.associate { it.arKitName to 0f }
        val frame = StreamFrame(
            timestampMs = 1L,
            face = FaceFrame(
                blendShapes = blendShapes,
                head = HeadPose(0f, 0f, 0f),
            ),
        )
        val encoded = BinaryFrameCodec.encode(frame)
        // Header(10) + 52 floats(208) + 6 floats(24) = 242
        assertEquals(242, encoded.size)
    }

    @Test
    fun singleHandFrameSize() {
        val landmarks = List(63) { 0f }
        val frame = StreamFrame(
            timestampMs = 1L,
            hands = listOf(HandFrame(side = "left", landmarks = landmarks)),
        )
        val encoded = BinaryFrameCodec.encode(frame)
        // Header(10) + handCount(1) + side(1) + 63 floats(252) = 264
        assertEquals(264, encoded.size)
    }

    // endregion

    // region Edge cases

    @Test
    fun maxTimestampRoundTrip() {
        val original = StreamFrame(timestampMs = Long.MAX_VALUE)
        val decoded = BinaryFrameCodec.decode(BinaryFrameCodec.encode(original))
        assertEquals(Long.MAX_VALUE, decoded.timestampMs)
    }

    @Test
    fun negativeTimestampRoundTrip() {
        val original = StreamFrame(timestampMs = -1L)
        val decoded = BinaryFrameCodec.decode(BinaryFrameCodec.encode(original))
        assertEquals(-1L, decoded.timestampMs)
    }

    @Test
    fun maxBlendShapeValuesRoundTrip() {
        // All blend shapes at 1.0f (maximum ARKit value)
        val blendShapes = BlendShape.entries.associate { it.arKitName to 1.0f }
        val original = StreamFrame(
            timestampMs = 1L,
            face = FaceFrame(
                blendShapes = blendShapes,
                head = HeadPose(0f, 0f, 0f),
            ),
        )
        val decoded = BinaryFrameCodec.decode(BinaryFrameCodec.encode(original))
        assertNotNull(decoded.face)
        for (shape in BlendShape.entries) {
            assertEquals(1.0f, decoded.face!!.blendShapes[shape.arKitName])
        }
    }

    @Test
    fun zeroBlendShapeValuesRoundTrip() {
        val blendShapes = BlendShape.entries.associate { it.arKitName to 0f }
        val original = StreamFrame(
            timestampMs = 1L,
            face = FaceFrame(
                blendShapes = blendShapes,
                head = HeadPose(0f, 0f, 0f),
            ),
        )
        val decoded = BinaryFrameCodec.decode(BinaryFrameCodec.encode(original))
        assertNotNull(decoded.face)
        for (shape in BlendShape.entries) {
            assertEquals(0f, decoded.face!!.blendShapes[shape.arKitName])
        }
    }

    @Test
    fun unknownHandSideRoundTrip() {
        // "unknown" side should survive encode/decode
        val landmarks = List(63) { 0f }
        val original = StreamFrame(
            timestampMs = 1L,
            hands = listOf(HandFrame(side = "unknown", landmarks = landmarks)),
        )
        val decoded = BinaryFrameCodec.decode(BinaryFrameCodec.encode(original))
        assertNotNull(decoded.hands)
        assertEquals("unknown", decoded.hands!![0].side)
    }

    @Test
    fun unrecognizedHandSideEncodedAsUnknown() {
        // Any side string that is not "left" or "right" maps to "unknown" after decode
        val landmarks = List(63) { 0f }
        val original = StreamFrame(
            timestampMs = 1L,
            hands = listOf(HandFrame(side = "both", landmarks = landmarks)),
        )
        val decoded = BinaryFrameCodec.decode(BinaryFrameCodec.encode(original))
        assertNotNull(decoded.hands)
        assertEquals("unknown", decoded.hands!![0].side)
    }

    @Test
    fun negativeHeadPoseValuesRoundTrip() {
        val blendShapes = BlendShape.entries.associate { it.arKitName to 0f }
        val original = StreamFrame(
            timestampMs = 1L,
            face = FaceFrame(
                blendShapes = blendShapes,
                head = HeadPose(
                    pitch = -90f,
                    yaw = -180f,
                    roll = -45f,
                    x = -1.5f,
                    y = -2.5f,
                    z = -3.5f,
                ),
            ),
        )
        val decoded = BinaryFrameCodec.decode(BinaryFrameCodec.encode(original))
        assertNotNull(decoded.face)
        val head = decoded.face!!.head
        assertEquals(-90f, head.pitch)
        assertEquals(-180f, head.yaw)
        assertEquals(-45f, head.roll)
        assertEquals(-1.5f, head.x)
        assertEquals(-2.5f, head.y)
        assertEquals(-3.5f, head.z)
    }

    // endregion

    // region Byte helper tests

    @Test
    fun writeReadFloatRoundTrip() {
        val buffer = ByteArray(4)
        val values = listOf(0f, 1f, -1f, Float.MAX_VALUE, Float.MIN_VALUE, Float.NaN, Float.POSITIVE_INFINITY)
        for (value in values) {
            writeFloat(buffer, 0, value)
            val result = readFloat(buffer, 0)
            assertEquals(value.toRawBits(), result.toRawBits(), "Float round-trip failed for $value")
        }
    }

    @Test
    fun writeReadIntRoundTrip() {
        val buffer = ByteArray(4)
        val values = listOf(0, 1, -1, Int.MAX_VALUE, Int.MIN_VALUE, 0xFF, 0x7F800000)
        for (value in values) {
            writeInt(buffer, 0, value)
            assertEquals(value, readInt(buffer, 0), "Int round-trip failed for $value")
        }
    }

    @Test
    fun writeReadLongRoundTrip() {
        val buffer = ByteArray(8)
        val values = listOf(0L, 1L, -1L, Long.MAX_VALUE, Long.MIN_VALUE, 0xFFFFFFFFL)
        for (value in values) {
            writeLong(buffer, 0, value)
            assertEquals(value, readLong(buffer, 0), "Long round-trip failed for $value")
        }
    }

    @Test
    fun writeIntLittleEndianOrder() {
        // Verify bytes are written in little-endian order
        val buffer = ByteArray(4)
        writeInt(buffer, 0, 0x01020304)
        assertEquals(0x04.toByte(), buffer[0])
        assertEquals(0x03.toByte(), buffer[1])
        assertEquals(0x02.toByte(), buffer[2])
        assertEquals(0x01.toByte(), buffer[3])
    }

    @Test
    fun writeLongLittleEndianOrder() {
        // Verify bytes are written in little-endian order for Long
        val buffer = ByteArray(8)
        writeLong(buffer, 0, 0x0102030405060708L)
        assertEquals(0x08.toByte(), buffer[0])
        assertEquals(0x07.toByte(), buffer[1])
        assertEquals(0x06.toByte(), buffer[2])
        assertEquals(0x05.toByte(), buffer[3])
        assertEquals(0x04.toByte(), buffer[4])
        assertEquals(0x03.toByte(), buffer[5])
        assertEquals(0x02.toByte(), buffer[6])
        assertEquals(0x01.toByte(), buffer[7])
    }

    // endregion
}
