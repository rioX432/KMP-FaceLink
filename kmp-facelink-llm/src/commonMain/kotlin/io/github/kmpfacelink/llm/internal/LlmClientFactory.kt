package io.github.kmpfacelink.llm.internal

import io.github.kmpfacelink.llm.FoundationModelsProviderRegistry
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
        is LlmConfig.FoundationModels -> createFoundationModelsClient(config)
        is LlmConfig.Hybrid -> HybridLlmClient(
            onDeviceClient = create(config.onDeviceConfig),
            cloudClient = create(config.cloudConfig),
            routingStrategy = config.routingStrategy,
        )
    }

    private fun createFoundationModelsClient(
        config: LlmConfig.FoundationModels,
    ): LlmClient {
        val provider = FoundationModelsProviderRegistry.get()
            ?: error(
                "No FoundationModelsProvider registered. " +
                    "Call FoundationModelsProviderRegistry.register() at app startup on iOS 26+.",
            )
        return FoundationModelsLlmClient(config, provider)
    }
}
