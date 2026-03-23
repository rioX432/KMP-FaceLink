package io.github.kmpfacelink.llm

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PromptFormatterTest {

    @Test
    fun formatEmptyHistory() {
        val history = ConversationHistory()
        val prompt = formatChatMlPrompt(history)
        assertEquals("<|im_start|>assistant\n", prompt)
    }

    @Test
    fun formatWithSystemPrompt() {
        val history = ConversationHistory(
            persona = PersonaConfig(systemPrompt = "You are a helpful assistant."),
        )
        val prompt = formatChatMlPrompt(history)
        val expected = "<|im_start|>system\n" +
            "You are a helpful assistant.<|im_end|>\n" +
            "<|im_start|>assistant\n"
        assertEquals(expected, prompt)
    }

    @Test
    fun formatWithUserMessage() {
        val history = ConversationHistory()
        history.addUser("Hello!")
        val prompt = formatChatMlPrompt(history)
        assertTrue(prompt.contains("<|im_start|>user\nHello!<|im_end|>\n"))
        assertTrue(prompt.endsWith("<|im_start|>assistant\n"))
    }

    @Test
    fun formatFullConversation() {
        val history = ConversationHistory(
            persona = PersonaConfig(systemPrompt = "Be concise."),
        )
        history.addUser("Hi")
        history.addAssistant("Hello!")
        history.addUser("How are you?")
        val prompt = formatChatMlPrompt(history)

        val expected = "<|im_start|>system\n" +
            "Be concise.<|im_end|>\n" +
            "<|im_start|>user\nHi<|im_end|>\n" +
            "<|im_start|>assistant\nHello!<|im_end|>\n" +
            "<|im_start|>user\nHow are you?<|im_end|>\n" +
            "<|im_start|>assistant\n"
        assertEquals(expected, prompt)
    }

    /**
     * Replicates the ChatML formatting logic for testing purposes.
     * This mirrors [OnDeviceLlmClient.formatPrompt] so tests remain in commonTest
     * without depending on Android-specific code.
     */
    private fun formatChatMlPrompt(history: ConversationHistory): String {
        val sb = StringBuilder()
        for (message in history.toMessageList()) {
            val role = when (message.role) {
                ChatMessage.Role.System -> "system"
                ChatMessage.Role.User -> "user"
                ChatMessage.Role.Assistant -> "assistant"
            }
            sb.append("<|im_start|>")
            sb.append(role)
            sb.append('\n')
            sb.append(message.content)
            sb.append("<|im_end|>\n")
        }
        sb.append("<|im_start|>assistant\n")
        return sb.toString()
    }
}
