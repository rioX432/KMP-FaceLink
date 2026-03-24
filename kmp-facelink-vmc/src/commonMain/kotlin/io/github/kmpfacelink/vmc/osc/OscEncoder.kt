package io.github.kmpfacelink.vmc.osc

/**
 * Encodes [OscMessage] instances into the OSC 1.0 binary wire format.
 *
 * All strings are null-terminated and padded to 4-byte boundaries.
 * Integers and floats use big-endian (network) byte order.
 */
internal object OscEncoder {

    /**
     * Encodes an [OscMessage] to a byte array in OSC binary format.
     */
    fun encode(message: OscMessage): ByteArray {
        val buffer = OscByteBuffer()
        writeString(buffer, message.address)
        writeTypeTagString(buffer, message.arguments)
        writeArguments(buffer, message.arguments)
        return buffer.toByteArray()
    }

    /**
     * Encodes multiple messages into an OSC bundle with the given timetag.
     */
    fun encodeBundle(messages: List<OscMessage>, timetag: Long = OSC_TIMETAG_IMMEDIATE): ByteArray {
        val buffer = OscByteBuffer()
        writeString(buffer, BUNDLE_PREFIX)
        writeTimetag(buffer, timetag)
        for (message in messages) {
            val messageBytes = encode(message)
            writeInt(buffer, messageBytes.size)
            buffer.write(messageBytes)
        }
        return buffer.toByteArray()
    }

    private fun writeTypeTagString(buffer: OscByteBuffer, arguments: List<OscArgument>) {
        val tags = buildString {
            append(',')
            for (arg in arguments) {
                append(typeTagFor(arg))
            }
        }
        writeString(buffer, tags)
    }

    private fun writeArguments(buffer: OscByteBuffer, arguments: List<OscArgument>) {
        for (arg in arguments) {
            when (arg) {
                is OscArgument.IntArg -> writeInt(buffer, arg.value)
                is OscArgument.FloatArg -> writeFloat(buffer, arg.value)
                is OscArgument.StringArg -> writeString(buffer, arg.value)
            }
        }
    }

    private fun typeTagFor(arg: OscArgument): Char = when (arg) {
        is OscArgument.IntArg -> 'i'
        is OscArgument.FloatArg -> 'f'
        is OscArgument.StringArg -> 's'
    }
}

/** OSC bundle address prefix. */
internal const val BUNDLE_PREFIX = "#bundle"

/** OSC immediate timetag (all ones = process immediately). */
internal const val OSC_TIMETAG_IMMEDIATE = 1L

internal fun writeString(buffer: OscByteBuffer, value: String) {
    val bytes = value.encodeToByteArray()
    buffer.write(bytes)
    // Null terminator + padding to 4-byte boundary
    val padCount = oscStringPadding(bytes.size)
    buffer.writeZeros(padCount)
}

internal fun writeInt(buffer: OscByteBuffer, value: Int) {
    buffer.writeByte((value shr SHIFT_24) and BYTE_MASK)
    buffer.writeByte((value shr SHIFT_16) and BYTE_MASK)
    buffer.writeByte((value shr SHIFT_8) and BYTE_MASK)
    buffer.writeByte(value and BYTE_MASK)
}

internal fun writeFloat(buffer: OscByteBuffer, value: Float) {
    writeInt(buffer, value.toRawBits())
}

private fun writeTimetag(buffer: OscByteBuffer, value: Long) {
    writeInt(buffer, (value shr Int.SIZE_BITS).toInt())
    writeInt(buffer, value.toInt())
}

/**
 * Calculates the number of null-padding bytes for an OSC string.
 *
 * An OSC string is null-terminated, then padded with additional nulls so that
 * the total byte count (content + nulls) is a multiple of 4.
 * The minimum is 1 null byte (the terminator itself).
 */
internal fun oscStringPadding(contentLength: Int): Int {
    val totalWithNull = contentLength + 1
    val remainder = totalWithNull % OSC_ALIGNMENT
    return if (remainder == 0) 1 else (OSC_ALIGNMENT - remainder) + 1
}

/** OSC requires 4-byte alignment. */
private const val OSC_ALIGNMENT = 4
private const val BYTE_MASK = 0xFF
private const val SHIFT_8 = 8
private const val SHIFT_16 = 16
private const val SHIFT_24 = 24
