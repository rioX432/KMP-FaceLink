package io.github.kmpfacelink.voice.tts

import io.github.kmpfacelink.voice.audio.AudioData

/**
 * Result of TTS synthesis.
 *
 * @property audio Synthesized audio data
 * @property phonemeEvents Phoneme timing data (empty if TTS backend doesn't provide timing)
 * @property durationMs Total audio duration in milliseconds
 */
public data class TtsResult(
    val audio: AudioData,
    val phonemeEvents: List<PhonemeEvent> = emptyList(),
    val durationMs: Long,
)
