package io.github.kmpfacelink.llm.internal

import io.github.kmpfacelink.llm.DefaultModelManager
import io.github.kmpfacelink.llm.LlmClient
import io.github.kmpfacelink.llm.LlmConfig
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp

internal actual fun createOnDeviceLlmClient(config: LlmConfig.OnDevice): LlmClient {
    val cacheDir = platformModelCacheDir()
    val httpClient = HttpClient(OkHttp)
    val modelManager = DefaultModelManager(httpClient, cacheDir)
    return OnDeviceLlmClient.createDefault(config, modelManager)
}
