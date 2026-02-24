package io.github.kmpfacelink.llm

import kotlin.test.Test
import kotlin.test.assertIs
import kotlin.test.assertNotNull

class LlmClientCreateTest {

    @Test
    fun createOpenAiClient() {
        val client = LlmClient.create(LlmConfig.OpenAi(apiKey = "test-key"))
        assertNotNull(client)
        assertIs<LlmState.Idle>(client.state.value)
        client.release()
    }

    @Test
    fun createAnthropicClient() {
        val client = LlmClient.create(LlmConfig.Anthropic(apiKey = "test-key"))
        assertNotNull(client)
        assertIs<LlmState.Idle>(client.state.value)
        client.release()
    }

    @Test
    fun createGeminiClient() {
        val client = LlmClient.create(LlmConfig.Gemini(apiKey = "test-key"))
        assertNotNull(client)
        assertIs<LlmState.Idle>(client.state.value)
        client.release()
    }
}
