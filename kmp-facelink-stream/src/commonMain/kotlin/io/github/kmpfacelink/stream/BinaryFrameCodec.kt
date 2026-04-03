package io.github.kmpfacelink.stream

import io.github.kmpfacelink.model.BlendShape

/**
 * Compact binary codec for [StreamFrame].
 *
 * Binary format (little-endian):
 * ```
 * [header]
 *   u8   version    = 1
 *   u8   flags      (bit 0 = has face, bit 1 = has hands)
 *   i64  timestampMs
 *
 * [face block]  (if flag bit 0)
 *   f32 × 52     blendShapes (ARKit order by BlendShape.ordinal)
 *   f32 × 6      headPose (pitch, yaw, roll, x, y, z)
 *
 * [hands block] (if flag bit 1)
 *   u8           handCount
 *   per hand:
 *     u8         side (0=left, 1=right, 2=unknown)
 *     f32 × 63   landmarks (21 joints × 3)
 * ```
 *
 * Total face-only frame: 2 + 8 + (52*4) + (6*4) = 242 bytes.
 */
internal object BinaryFrameCodec {

    private const val VERSION: Byte = 1
    private const val FLAG_HAS_FACE: Int = 1
    private const val FLAG_HAS_HANDS: Int = 2
    private const val BLEND_SHAPE_COUNT: Int = 52
    private const val HEAD_POSE_FLOATS: Int = 6
    private const val HAND_LANDMARK_FLOATS: Int = 63
    private const val HEADER_SIZE: Int = 10 // 1 + 1 + 8
    private const val FACE_BLOCK_SIZE: Int = (BLEND_SHAPE_COUNT + HEAD_POSE_FLOATS) * Float.SIZE_BYTES
    private const val HAND_ENTRY_SIZE: Int = 1 + HAND_LANDMARK_FLOATS * Float.SIZE_BYTES
    private const val BYTE_MASK: Int = 0xFF

    /**
     * Encodes a [StreamFrame] into a compact binary representation.
     */
    fun encode(frame: StreamFrame): ByteArray {
        val size = calculateSize(frame)
        val buffer = ByteArray(size)
        var offset = writeHeader(buffer, frame)
        offset = writeFaceBlock(buffer, offset, frame.face)
        writeHandsBlock(buffer, offset, frame.hands)
        return buffer
    }

    /**
     * Decodes a binary [ByteArray] back into a [StreamFrame].
     */
    fun decode(data: ByteArray): StreamFrame {
        var offset = 0
        // skip version byte
        offset++
        val flags = data[offset].toInt() and BYTE_MASK
        offset++
        val timestampMs = readLong(data, offset)
        offset += Long.SIZE_BYTES

        val face = if (flags and FLAG_HAS_FACE != 0) {
            val result = readFaceBlock(data, offset)
            offset = result.second
            result.first
        } else {
            null
        }

        val hands = if (flags and FLAG_HAS_HANDS != 0) {
            val result = readHandsBlock(data, offset)
            result.first
        } else {
            null
        }

        return StreamFrame(timestampMs = timestampMs, face = face, hands = hands)
    }

    private fun calculateSize(frame: StreamFrame): Int {
        var size = HEADER_SIZE
        if (frame.face != null) size += FACE_BLOCK_SIZE
        if (!frame.hands.isNullOrEmpty()) {
            size += 1 + frame.hands.size * HAND_ENTRY_SIZE
        }
        return size
    }

    private fun writeHeader(buffer: ByteArray, frame: StreamFrame): Int {
        var offset = 0
        buffer[offset++] = VERSION
        var flags = 0
        if (frame.face != null) flags = flags or FLAG_HAS_FACE
        if (!frame.hands.isNullOrEmpty()) flags = flags or FLAG_HAS_HANDS
        buffer[offset++] = flags.toByte()
        writeLong(buffer, offset, frame.timestampMs)
        offset += Long.SIZE_BYTES
        return offset
    }

    private fun writeFaceBlock(buffer: ByteArray, startOffset: Int, face: FaceFrame?): Int {
        if (face == null) return startOffset
        var offset = startOffset
        for (shape in BlendShape.entries) {
            val value = face.blendShapes[shape.arKitName] ?: 0f
            writeFloat(buffer, offset, value)
            offset += Float.SIZE_BYTES
        }
        offset = writeHeadPose(buffer, offset, face.head)
        return offset
    }

    private fun writeHandsBlock(
        buffer: ByteArray,
        startOffset: Int,
        hands: List<HandFrame>?,
    ): Int {
        if (hands.isNullOrEmpty()) return startOffset
        var offset = startOffset
        buffer[offset++] = hands.size.toByte()
        for (hand in hands) {
            buffer[offset++] = sideToIndex(hand.side).toByte()
            for (value in hand.landmarks) {
                writeFloat(buffer, offset, value)
                offset += Float.SIZE_BYTES
            }
        }
        return offset
    }

    private fun readFaceBlock(data: ByteArray, startOffset: Int): Pair<FaceFrame, Int> {
        var offset = startOffset
        val blendShapes = mutableMapOf<String, Float>()
        for (shape in BlendShape.entries) {
            blendShapes[shape.arKitName] = readFloat(data, offset)
            offset += Float.SIZE_BYTES
        }
        val headPoseResult = readHeadPose(data, offset)
        offset = headPoseResult.second
        return FaceFrame(blendShapes, headPoseResult.first) to offset
    }

    private fun readHandsBlock(data: ByteArray, startOffset: Int): Pair<List<HandFrame>, Int> {
        var offset = startOffset
        val count = data[offset].toInt() and BYTE_MASK
        offset++
        val hands = mutableListOf<HandFrame>()
        repeat(count) {
            val sideIndex = data[offset].toInt() and BYTE_MASK
            offset++
            val landmarks = mutableListOf<Float>()
            repeat(HAND_LANDMARK_FLOATS) {
                landmarks.add(readFloat(data, offset))
                offset += Float.SIZE_BYTES
            }
            hands.add(HandFrame(side = indexToSide(sideIndex), landmarks = landmarks))
        }
        return hands to offset
    }

    private fun writeHeadPose(buffer: ByteArray, startOffset: Int, head: HeadPose): Int {
        var offset = startOffset
        val values = floatArrayOf(head.pitch, head.yaw, head.roll, head.x, head.y, head.z)
        for (value in values) {
            writeFloat(buffer, offset, value)
            offset += Float.SIZE_BYTES
        }
        return offset
    }

    private fun readHeadPose(data: ByteArray, startOffset: Int): Pair<HeadPose, Int> {
        var offset = startOffset
        val values = FloatArray(HEAD_POSE_FLOATS)
        for (i in values.indices) {
            values[i] = readFloat(data, offset)
            offset += Float.SIZE_BYTES
        }
        return HeadPose(
            pitch = values[IDX_PITCH],
            yaw = values[IDX_YAW],
            roll = values[IDX_ROLL],
            x = values[IDX_X],
            y = values[IDX_Y],
            z = values[IDX_Z],
        ) to offset
    }
}

private const val IDX_PITCH = 0
private const val IDX_YAW = 1
private const val IDX_ROLL = 2
private const val IDX_X = 3
private const val IDX_Y = 4
private const val IDX_Z = 5

private fun sideToIndex(side: String): Int = when (side) {
    "left" -> 0
    "right" -> 1
    else -> 2
}

private fun indexToSide(index: Int): String = when (index) {
    0 -> "left"
    1 -> "right"
    else -> "unknown"
}

// Little-endian byte I/O helpers

private const val BYTE_MASK: Int = 0xFF
private const val LONG_MASK: Long = 0xFFFFFFFFL
private const val BITS_PER_BYTE: Int = 8
private const val SHIFT_TWO_BYTES: Int = 16
private const val SHIFT_THREE_BYTES: Int = 24
private const val BITS_PER_INT: Int = 32
private const val BYTE_OFFSET_2: Int = 2
private const val BYTE_OFFSET_3: Int = 3

internal fun writeFloat(buffer: ByteArray, offset: Int, value: Float) {
    writeInt(buffer, offset, value.toRawBits())
}

internal fun readFloat(buffer: ByteArray, offset: Int): Float =
    Float.fromBits(readInt(buffer, offset))

internal fun writeInt(buffer: ByteArray, offset: Int, value: Int) {
    buffer[offset] = (value and BYTE_MASK).toByte()
    buffer[offset + 1] = (value shr BITS_PER_BYTE and BYTE_MASK).toByte()
    buffer[offset + BYTE_OFFSET_2] = (value shr SHIFT_TWO_BYTES and BYTE_MASK).toByte()
    buffer[offset + BYTE_OFFSET_3] = (value shr SHIFT_THREE_BYTES and BYTE_MASK).toByte()
}

internal fun readInt(buffer: ByteArray, offset: Int): Int =
    (buffer[offset].toInt() and BYTE_MASK) or
        (buffer[offset + 1].toInt() and BYTE_MASK shl BITS_PER_BYTE) or
        (buffer[offset + BYTE_OFFSET_2].toInt() and BYTE_MASK shl SHIFT_TWO_BYTES) or
        (buffer[offset + BYTE_OFFSET_3].toInt() and BYTE_MASK shl SHIFT_THREE_BYTES)

internal fun writeLong(buffer: ByteArray, offset: Int, value: Long) {
    writeInt(buffer, offset, value.toInt())
    writeInt(buffer, offset + Int.SIZE_BYTES, (value shr BITS_PER_INT).toInt())
}

internal fun readLong(buffer: ByteArray, offset: Int): Long {
    val low = readInt(buffer, offset).toLong() and LONG_MASK
    val high = readInt(buffer, offset + Int.SIZE_BYTES).toLong() and LONG_MASK
    return low or (high shl BITS_PER_INT)
}
