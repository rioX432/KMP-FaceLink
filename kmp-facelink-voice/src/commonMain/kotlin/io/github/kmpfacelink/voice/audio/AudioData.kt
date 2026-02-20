package io.github.kmpfacelink.voice.audio

/**
 * Raw audio data with format metadata.
 *
 * @property bytes Raw PCM audio bytes
 * @property format Audio format descriptor
 * @property durationMs Audio duration in milliseconds
 */
public data class AudioData(
    val bytes: ByteArray,
    val format: AudioFormat,
    val durationMs: Long,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is AudioData) return false
        return bytes.contentEquals(other.bytes) && format == other.format && durationMs == other.durationMs
    }

    override fun hashCode(): Int {
        var result = bytes.contentHashCode()
        result = 31 * result + format.hashCode()
        result = 31 * result + durationMs.hashCode()
        return result
    }
}
