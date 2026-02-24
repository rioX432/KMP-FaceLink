package io.github.kmpfacelink.llm

/**
 * Manages conversation messages with token-aware context window trimming.
 *
 * Automatically prepends the system message from [persona] if a system prompt is configured.
 * When the estimated token count exceeds [maxContextTokens], the oldest non-system messages
 * are dropped to fit within the budget.
 *
 * **Note:** This class is not thread-safe. Callers must ensure that mutations
 * ([addUser], [addAssistant], [clear]) and reads ([toMessageList], [messages])
 * are not called concurrently from different coroutines or threads.
 *
 * @property persona Character configuration with system prompt
 * @property maxContextTokens Maximum estimated tokens for the context window
 */
public class ConversationHistory(
    public val persona: PersonaConfig = PersonaConfig(),
    public val maxContextTokens: Int = DEFAULT_CONTEXT_TOKENS,
) {
    private val _messages = mutableListOf<ChatMessage>()

    /** Current messages in the conversation (excluding the system message). */
    public val messages: List<ChatMessage> get() = _messages.toList()

    /**
     * Returns all messages ready to send to the LLM, including the system message.
     *
     * If the estimated token count exceeds [maxContextTokens], older messages
     * are trimmed from the beginning while preserving the system message.
     */
    public fun toMessageList(): List<ChatMessage> {
        val systemMessages = buildSystemMessages()
        val allMessages = systemMessages + _messages
        return trimToFit(allMessages, systemMessages.size)
    }

    /** Adds a user message to the conversation. */
    public fun addUser(content: String) {
        _messages.add(ChatMessage(role = ChatMessage.Role.User, content = content))
    }

    /** Adds an assistant message to the conversation. */
    public fun addAssistant(content: String) {
        _messages.add(ChatMessage(role = ChatMessage.Role.Assistant, content = content))
    }

    /** Clears all conversation messages (persona is preserved). */
    public fun clear() {
        _messages.clear()
    }

    /** Returns the number of non-system messages. */
    public val size: Int get() = _messages.size

    private fun buildSystemMessages(): List<ChatMessage> {
        if (persona.systemPrompt.isBlank()) return emptyList()
        return listOf(ChatMessage(role = ChatMessage.Role.System, content = persona.systemPrompt))
    }

    private fun trimToFit(messages: List<ChatMessage>, systemCount: Int): List<ChatMessage> {
        var totalTokens = TokenCounter.estimate(messages)
        if (totalTokens <= maxContextTokens) return messages

        val result = messages.toMutableList()
        while (result.size > systemCount + 1 && totalTokens > maxContextTokens) {
            val removed = result.removeAt(systemCount)
            totalTokens -= TokenCounter.estimate(removed.content) + TokenCounter.ROLE_OVERHEAD
        }
        return result
    }

    private companion object {
        const val DEFAULT_CONTEXT_TOKENS = 4096
    }
}
