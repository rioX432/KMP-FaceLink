package io.github.kmpfacelink.llm

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals

class ChatMessageTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun serializationRoundTrip() {
        val message = ChatMessage(ChatMessage.Role.User, "Hello")
        val encoded = json.encodeToString(ChatMessage.serializer(), message)
        val decoded = json.decodeFromString(ChatMessage.serializer(), encoded)
        assertEquals(message, decoded)
    }

    @Test
    fun roleValues() {
        assertEquals(3, ChatMessage.Role.entries.size)
        assertEquals("System", ChatMessage.Role.System.name)
        assertEquals("User", ChatMessage.Role.User.name)
        assertEquals("Assistant", ChatMessage.Role.Assistant.name)
    }
}
