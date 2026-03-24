package io.github.kmpfacelink.llm.internal

import io.github.kmpfacelink.llm.ChatMessage
import io.github.kmpfacelink.llm.ConversationHistory

/**
 * Formats a [ConversationHistory] into a ChatML prompt string
 * suitable for instruction-tuned GGUF models.
 *
 * Shared across Android (llama.cpp) and iOS (MLX) on-device clients.
 */
internal fun formatChatMlPrompt(history: ConversationHistory): String {
    val sb = StringBuilder()
    for (message in history.toMessageList()) {
        appendChatMlMessage(sb, message)
    }
    sb.append("<|im_start|>assistant\n")
    return sb.toString()
}

private fun appendChatMlMessage(sb: StringBuilder, message: ChatMessage) {
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
