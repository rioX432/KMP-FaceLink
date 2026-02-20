package io.github.kmpfacelink.voice.asr

/**
 * Result of a speech-to-text transcription.
 *
 * @property text Transcribed text
 * @property isFinal Whether this is a final (non-partial) transcription
 * @property language Detected language code (e.g. "en"), null if unknown
 * @property timestampMs Timestamp when this transcription was produced
 */
public data class TranscriptionResult(
    val text: String,
    val isFinal: Boolean = true,
    val language: String? = null,
    val timestampMs: Long = 0L,
)
