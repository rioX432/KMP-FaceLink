package io.github.kmpfacelink.voice

/**
 * Shared API endpoint constants used across voice module configurations.
 *
 * These are default values only. Each config class (e.g. [io.github.kmpfacelink.voice.asr.AsrConfig],
 * [io.github.kmpfacelink.voice.tts.TtsConfig]) accepts a `baseUrl` parameter to override them
 * with custom or self-hosted endpoints.
 */
public object ApiEndpoints {
    /** OpenAI API v1 base URL, used by both ASR (Whisper) and TTS engines. */
    public const val OPENAI_V1: String = "https://api.openai.com/v1"

    /** ElevenLabs API v1 base URL for TTS. */
    public const val ELEVENLABS_V1: String = "https://api.elevenlabs.io/v1"

    /** Default port for the VOICEVOX local TTS engine. */
    public const val DEFAULT_VOICEVOX_PORT: Int = 50021
}
