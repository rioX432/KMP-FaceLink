package io.github.kmpfacelink.voice.asr

import io.github.kmpfacelink.voice.ApiEndpoints

private const val DEFAULT_WHISPER_THREADS = 4

/**
 * Configuration for ASR engines.
 */
public sealed class AsrConfig {
    /**
     * OpenAI Whisper cloud API configuration.
     *
     * @property apiKey OpenAI API key
     * @property baseUrl Base URL for the API (supports OpenAI-compatible endpoints)
     * @property model Whisper model name
     * @property language Optional language hint (ISO 639-1)
     */
    public data class OpenAiWhisper(
        val apiKey: String,
        val baseUrl: String = ApiEndpoints.OPENAI_V1,
        val model: String = "whisper-1",
        val language: String? = null,
    ) : AsrConfig()

    /**
     * On-device whisper.cpp configuration.
     *
     * @property modelPath Path to the whisper.cpp model file
     * @property language Language hint (null for auto-detect)
     * @property threads Number of threads for inference
     */
    public data class WhisperCpp(
        val modelPath: String,
        val language: String? = null,
        val threads: Int = DEFAULT_WHISPER_THREADS,
    ) : AsrConfig()
}
