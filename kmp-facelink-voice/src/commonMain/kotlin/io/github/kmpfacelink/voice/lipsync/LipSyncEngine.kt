package io.github.kmpfacelink.voice.lipsync

import io.github.kmpfacelink.voice.audio.AudioData
import io.github.kmpfacelink.voice.tts.PhonemeEvent
import kotlinx.coroutines.flow.Flow

/**
 * Engine that generates lip sync animation frames from TTS output.
 *
 * Supports two strategies:
 * - **Phoneme-based**: Uses phoneme timing data from VOICEVOX/ElevenLabs for accurate lip sync
 * - **Amplitude-based**: Uses audio volume as fallback for backends without timing data (OpenAI TTS)
 */
public interface LipSyncEngine {
    /**
     * Generates lip sync frames from phoneme timing data.
     *
     * @param phonemeEvents Phoneme events with timing from TTS
     * @param config Animation configuration
     * @return Flow of lip sync frames at the configured frame rate
     */
    public fun animateFromPhonemes(
        phonemeEvents: List<PhonemeEvent>,
        config: LipSyncConfig = LipSyncConfig(),
    ): Flow<LipSyncFrame>

    /**
     * Generates lip sync frames from audio amplitude analysis.
     *
     * Fallback for TTS backends that don't provide phoneme timing.
     *
     * @param audio Audio data to analyze
     * @param config Animation configuration
     * @return Flow of lip sync frames at the configured frame rate
     */
    public fun animateFromAudio(
        audio: AudioData,
        config: LipSyncConfig = LipSyncConfig(),
    ): Flow<LipSyncFrame>
}
