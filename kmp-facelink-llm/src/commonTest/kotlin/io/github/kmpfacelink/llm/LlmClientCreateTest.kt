package io.github.kmpfacelink.llm

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNotNull

class LlmClientCreateTest {

    @AfterTest
    fun tearDown() {
        FoundationModelsProviderRegistry.clear()
    }

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

    @Test
    fun createFoundationModelsClientWithProvider() {
        FoundationModelsProviderRegistry.register(StubProvider())
        val client = LlmClient.create(LlmConfig.FoundationModels())
        assertNotNull(client)
        assertIs<LlmState.Idle>(client.state.value)
        client.release()
    }

    @Test
    fun createFoundationModelsClientWithoutProviderThrows() {
        assertFailsWith<IllegalStateException> {
            LlmClient.create(LlmConfig.FoundationModels())
        }
    }

    private class StubProvider : FoundationModelsProvider {
        override fun isAvailable(): Boolean = true
        override suspend fun respond(
            prompt: String,
            instructions: String,
            maxTokens: Int,
            temperature: Double,
        ): String = ""

        override fun streamResponse(
            prompt: String,
            instructions: String,
            maxTokens: Int,
            temperature: Double,
        ): Flow<String> = emptyFlow()
    }
}
