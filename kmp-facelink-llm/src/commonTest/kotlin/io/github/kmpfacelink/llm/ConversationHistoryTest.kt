package io.github.kmpfacelink.llm

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ConversationHistoryTest {

    @Test
    fun addAndRetrieveMessages() {
        val history = ConversationHistory()
        history.addUser("Hello")
        history.addAssistant("Hi there")

        assertEquals(2, history.size)
        assertEquals(ChatMessage.Role.User, history.messages[0].role)
        assertEquals(ChatMessage.Role.Assistant, history.messages[1].role)
    }

    @Test
    fun toMessageListIncludesSystemPrompt() {
        val persona = PersonaConfig(systemPrompt = "You are helpful.")
        val history = ConversationHistory(persona = persona)
        history.addUser("Hello")

        val messages = history.toMessageList()
        assertEquals(2, messages.size)
        assertEquals(ChatMessage.Role.System, messages[0].role)
        assertEquals("You are helpful.", messages[0].content)
        assertEquals(ChatMessage.Role.User, messages[1].role)
    }

    @Test
    fun toMessageListOmitsSystemWhenEmpty() {
        val history = ConversationHistory()
        history.addUser("Hello")

        val messages = history.toMessageList()
        assertEquals(1, messages.size)
        assertEquals(ChatMessage.Role.User, messages[0].role)
    }

    @Test
    fun trimDropsOldestMessagesWhenOverBudget() {
        val persona = PersonaConfig(systemPrompt = "sys")
        // Very small budget to force trimming
        val history = ConversationHistory(persona = persona, maxContextTokens = 20)

        // Add enough messages to exceed the budget
        repeat(10) { i ->
            history.addUser("Message number $i with some extra text")
            history.addAssistant("Reply to message $i with content")
        }

        val messages = history.toMessageList()
        // System message should always be preserved
        assertEquals(ChatMessage.Role.System, messages.first().role)
        // Should have been trimmed to fit within budget
        assertTrue(messages.size < 21) // 20 user+assistant + 1 system
        assertTrue(TokenCounter.estimate(messages) <= 20)
    }

    @Test
    fun clearRemovesAllMessages() {
        val history = ConversationHistory()
        history.addUser("Hello")
        history.addAssistant("Hi")
        history.clear()

        assertEquals(0, history.size)
        assertTrue(history.messages.isEmpty())
    }

    @Test
    fun messagesReturnsCopy() {
        val history = ConversationHistory()
        history.addUser("Hello")
        val messages = history.messages

        history.addUser("World")
        assertEquals(1, messages.size)
        assertEquals(2, history.size)
    }
}
