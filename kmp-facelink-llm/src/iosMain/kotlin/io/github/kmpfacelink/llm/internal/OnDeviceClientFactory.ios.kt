package io.github.kmpfacelink.llm.internal

import io.github.kmpfacelink.llm.FoundationModelsProviderRegistry
import io.github.kmpfacelink.llm.LlmClient
import io.github.kmpfacelink.llm.LlmConfig

/**
 * iOS factory for on-device LLM clients.
 *
 * On iOS 26+, prefer [LlmConfig.FoundationModels] which uses the system
 * language model with no download required.
 *
 * For [LlmConfig.OnDevice] (GGUF models), this factory checks if Foundation Models
 * is available and transparently upgrades to it when possible, avoiding unnecessary
 * model downloads.
 */
internal actual fun createOnDeviceLlmClient(config: LlmConfig.OnDevice): LlmClient {
    // If Foundation Models is available, prefer it over downloading a GGUF model
    val provider = FoundationModelsProviderRegistry.get()
    if (provider != null && provider.isAvailable()) {
        val fmConfig = LlmConfig.FoundationModels(
            maxTokens = config.maxTokens,
            temperature = config.temperature,
        )
        return FoundationModelsLlmClient(fmConfig, provider)
    }

    // Fallback: GGUF model path (MLX stubs removed — this path is non-functional on iOS)
    error(
        "On-device GGUF inference is not supported on iOS. " +
            "Use LlmConfig.FoundationModels on iOS 26+ or a cloud provider as fallback.",
    )
}
