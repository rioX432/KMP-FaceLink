package io.github.kmpfacelink.stream

import io.github.kmpfacelink.model.BlendShape
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * Error-scenario and boundary tests for [BinaryFrameCodec].
 *
 * Complements [BinaryFrameCodecTest] which covers happy-path round-trips.
 * These tests document how the codec behaves (or throws) on malformed input,
 * truncated frames, and boundary conditions.
 */
class BinaryFrameCodecErrorTest {

    // region Truncated frames

    @Test
    fun decodeEmptyByteArrayThrows() {
        // Less than the minimum 10-byte header — must throw
        assertFailsWith<IndexOutOfBoundsException> {
            BinaryFrameCodec.decode(ByteArray(0))
        }
    }

    @Test
    fun decodeSingleByteThrows() {
        // Only version byte — missing flags and timestamp
        assertFailsWith<IndexOutOfBoundsException> {
            BinaryFrameCodec.decode(ByteArray(1) { 1 })
        }
    }

    @Test
    fun decodeHeaderTruncatedAt9BytesThrows() {
        // 9 bytes: version + flags + 7 of 8 timestamp bytes — incomplete header
        assertFailsWith<IndexOutOfBoundsException> {
            BinaryFrameCodec.decode(ByteArray(9))
        }
    }

    @Test
    fun decodeFaceFrameTruncatedMidBlendShapesThrows() {
        // Flags = 0x01 (has face), but payload is cut off before the full face block.
        // Face block needs 232 bytes (52+6 floats × 4), so 10 + 100 is truncated.
        val data = ByteArray(110)
        data[0] = 1 // version
        data[1] = 1 // FLAG_HAS_FACE
        // timestamp bytes at [2..9] left as 0
        assertFailsWith<IndexOutOfBoundsException> {
            BinaryFrameCodec.decode(data)
        }
    }

    @Test
    fun decodeFaceFrameTruncatedAtHeadPoseThrows() {
        // Provide all blend shape bytes but cut the head pose section
        // Header(10) + 52 floats(208) = 218 bytes; head pose needs 24 more.
        val data = ByteArray(218)
        data[0] = 1 // version
        data[1] = 1 // FLAG_HAS_FACE
        assertFailsWith<IndexOutOfBoundsException> {
            BinaryFrameCodec.decode(data)
        }
    }

    @Test
    fun decodeHandsFrameTruncatedMidLandmarksThrows() {
        // Header(10) + handCount(1) + side(1) = 12 bytes, then 63 floats = 252 bytes needed.
        // Supply only a partial landmark payload.
        val data = ByteArray(50)
        data[0] = 1 // version
        data[1] = 2 // FLAG_HAS_HANDS
        // handCount = 1, side = 0 (left)
        data[10] = 1 // handCount
        data[11] = 0 // side = left
        assertFailsWith<IndexOutOfBoundsException> {
            BinaryFrameCodec.decode(data)
        }
    }

    @Test
    fun decodeHandsFrameMissingHandCountByteThrows() {
        // Exactly 10 bytes (valid header) with FLAG_HAS_HANDS set but no handCount byte
        val data = ByteArray(10)
        data[0] = 1 // version
        data[1] = 2 // FLAG_HAS_HANDS
        assertFailsWith<IndexOutOfBoundsException> {
            BinaryFrameCodec.decode(data)
        }
    }

    // endregion

    // region Version byte (currently skipped — no version check)

    @Test
    fun decodeVersionZeroIsAccepted() {
        // The codec skips the version byte entirely; version 0 decodes without error.
        val original = StreamFrame(timestampMs = 7L)
        val encoded = BinaryFrameCodec.encode(original)
        encoded[0] = 0 // overwrite version with 0
        val decoded = BinaryFrameCodec.decode(encoded)
        assertEquals(7L, decoded.timestampMs)
        assertNull(decoded.face)
        assertNull(decoded.hands)
    }

    @Test
    fun decodeVersionFfIsAccepted() {
        // Version 0xFF is also silently accepted; codec only uses flags + payload.
        val original = StreamFrame(timestampMs = 99L)
        val encoded = BinaryFrameCodec.encode(original)
        encoded[0] = 0xFF.toByte()
        val decoded = BinaryFrameCodec.decode(encoded)
        assertEquals(99L, decoded.timestampMs)
    }

    @Test
    fun decodeWrongVersionWithFaceDataStillDecodes() {
        // Verify that even with a "wrong" version the face payload is still decoded,
        // since version validation is not implemented.
        val blendShapes = BlendShape.entries.associate { it.arKitName to 0.5f }
        val original = StreamFrame(
            timestampMs = 1L,
            face = FaceFrame(blendShapes = blendShapes, head = HeadPose(1f, 2f, 3f)),
        )
        val encoded = BinaryFrameCodec.encode(original)
        encoded[0] = 42 // arbitrary non-1 version
        val decoded = BinaryFrameCodec.decode(encoded)
        assertNotNull(decoded.face)
        assertEquals(0.5f, decoded.face!!.blendShapes[BlendShape.JAW_OPEN.arKitName])
    }

    // endregion

    // region Corrupt flags byte

    @Test
    fun decodeCorruptFlagsBothBitsSetWithNoPayloadThrows() {
        // Flags = 0x03 means both face and hands present, but buffer only has the header.
        // Attempting to read the face block will go out of bounds.
        val data = ByteArray(10)
        data[0] = 1 // version
        data[1] = 3 // FLAG_HAS_FACE | FLAG_HAS_HANDS
        assertFailsWith<IndexOutOfBoundsException> {
            BinaryFrameCodec.decode(data)
        }
    }

    @Test
    fun decodeUnknownFlagBitsIgnored() {
        // Upper bits of the flags byte are not recognised; codec only checks bits 0 and 1.
        // A valid header-only frame with flags = 0xFC (bits 2-7 set, bits 0-1 clear)
        // should decode successfully as an empty frame.
        val original = StreamFrame(timestampMs = 55L)
        val encoded = BinaryFrameCodec.encode(original)
        encoded[1] = (encoded[1].toInt() or 0xFC).toByte() // set unknown flag bits
        val decoded = BinaryFrameCodec.decode(encoded)
        assertEquals(55L, decoded.timestampMs)
        assertNull(decoded.face)
        assertNull(decoded.hands)
    }

    // endregion

    // region Maximum frame size boundary

    @Test
    fun encodeTwoHandsFrameSize() {
        // Header(10) + handCount(1) + 2 × (side(1) + 63 floats(252)) = 517
        val landmarks = List(63) { 0f }
        val frame = StreamFrame(
            timestampMs = 1L,
            hands = listOf(
                HandFrame(side = "left", landmarks = landmarks),
                HandFrame(side = "right", landmarks = landmarks),
            ),
        )
        val encoded = BinaryFrameCodec.encode(frame)
        assertEquals(517, encoded.size)
    }

    @Test
    fun encodeFaceAndTwoHandsFrameSize() {
        // Header(10) + face(232) + handCount(1) + 2 × (1+252) = 497
        val blendShapes = BlendShape.entries.associate { it.arKitName to 0f }
        val landmarks = List(63) { 0f }
        val frame = StreamFrame(
            timestampMs = 1L,
            face = FaceFrame(blendShapes = blendShapes, head = HeadPose(0f, 0f, 0f)),
            hands = listOf(
                HandFrame(side = "left", landmarks = landmarks),
                HandFrame(side = "right", landmarks = landmarks),
            ),
        )
        val encoded = BinaryFrameCodec.encode(frame)
        // Header(10) + FaceBlock(232) + handCount(1) + 2*(1+252) = 10+232+1+506 = 749
        assertEquals(749, encoded.size)
    }

    @Test
    fun encodeDecodeMinTimestamp() {
        // Long.MIN_VALUE is the smallest possible timestamp
        val original = StreamFrame(timestampMs = Long.MIN_VALUE)
        val decoded = BinaryFrameCodec.decode(BinaryFrameCodec.encode(original))
        assertEquals(Long.MIN_VALUE, decoded.timestampMs)
    }

    @Test
    fun encodeDecodeSpecialFloatValues() {
        // Float.NaN, Infinity, and -Infinity must survive encode/decode via raw bits
        val blendShapes = BlendShape.entries.associate { it.arKitName to 0f }.toMutableMap()
        blendShapes[BlendShape.JAW_OPEN.arKitName] = Float.NaN
        blendShapes[BlendShape.EYE_BLINK_LEFT.arKitName] = Float.POSITIVE_INFINITY
        blendShapes[BlendShape.EYE_BLINK_RIGHT.arKitName] = Float.NEGATIVE_INFINITY
        val original = StreamFrame(
            timestampMs = 1L,
            face = FaceFrame(blendShapes = blendShapes, head = HeadPose(Float.NaN, Float.POSITIVE_INFINITY, 0f)),
        )
        val decoded = BinaryFrameCodec.decode(BinaryFrameCodec.encode(original))
        assertNotNull(decoded.face)
        // NaN != NaN in IEEE 754, so compare raw bits
        assertEquals(
            Float.NaN.toRawBits(),
            decoded.face!!.blendShapes[BlendShape.JAW_OPEN.arKitName]!!.toRawBits(),
        )
        assertEquals(Float.POSITIVE_INFINITY, decoded.face!!.blendShapes[BlendShape.EYE_BLINK_LEFT.arKitName])
        assertEquals(Float.NEGATIVE_INFINITY, decoded.face!!.blendShapes[BlendShape.EYE_BLINK_RIGHT.arKitName])
        assertEquals(Float.NaN.toRawBits(), decoded.face!!.head.pitch.toRawBits())
        assertEquals(Float.POSITIVE_INFINITY, decoded.face!!.head.yaw)
    }

    // endregion

    // region Empty payloads

    @Test
    fun decodeHeaderOnlyFrameWithFaceFlagClearedIsEmpty() {
        // Encode a real frame then zero out the flags byte — result must be empty frame
        val blendShapes = BlendShape.entries.associate { it.arKitName to 1f }
        val original = StreamFrame(
            timestampMs = 300L,
            face = FaceFrame(blendShapes = blendShapes, head = HeadPose(0f, 0f, 0f)),
        )
        val encoded = BinaryFrameCodec.encode(original)
        encoded[1] = 0 // clear all flags — signal no face, no hands
        // The payload bytes remain, but decode should read only the header-only path
        val decoded = BinaryFrameCodec.decode(encoded)
        assertEquals(300L, decoded.timestampMs)
        assertNull(decoded.face)
        assertNull(decoded.hands)
    }

    @Test
    fun decodeZeroHandCount() {
        // Manually craft a frame: FLAG_HAS_HANDS set but handCount = 0.
        // Codec should return an empty hands list.
        val data = ByteArray(11)
        data[0] = 1 // version
        data[1] = 2 // FLAG_HAS_HANDS
        // timestamp at [2..9] = 0L
        data[10] = 0 // handCount = 0
        val decoded = BinaryFrameCodec.decode(data)
        assertNotNull(decoded.hands)
        assertEquals(0, decoded.hands!!.size)
    }

    // endregion

    // region Corrupt payload bytes (valid size, wrong content)

    @Test
    fun decodeGarbagePayloadDoesNotCrashOnFaceBlock() {
        // A 242-byte buffer with FLAG_HAS_FACE set and all payload bytes as 0xFF.
        // The codec reads raw floats, so 0xFF bytes produce NaN / garbage floats,
        // but no exception should be thrown because the size is sufficient.
        val data = ByteArray(242) { 0xFF.toByte() }
        data[0] = 1 // version
        data[1] = 1 // FLAG_HAS_FACE
        // timestamp = 0xFFFF_FFFF_FFFF_FF00 (bytes 2–9 are 0xFF except version/flags handled above)
        val decoded = BinaryFrameCodec.decode(data)
        assertNotNull(decoded.face)
        // blendShapes map must have exactly 52 entries
        assertEquals(52, decoded.face!!.blendShapes.size)
    }

    @Test
    fun decodeGarbagePayloadDoesNotCrashOnHandBlock() {
        // Craft a minimal valid hands block with handCount=1, side=0xFF (maps to "unknown"),
        // and 63 floats of 0x00. Total: header(10) + handCount(1) + side(1) + 252 = 264 bytes.
        val data = ByteArray(264)
        data[0] = 1 // version
        data[1] = 2 // FLAG_HAS_HANDS
        // timestamp at [2..9] = 0
        data[10] = 1 // handCount = 1
        data[11] = 0xFF.toByte() // unrecognised side → maps to "unknown"
        // remaining bytes are 0x00 (valid zero floats)
        val decoded = BinaryFrameCodec.decode(data)
        assertNotNull(decoded.hands)
        assertEquals(1, decoded.hands!!.size)
        assertEquals("unknown", decoded.hands!![0].side)
        assertEquals(63, decoded.hands!![0].landmarks.size)
    }

    // endregion
}
