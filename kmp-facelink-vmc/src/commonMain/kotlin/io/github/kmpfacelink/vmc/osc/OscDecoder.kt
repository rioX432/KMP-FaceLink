package io.github.kmpfacelink.vmc.osc

/**
 * Decodes OSC 1.0 binary data into [OscMessage] instances.
 *
 * Supports both individual messages and OSC bundles.
 * Unknown type tags are skipped (per OSC spec, unknown addresses/arguments should be ignored).
 */
internal object OscDecoder {

    /**
     * Decodes an OSC packet (message or bundle) from raw bytes.
     * Returns a list of messages. A single message returns a one-element list;
     * a bundle returns all contained messages.
     */
    fun decode(data: ByteArray): List<OscMessage> {
        val reader = OscByteReader(data)
        return if (isBundle(data)) {
            decodeBundle(reader)
        } else {
            listOf(decodeMessage(reader))
        }
    }

    private fun isBundle(data: ByteArray): Boolean {
        if (data.size < BUNDLE_PREFIX.length) return false
        val prefix = data.decodeToString(endIndex = BUNDLE_PREFIX.length)
        return prefix == BUNDLE_PREFIX
    }

    private fun decodeBundle(reader: OscByteReader): List<OscMessage> {
        // Skip "#bundle" string (padded to 4-byte boundary)
        reader.readString()
        // Skip timetag (8 bytes)
        reader.skip(TIMETAG_SIZE)

        val messages = mutableListOf<OscMessage>()
        while (reader.hasRemaining()) {
            val elementSize = reader.readInt()
            if (elementSize <= 0) break
            val elementBytes = reader.readBytes(elementSize)
            messages.addAll(decode(elementBytes))
        }
        return messages
    }

    private fun decodeMessage(reader: OscByteReader): OscMessage {
        val address = reader.readString()
        val typeTags = reader.readString()
        val arguments = decodeArguments(reader, typeTags)
        return OscMessage(address, arguments)
    }

    private fun decodeArguments(reader: OscByteReader, typeTags: String): List<OscArgument> {
        // Type tag string starts with ','
        if (typeTags.isEmpty() || typeTags[0] != ',') return emptyList()
        val tags = typeTags.substring(1)
        return tags.mapNotNull { tag -> decodeTag(reader, tag) }
    }

    private fun decodeTag(reader: OscByteReader, tag: Char): OscArgument? = when (tag) {
        'i' -> OscArgument.IntArg(reader.readInt())
        'f' -> OscArgument.FloatArg(reader.readFloat())
        's' -> OscArgument.StringArg(reader.readString())
        else -> {
            skipUnsupportedTag(reader, tag)
            null
        }
    }

    /**
     * Skips data for an unsupported OSC type tag.
     *
     * Known OSC types have well-defined sizes: we advance the reader past their data
     * so subsequent arguments decode correctly. Zero-size tags (True, False, Nil,
     * Infinitum) need no skipping. For truly unknown tags, we cannot determine the
     * data size, so we skip nothing and hope the stream remains aligned.
     */
    private fun skipUnsupportedTag(reader: OscByteReader, tag: Char) {
        when (tag) {
            // 8-byte types: int64, float64 / double, timetag
            'h', 'd', 't' -> reader.skip(EIGHT_BYTE_TAG_SIZE)
            // 4-byte types: char, RGBA color, MIDI message
            'c', 'r', 'm' -> reader.skip(FOUR_BYTE_TAG_SIZE)
            // Variable-length: blob (int32 size prefix + data, 4-byte aligned)
            'b' -> skipBlob(reader)
            // Zero-size types: True, False, Nil, Infinitum
            'T', 'F', 'N', 'I' -> { /* no data to skip */ }
            // Truly unknown tag — cannot determine size
            else -> { /* skip nothing; stream may become misaligned */ }
        }
    }

    private fun skipBlob(reader: OscByteReader) {
        val blobSize = reader.readInt()
        val padded = blobSize + oscBlobPadding(blobSize)
        reader.skip(padded)
    }
}

private const val TIMETAG_SIZE = 8
private const val FOUR_BYTE_TAG_SIZE = 4
private const val EIGHT_BYTE_TAG_SIZE = 8
private const val OSC_ALIGN = 4

/** Returns the number of padding bytes needed to align a blob payload to 4 bytes. */
private fun oscBlobPadding(size: Int): Int {
    val remainder = size % OSC_ALIGN
    return if (remainder == 0) 0 else OSC_ALIGN - remainder
}
