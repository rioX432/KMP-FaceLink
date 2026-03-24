package io.github.kmpfacelink.vmc.osc

/**
 * Sequential reader for OSC binary data.
 *
 * Handles 4-byte alignment, big-endian integers/floats, and null-terminated padded strings.
 */
internal class OscByteReader(private val data: ByteArray) {
    private var position = 0

    fun hasRemaining(): Boolean = position < data.size

    /**
     * Reads a null-terminated, 4-byte-aligned OSC string.
     */
    fun readString(): String {
        val start = position
        // Find null terminator
        while (position < data.size && data[position] != ZERO_BYTE) {
            position++
        }
        val str = data.decodeToString(start, position)
        // Skip null terminator
        position++
        // Align to 4-byte boundary
        alignTo4()
        return str
    }

    /**
     * Reads a big-endian 32-bit integer.
     */
    fun readInt(): Int {
        require(position + INT_SIZE <= data.size) { "Not enough data for int32" }
        val value = ((data[position].toInt() and BYTE_MASK) shl SHIFT_24) or
            ((data[position + 1].toInt() and BYTE_MASK) shl SHIFT_16) or
            ((data[position + 2].toInt() and BYTE_MASK) shl SHIFT_8) or
            (data[position + 3].toInt() and BYTE_MASK)
        position += INT_SIZE
        return value
    }

    /**
     * Reads a big-endian 32-bit IEEE 754 float.
     */
    fun readFloat(): Float = Float.fromBits(readInt())

    /**
     * Reads the specified number of bytes.
     */
    fun readBytes(count: Int): ByteArray {
        require(position + count <= data.size) { "Not enough data: need $count, have ${data.size - position}" }
        val result = data.copyOfRange(position, position + count)
        position += count
        return result
    }

    /**
     * Skips the specified number of bytes.
     */
    fun skip(count: Int) {
        position += count
    }

    private fun alignTo4() {
        val remainder = position % OSC_ALIGN
        if (remainder != 0) {
            position += OSC_ALIGN - remainder
        }
    }
}

private const val ZERO_BYTE: Byte = 0
private const val BYTE_MASK = 0xFF
private const val INT_SIZE = 4
private const val OSC_ALIGN = 4
private const val SHIFT_8 = 8
private const val SHIFT_16 = 16
private const val SHIFT_24 = 24
