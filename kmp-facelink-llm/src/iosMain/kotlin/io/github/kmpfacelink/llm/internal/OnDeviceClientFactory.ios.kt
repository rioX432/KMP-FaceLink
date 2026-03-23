package io.github.kmpfacelink.llm.internal

import io.github.kmpfacelink.llm.DefaultModelManager
import io.github.kmpfacelink.llm.LlmClient
import io.github.kmpfacelink.llm.LlmConfig
import io.github.kmpfacelink.llm.ModelRegistry
import io.ktor.client.HttpClient
import io.ktor.client.engine.darwin.Darwin

internal actual fun createOnDeviceLlmClient(config: LlmConfig.OnDevice): LlmClient {
    val modelInfo = ModelRegistry.findById(config.modelId)
    requireNotNull(modelInfo) { "Unknown model ID: ${config.modelId}" }
    val cacheDir = platformModelCacheDir()
    val httpClient = HttpClient(Darwin)
    val modelManager = DefaultModelManager(httpClient, cacheDir)
    val engine = IosInferenceEngine()
    return OnDeviceLlmClient(config, engine, modelManager)
}
