package io.github.kmpfacelink.voice.audio

private const val DEFAULT_SAMPLE_RATE = 16000
private const val DEFAULT_BITS_PER_SAMPLE = 16

/**
 * Audio format descriptor.
 *
 * @property sampleRate Sample rate in Hz (default 16000 for speech)
 * @property channels Number of audio channels (1 = mono, 2 = stereo)
 * @property bitsPerSample Bits per sample (16 for Int16, 32 for Float32)
 */
public data class AudioFormat(
    val sampleRate: Int = DEFAULT_SAMPLE_RATE,
    val channels: Int = 1,
    val bitsPerSample: Int = DEFAULT_BITS_PER_SAMPLE,
)
