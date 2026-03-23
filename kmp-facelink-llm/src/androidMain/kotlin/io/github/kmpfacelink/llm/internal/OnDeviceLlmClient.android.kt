package io.github.kmpfacelink.llm.internal

import io.github.kmpfacelink.llm.LlmConfig
import io.github.kmpfacelink.llm.ModelManager
import io.github.kmpfacelink.llm.ModelRegistry

/**
 * Creates an [OnDeviceLlmClient] with the default Android inference engine (llama.cpp).
 */
internal fun createAndroidOnDeviceLlmClient(
    config: LlmConfig.OnDevice,
    modelManager: ModelManager,
): OnDeviceLlmClient {
    val modelInfo = ModelRegistry.findById(config.modelId)
    requireNotNull(modelInfo) { "Unknown model ID: ${config.modelId}" }
    val engine = AndroidInferenceEngine()
    return OnDeviceLlmClient(config, engine, modelManager)
}
