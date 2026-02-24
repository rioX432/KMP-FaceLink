package io.github.kmpfacelink.llm.internal

import io.github.kmpfacelink.llm.LlmClient
import io.github.kmpfacelink.llm.LlmConfig

/**
 * Factory for creating [LlmClient] instances from configuration.
 */
internal object LlmClientFactory {

    internal fun create(config: LlmConfig): LlmClient = when (config) {
        is LlmConfig.OpenAi -> OpenAiLlmClient(config)
        is LlmConfig.Anthropic -> AnthropicLlmClient(config)
        is LlmConfig.Gemini -> GeminiLlmClient(config)
    }
}
