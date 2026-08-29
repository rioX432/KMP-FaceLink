@file:Suppress("MagicNumber")

package io.github.kmpfacelink.model

/** Versioned deterministic encoding and SHA-256 fingerprint for runtime epochs. */
public object CanonicalContextFingerprint {

    /** Returns a portable fingerprint that excludes tracker-session identity. */
    public fun computeConfiguration(epoch: TrackingContextEpoch): String = sha256Hex(encodeConfiguration(epoch))

    /** Returns a runtime fingerprint that binds session and epoch identity. */
    public fun computeEpoch(epoch: TrackingContextEpoch): String = sha256Hex(encodeEpoch(epoch))

    /** Returns portable canonical configuration bytes for evidence matching. */
    public fun encodeConfiguration(epoch: TrackingContextEpoch): ByteArray {
        val writer = CanonicalWriter()
        writer.string(CONFIGURATION_SCHEMA_ID)
        writeConfiguration(writer, epoch)
        return writer.toByteArray()
    }

    /** Returns runtime canonical bytes for submission and interpolation correlation. */
    public fun encodeEpoch(epoch: TrackingContextEpoch): ByteArray {
        val writer = CanonicalWriter()
        writer.string(EPOCH_SCHEMA_ID)
        writer.uLong(epoch.epochId)
        writer.string(epoch.sessionInstanceId.opaqueValue)
        writer.bytes(encodeConfiguration(epoch))
        return writer.toByteArray()
    }

    private fun writeConfiguration(writer: CanonicalWriter, epoch: TrackingContextEpoch) {
        writer.string(epoch.estimator.family.name)
        writer.dimensionString(epoch.estimator.libraryVersion)
        writer.dimensionString(epoch.estimator.modelSha256)
        writer.string(epoch.platform.platform)
        writer.dimensionString(epoch.platform.osVersion)
        writer.dimensionString(epoch.platform.deviceModel)
        writer.dimensionString(epoch.platform.estimatorConfigurationId)
        writer.dimensionEnum(epoch.runningMode) { mode -> mode.name }
        writer.dimensionString(epoch.resolvedDelegate)
        writer.dimensionInt(epoch.inputWidth)
        writer.dimensionInt(epoch.inputHeight)
        writer.dimensionBoolean(epoch.mirroredBeforeInference)
        writer.int(epoch.configuredTransforms.size)
        for (transform in epoch.configuredTransforms) {
            writer.string(transform.kind.name)
            writer.string(transform.implementationId)
            writer.string(transform.configurationFingerprint)
            writer.string(transform.stateDependence.name)
        }
        val sortedShapes = epoch.activeBlendShapes.sortedBy { shape -> shape.arKitName }
        writer.int(sortedShapes.size)
        for (shape in sortedShapes) writer.string(shape.arKitName)
    }

    private const val CONFIGURATION_SCHEMA_ID: String = "KMPFL_CONFIGURATION_V1"
    private const val EPOCH_SCHEMA_ID: String = "KMPFL_EPOCH_V1"
}

internal fun sha256Hex(bytes: ByteArray): String =
    Sha256.digest(bytes).joinToString(separator = "") { byte ->
        byte.toUByte().toString(radix = 16).padStart(length = 2, padChar = '0')
    }

private class CanonicalWriter {
    private val bytes = mutableListOf<Byte>()

    fun string(value: String) {
        val encoded = value.encodeToByteArray()
        bytes(encoded)
    }

    fun bytes(value: ByteArray) {
        int(value.size)
        bytes.addAll(value.toList())
    }

    fun int(value: Int) {
        for (shift in INT_SHIFTS) bytes += (value ushr shift).toByte()
    }

    fun uLong(value: ULong) {
        for (shift in LONG_SHIFTS) bytes += (value shr shift).toByte()
    }

    fun dimensionString(value: DimensionValue<String>) {
        dimension(value, ::string)
    }

    fun dimensionInt(value: DimensionValue<Int>) {
        dimension(value, ::int)
    }

    fun dimensionBoolean(value: DimensionValue<Boolean>) {
        dimension(value) { item -> bytes += if (item) TRUE_BYTE else FALSE_BYTE }
    }

    fun <T> dimensionEnum(value: DimensionValue<T>, wireName: (T) -> String) {
        dimension(value) { item -> string(wireName(item)) }
    }

    fun toByteArray(): ByteArray = bytes.toByteArray()

    private fun <T> dimension(value: DimensionValue<T>, known: (T) -> Unit) {
        when (value) {
            is DimensionValue.Known -> {
                bytes += KNOWN_TAG
                known(value.value)
            }
            DimensionValue.NotApplicable -> bytes += NOT_APPLICABLE_TAG
            DimensionValue.Unknown -> bytes += UNKNOWN_TAG
        }
    }

    private companion object {
        val INT_SHIFTS: IntArray = intArrayOf(24, 16, 8, 0)
        val LONG_SHIFTS: IntArray = intArrayOf(56, 48, 40, 32, 24, 16, 8, 0)
        const val KNOWN_TAG: Byte = 1
        const val NOT_APPLICABLE_TAG: Byte = 2
        const val UNKNOWN_TAG: Byte = 3
        const val FALSE_BYTE: Byte = 0
        const val TRUE_BYTE: Byte = 1
    }
}

private object Sha256 {
    private val roundConstants: IntArray = intArrayOf(
        0x428a2f98, 0x71374491, 0xb5c0fbcf.toInt(), 0xe9b5dba5.toInt(),
        0x3956c25b, 0x59f111f1, 0x923f82a4.toInt(), 0xab1c5ed5.toInt(),
        0xd807aa98.toInt(), 0x12835b01, 0x243185be, 0x550c7dc3,
        0x72be5d74, 0x80deb1fe.toInt(), 0x9bdc06a7.toInt(), 0xc19bf174.toInt(),
        0xe49b69c1.toInt(), 0xefbe4786.toInt(), 0x0fc19dc6, 0x240ca1cc,
        0x2de92c6f, 0x4a7484aa, 0x5cb0a9dc, 0x76f988da,
        0x983e5152.toInt(), 0xa831c66d.toInt(), 0xb00327c8.toInt(), 0xbf597fc7.toInt(),
        0xc6e00bf3.toInt(), 0xd5a79147.toInt(), 0x06ca6351, 0x14292967,
        0x27b70a85, 0x2e1b2138, 0x4d2c6dfc, 0x53380d13,
        0x650a7354, 0x766a0abb, 0x81c2c92e.toInt(), 0x92722c85.toInt(),
        0xa2bfe8a1.toInt(), 0xa81a664b.toInt(), 0xc24b8b70.toInt(), 0xc76c51a3.toInt(),
        0xd192e819.toInt(), 0xd6990624.toInt(), 0xf40e3585.toInt(), 0x106aa070,
        0x19a4c116, 0x1e376c08, 0x2748774c, 0x34b0bcb5,
        0x391c0cb3, 0x4ed8aa4a, 0x5b9cca4f, 0x682e6ff3,
        0x748f82ee, 0x78a5636f, 0x84c87814.toInt(), 0x8cc70208.toInt(),
        0x90befffa.toInt(), 0xa4506ceb.toInt(), 0xbef9a3f7.toInt(), 0xc67178f2.toInt(),
    )

    fun digest(input: ByteArray): ByteArray {
        val padded = pad(input)
        val state = intArrayOf(
            0x6a09e667,
            0xbb67ae85.toInt(),
            0x3c6ef372,
            0xa54ff53a.toInt(),
            0x510e527f,
            0x9b05688c.toInt(),
            0x1f83d9ab,
            0x5be0cd19,
        )
        val schedule = IntArray(ROUND_COUNT)
        for (offset in padded.indices step BLOCK_BYTES) {
            fillSchedule(padded, offset, schedule)
            compress(state, schedule)
        }
        return stateToBytes(state)
    }

    private fun pad(input: ByteArray): ByteArray {
        val bitLength = input.size.toULong() * BITS_PER_BYTE.toULong()
        var total = input.size + ONE_MARKER_BYTE + LENGTH_BYTES
        while (total % BLOCK_BYTES != 0) total++
        val output = ByteArray(total)
        input.copyInto(output)
        output[input.size] = HIGH_BIT
        for (index in 0 until LENGTH_BYTES) {
            val shift = (LENGTH_BYTES - 1 - index) * BITS_PER_BYTE
            output[total - LENGTH_BYTES + index] = (bitLength shr shift).toByte()
        }
        return output
    }

    private fun fillSchedule(block: ByteArray, offset: Int, schedule: IntArray) {
        for (index in 0 until INITIAL_WORDS) {
            val base = offset + index * WORD_BYTES
            schedule[index] =
                ((block[base].toInt() and BYTE_MASK) shl 24) or
                ((block[base + 1].toInt() and BYTE_MASK) shl 16) or
                ((block[base + 2].toInt() and BYTE_MASK) shl 8) or
                (block[base + 3].toInt() and BYTE_MASK)
        }
        for (index in INITIAL_WORDS until ROUND_COUNT) {
            val s0 = schedule[index - 15].rotateRight(7) xor
                schedule[index - 15].rotateRight(18) xor (schedule[index - 15] ushr 3)
            val s1 = schedule[index - 2].rotateRight(17) xor
                schedule[index - 2].rotateRight(19) xor (schedule[index - 2] ushr 10)
            schedule[index] = schedule[index - 16] + s0 + schedule[index - 7] + s1
        }
    }

    private fun compress(state: IntArray, schedule: IntArray) {
        var a = state[0]
        var b = state[1]
        var c = state[2]
        var d = state[3]
        var e = state[4]
        var f = state[5]
        var g = state[6]
        var h = state[7]
        for (index in 0 until ROUND_COUNT) {
            val sum1 = e.rotateRight(6) xor e.rotateRight(11) xor e.rotateRight(25)
            val choice = (e and f) xor (e.inv() and g)
            val temporary1 = h + sum1 + choice + roundConstants[index] + schedule[index]
            val sum0 = a.rotateRight(2) xor a.rotateRight(13) xor a.rotateRight(22)
            val majority = (a and b) xor (a and c) xor (b and c)
            val temporary2 = sum0 + majority
            h = g
            g = f
            f = e
            e = d + temporary1
            d = c
            c = b
            b = a
            a = temporary1 + temporary2
        }
        state[0] += a
        state[1] += b
        state[2] += c
        state[3] += d
        state[4] += e
        state[5] += f
        state[6] += g
        state[7] += h
    }

    private fun stateToBytes(state: IntArray): ByteArray {
        val output = ByteArray(state.size * WORD_BYTES)
        for ((index, word) in state.withIndex()) {
            val base = index * WORD_BYTES
            output[base] = (word ushr 24).toByte()
            output[base + 1] = (word ushr 16).toByte()
            output[base + 2] = (word ushr 8).toByte()
            output[base + 3] = word.toByte()
        }
        return output
    }

    private fun Int.rotateRight(bits: Int): Int = (this ushr bits) or (this shl (Int.SIZE_BITS - bits))

    private const val BLOCK_BYTES: Int = 64
    private const val ROUND_COUNT: Int = 64
    private const val INITIAL_WORDS: Int = 16
    private const val WORD_BYTES: Int = 4
    private const val LENGTH_BYTES: Int = 8
    private const val ONE_MARKER_BYTE: Int = 1
    private const val BITS_PER_BYTE: Int = 8
    private const val BYTE_MASK: Int = 0xff
    private const val HIGH_BIT: Byte = -128
}
