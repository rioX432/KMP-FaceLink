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
        is LlmConfig.OnDevice -> createOnDeviceLlmClient(config)
        is LlmConfig.Hybrid -> HybridLlmClient(
            onDeviceClient = create(config.onDeviceConfig),
            cloudClient = create(config.cloudConfig),
            routingStrategy = config.routingStrategy,
        )
    }
}
