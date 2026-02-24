package io.github.kmpfacelink.llm

import kotlin.test.Test
import kotlin.test.assertEquals

class TokenCounterTest {

    @Test
    fun estimateEmptyString() {
        assertEquals(0, TokenCounter.estimate(""))
    }

    @Test
    fun estimateShortString() {
        // "Hi" = 2 chars → ceil(2/4) = 1
        assertEquals(1, TokenCounter.estimate("Hi"))
    }

    @Test
    fun estimateExactMultiple() {
        // "test" = 4 chars → 4/4 = 1
        assertEquals(1, TokenCounter.estimate("test"))
    }

    @Test
    fun estimateLongerString() {
        // "Hello, world!" = 13 chars → ceil(13/4) = 4
        assertEquals(4, TokenCounter.estimate("Hello, world!"))
    }

    @Test
    fun estimateMessages() {
        val messages = listOf(
            ChatMessage(ChatMessage.Role.User, "test"),
            ChatMessage(ChatMessage.Role.Assistant, "ok"),
        )
        // "test" → 1 token + 4 overhead = 5
        // "ok" → 1 token + 4 overhead = 5
        // total = 10
        assertEquals(10, TokenCounter.estimate(messages))
    }
}
