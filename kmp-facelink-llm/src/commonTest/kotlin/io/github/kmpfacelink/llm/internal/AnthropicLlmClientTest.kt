package io.github.kmpfacelink.llm.internal

import io.github.kmpfacelink.llm.ChatMessage
import io.github.kmpfacelink.llm.ConversationHistory
import io.github.kmpfacelink.llm.LlmConfig
import io.github.kmpfacelink.llm.LlmState
import io.github.kmpfacelink.llm.PersonaConfig
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

class AnthropicLlmClientTest {

    @Test
    fun initialStateIsIdle() {
        val client = AnthropicLlmClient(LlmConfig.Anthropic(apiKey = "test-key"))
        assertIs<LlmState.Idle>(client.state.value)
        client.release()
    }

    @Test
    fun factoryCreatesAnthropicClientForAnthropicConfig() {
        val config = LlmConfig.Anthropic(apiKey = "test-key")
        val client = LlmClientFactory.create(config)
        assertIs<AnthropicLlmClient>(client)
        client.release()
    }

    @Test
    fun configDefaultModel() {
        val config = LlmConfig.Anthropic(apiKey = "test-key")
        assertEquals("claude-haiku-4-5-20251001", config.model)
    }

    @Test
    fun configDefaultMaxTokens() {
        val config = LlmConfig.Anthropic(apiKey = "test-key")
        assertEquals(1024, config.maxTokens)
    }

    @Test
    fun configDefaultTemperature() {
        val config = LlmConfig.Anthropic(apiKey = "test-key")
        assertEquals(0.7, config.temperature)
    }

    @Test
    fun configCustomValues() {
        val config = LlmConfig.Anthropic(
            apiKey = "sk-ant-key",
            model = "claude-sonnet-4-5-20250514",
            maxTokens = 4096,
            temperature = 0.5,
        )
        assertEquals("sk-ant-key", config.apiKey)
        assertEquals("claude-sonnet-4-5-20250514", config.model)
        assertEquals(4096, config.maxTokens)
        assertEquals(0.5, config.temperature)
    }

    @Test
    fun systemMessageIsExtractedFromConversation() {
        val persona = PersonaConfig(systemPrompt = "You are a helpful assistant.")
        val history = ConversationHistory(persona = persona)
        history.addUser("Hello")

        val messages = history.toMessageList()
        val systemMsg = messages.firstOrNull { it.role == ChatMessage.Role.System }
        assertEquals("You are a helpful assistant.", systemMsg?.content)
    }

    @Test
    fun noSystemMessageWhenPersonaHasEmptyPrompt() {
        val persona = PersonaConfig(systemPrompt = "")
        val history = ConversationHistory(persona = persona)
        history.addUser("Hello")

        val messages = history.toMessageList()
        val systemMsg = messages.firstOrNull { it.role == ChatMessage.Role.System }
        assertNull(systemMsg)
    }

    @Test
    fun nonSystemMessagesArePreservedInOrder() {
        val history = ConversationHistory()
        history.addUser("First")
        history.addAssistant("Second")
        history.addUser("Third")

        val messages = history.toMessageList()
        assertEquals(ChatMessage.Role.User, messages[0].role)
        assertEquals("First", messages[0].content)
        assertEquals(ChatMessage.Role.Assistant, messages[1].role)
        assertEquals(ChatMessage.Role.User, messages[2].role)
    }

    @Test
    fun releaseDoesNotThrow() {
        val client = AnthropicLlmClient(LlmConfig.Anthropic(apiKey = "test-key"))
        client.release()
    }
}
