package io.github.kmpfacelink.voice.tts

/**
 * A phoneme with timing information from TTS synthesis.
 *
 * @property phoneme Phoneme string (IPA or VOICEVOX format)
 * @property startMs Start time in milliseconds relative to audio start
 * @property endMs End time in milliseconds relative to audio start
 */
public data class PhonemeEvent(
    val phoneme: String,
    val startMs: Long,
    val endMs: Long,
)
