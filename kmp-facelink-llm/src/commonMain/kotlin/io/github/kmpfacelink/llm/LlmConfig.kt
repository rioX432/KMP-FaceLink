package io.github.kmpfacelink.llm

private const val DEFAULT_MAX_TOKENS = 1024
private const val DEFAULT_TEMPERATURE = 0.7

/**
 * Configuration for LLM provider backends.
 *
 * Each variant configures a different API provider. Use with [LlmClient.Companion.create]
 * to instantiate the corresponding client.
 */
public sealed class LlmConfig {

    /**
     * OpenAI Chat Completions API configuration.
     *
     * @property apiKey OpenAI API key
     * @property model Model name (e.g. "gpt-4o-mini", "gpt-4o")
     * @property baseUrl Base URL for the API (supports OpenAI-compatible endpoints)
     * @property maxTokens Maximum tokens in the response
     * @property temperature Sampling temperature (0.0–2.0)
     */
    public data class OpenAi(
        val apiKey: String,
        val model: String = "gpt-4o-mini",
        val baseUrl: String = "https://api.openai.com/v1",
        val maxTokens: Int = DEFAULT_MAX_TOKENS,
        val temperature: Double = DEFAULT_TEMPERATURE,
    ) : LlmConfig()

    /**
     * Anthropic Messages API configuration.
     *
     * @property apiKey Anthropic API key
     * @property model Model name (e.g. "claude-haiku-4-5-20251001", "claude-sonnet-4-5-20250514")
     * @property maxTokens Maximum tokens in the response
     * @property temperature Sampling temperature (0.0–1.0)
     */
    public data class Anthropic(
        val apiKey: String,
        val model: String = "claude-haiku-4-5-20251001",
        val maxTokens: Int = DEFAULT_MAX_TOKENS,
        val temperature: Double = DEFAULT_TEMPERATURE,
    ) : LlmConfig()

    /**
     * Google Gemini API configuration.
     *
     * @property apiKey Gemini API key
     * @property model Model name (e.g. "gemini-2.0-flash", "gemini-2.0-pro")
     * @property maxTokens Maximum tokens in the response
     * @property temperature Sampling temperature (0.0–2.0)
     */
    public data class Gemini(
        val apiKey: String,
        val model: String = "gemini-2.0-flash",
        val maxTokens: Int = DEFAULT_MAX_TOKENS,
        val temperature: Double = DEFAULT_TEMPERATURE,
    ) : LlmConfig()
}
