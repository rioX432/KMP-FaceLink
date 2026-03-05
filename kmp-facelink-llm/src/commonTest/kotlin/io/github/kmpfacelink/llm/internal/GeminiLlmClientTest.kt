package io.github.kmpfacelink.llm.internal

import io.github.kmpfacelink.llm.ChatMessage
import io.github.kmpfacelink.llm.ConversationHistory
import io.github.kmpfacelink.llm.LlmConfig
import io.github.kmpfacelink.llm.LlmState
import io.github.kmpfacelink.llm.PersonaConfig
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull

class GeminiLlmClientTest {

    @Test
    fun initialStateIsIdle() {
        val client = GeminiLlmClient(LlmConfig.Gemini(apiKey = "test-key"))
        assertIs<LlmState.Idle>(client.state.value)
        client.release()
    }

    @Test
    fun factoryCreatesGeminiClientForGeminiConfig() {
        val config = LlmConfig.Gemini(apiKey = "test-key")
        val client = LlmClientFactory.create(config)
        assertIs<GeminiLlmClient>(client)
        client.release()
    }

    @Test
    fun configDefaultModel() {
        val config = LlmConfig.Gemini(apiKey = "test-key")
        assertEquals("gemini-2.0-flash", config.model)
    }

    @Test
    fun configDefaultMaxTokens() {
        val config = LlmConfig.Gemini(apiKey = "test-key")
        assertEquals(1024, config.maxTokens)
    }

    @Test
    fun configDefaultTemperature() {
        val config = LlmConfig.Gemini(apiKey = "test-key")
        assertEquals(0.7, config.temperature)
    }

    @Test
    fun configCustomValues() {
        val config = LlmConfig.Gemini(
            apiKey = "ai-gemini-key",
            model = "gemini-2.0-pro",
            maxTokens = 8192,
            temperature = 0.3,
        )
        assertEquals("ai-gemini-key", config.apiKey)
        assertEquals("gemini-2.0-pro", config.model)
        assertEquals(8192, config.maxTokens)
        assertEquals(0.3, config.temperature)
    }

    @Test
    fun clientIsNotNullAfterCreation() {
        val client = GeminiLlmClient(LlmConfig.Gemini(apiKey = "test-key"))
        assertNotNull(client)
        client.release()
    }

    @Test
    fun systemMessageIsIncludedInMessageList() {
        val persona = PersonaConfig(systemPrompt = "You are Gemini assistant.")
        val history = ConversationHistory(persona = persona)
        history.addUser("Hello")

        val messages = history.toMessageList()
        val systemMsg = messages.firstOrNull { it.role == ChatMessage.Role.System }
        assertEquals("You are Gemini assistant.", systemMsg?.content)
    }

    @Test
    fun userAndAssistantMessagesAreFiltered() {
        val persona = PersonaConfig(systemPrompt = "sys")
        val history = ConversationHistory(persona = persona)
        history.addUser("Hi")
        history.addAssistant("Hello")

        val allMessages = history.toMessageList()
        val nonSystem = allMessages.filter { it.role != ChatMessage.Role.System }
        assertEquals(2, nonSystem.size)
        assertEquals(ChatMessage.Role.User, nonSystem[0].role)
        assertEquals(ChatMessage.Role.Assistant, nonSystem[1].role)
    }

    @Test
    fun releaseDoesNotThrow() {
        val client = GeminiLlmClient(LlmConfig.Gemini(apiKey = "test-key"))
        client.release()
    }
}
