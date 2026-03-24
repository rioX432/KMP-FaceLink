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
        return tags.map { tag ->
            when (tag) {
                'i' -> OscArgument.IntArg(reader.readInt())
                'f' -> OscArgument.FloatArg(reader.readFloat())
                's' -> OscArgument.StringArg(reader.readString())
                else -> error("Unsupported OSC type tag: $tag")
            }
        }
    }
}

private const val TIMETAG_SIZE = 8
