package io.github.kmpfacelink.llm

/**
 * Approximate token counter for context window management.
 *
 * Uses character-based estimation (1 token ≈ 4 characters) which is a
 * reasonable heuristic across English and CJK text. For precise counts,
 * use a provider-specific tokenizer externally.
 */
public object TokenCounter {
    private const val CHARS_PER_TOKEN = 4

    /** Estimates the token count for a single string. */
    public fun estimate(text: String): Int = (text.length + CHARS_PER_TOKEN - 1) / CHARS_PER_TOKEN

    /** Estimates the total token count for a list of messages. */
    public fun estimate(messages: List<ChatMessage>): Int =
        messages.sumOf { estimate(it.content) + ROLE_OVERHEAD }

    internal const val ROLE_OVERHEAD = 4
}
