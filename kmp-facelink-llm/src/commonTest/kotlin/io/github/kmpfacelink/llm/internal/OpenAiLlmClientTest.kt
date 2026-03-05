package io.github.kmpfacelink.llm.internal

import io.github.kmpfacelink.llm.LlmConfig
import io.github.kmpfacelink.llm.LlmState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class OpenAiLlmClientTest {

    @Test
    fun initialStateIsIdle() {
        val client = OpenAiLlmClient(LlmConfig.OpenAi(apiKey = "test-key"))
        assertIs<LlmState.Idle>(client.state.value)
        client.release()
    }

    @Test
    fun factoryCreatesOpenAiClientForOpenAiConfig() {
        val config = LlmConfig.OpenAi(apiKey = "test-key")
        val client = LlmClientFactory.create(config)
        assertIs<OpenAiLlmClient>(client)
        client.release()
    }

    @Test
    fun configDefaultModel() {
        val config = LlmConfig.OpenAi(apiKey = "test-key")
        assertEquals("gpt-4o-mini", config.model)
    }

    @Test
    fun configDefaultBaseUrl() {
        val config = LlmConfig.OpenAi(apiKey = "test-key")
        assertEquals("https://api.openai.com/v1", config.baseUrl)
    }

    @Test
    fun configDefaultMaxTokens() {
        val config = LlmConfig.OpenAi(apiKey = "test-key")
        assertEquals(1024, config.maxTokens)
    }

    @Test
    fun configDefaultTemperature() {
        val config = LlmConfig.OpenAi(apiKey = "test-key")
        assertEquals(0.7, config.temperature)
    }

    @Test
    fun configCustomValues() {
        val config = LlmConfig.OpenAi(
            apiKey = "custom-key",
            model = "gpt-4o",
            baseUrl = "https://my-proxy.example.com/v1",
            maxTokens = 2048,
            temperature = 1.0,
        )
        assertEquals("custom-key", config.apiKey)
        assertEquals("gpt-4o", config.model)
        assertEquals("https://my-proxy.example.com/v1", config.baseUrl)
        assertEquals(2048, config.maxTokens)
        assertEquals(1.0, config.temperature)
    }

    @Test
    fun releaseDoesNotThrow() {
        val client = OpenAiLlmClient(LlmConfig.OpenAi(apiKey = "test-key"))
        client.release()
    }
}
