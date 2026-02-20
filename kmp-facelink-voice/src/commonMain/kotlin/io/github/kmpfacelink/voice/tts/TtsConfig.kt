package io.github.kmpfacelink.voice.tts

private const val DEFAULT_VOICEVOX_PORT = 50021

/**
 * Configuration for TTS engines.
 */
public sealed class TtsConfig {
    /**
     * OpenAI TTS API configuration.
     *
     * Does not provide phoneme timing — lip sync uses amplitude-based animation.
     *
     * @property apiKey OpenAI API key
     * @property voice Voice name (e.g. "alloy", "echo", "fable", "onyx", "nova", "shimmer")
     * @property model Model name (e.g. "tts-1", "tts-1-hd")
     * @property speed Speech speed multiplier (0.25–4.0)
     */
    public data class OpenAiTts(
        val apiKey: String,
        val voice: String = "alloy",
        val model: String = "tts-1",
        val speed: Float = 1.0f,
    ) : TtsConfig()

    /**
     * ElevenLabs TTS API configuration.
     *
     * Provides character-level timestamps for approximate phoneme lip sync.
     *
     * @property apiKey ElevenLabs API key
     * @property voiceId Voice ID
     * @property modelId Model ID (e.g. "eleven_multilingual_v2")
     * @property stability Voice stability (0.0–1.0)
     * @property similarityBoost Similarity boost (0.0–1.0)
     */
    public data class ElevenLabs(
        val apiKey: String,
        val voiceId: String,
        val modelId: String = "eleven_multilingual_v2",
        val stability: Float = 0.5f,
        val similarityBoost: Float = 0.75f,
    ) : TtsConfig()

    /**
     * VOICEVOX local TTS configuration.
     *
     * Provides mora-level phoneme timing for high-quality lip sync.
     * Requires VOICEVOX engine running locally or on network.
     *
     * @property host VOICEVOX engine host
     * @property port VOICEVOX engine port
     * @property speakerId Speaker ID
     */
    public data class Voicevox(
        val host: String = "localhost",
        val port: Int = DEFAULT_VOICEVOX_PORT,
        val speakerId: Int = 0,
    ) : TtsConfig()
}
