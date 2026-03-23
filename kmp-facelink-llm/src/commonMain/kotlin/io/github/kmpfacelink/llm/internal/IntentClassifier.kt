package io.github.kmpfacelink.llm.internal

import io.github.kmpfacelink.llm.ChatMessage
import io.github.kmpfacelink.llm.ConversationHistory
import io.github.kmpfacelink.llm.TokenCounter

/**
 * Heuristic-based intent classifier that estimates query complexity.
 *
 * Analyzes the latest user message and conversation context to produce
 * a [QueryComplexity] classification used for hybrid routing decisions.
 */
internal class IntentClassifier {

    /**
     * Classifies the complexity of the current conversation state.
     *
     * @param history Conversation history (latest user message + context)
     * @return Classified query complexity
     */
    internal fun classify(history: ConversationHistory): QueryComplexity {
        val latestUserMessage = history.messages
            .lastOrNull { it.role == ChatMessage.Role.User }
            ?: return QueryComplexity.SIMPLE

        val text = latestUserMessage.content.lowercase()
        val tokenCount = TokenCounter.estimate(latestUserMessage.content)
        val conversationDepth = history.size

        return when {
            isComplexQuery(text, tokenCount, conversationDepth) -> QueryComplexity.COMPLEX
            isSimpleQuery(text, tokenCount) -> QueryComplexity.SIMPLE
            else -> QueryComplexity.MODERATE
        }
    }

    private fun isSimpleQuery(text: String, tokenCount: Int): Boolean {
        if (tokenCount <= VERY_SHORT_THRESHOLD) return true
        if (SIMPLE_KEYWORDS.any { text.contains(it) } && tokenCount < SHORT_MESSAGE_THRESHOLD) {
            return true
        }
        return false
    }

    private fun isComplexQuery(text: String, tokenCount: Int, conversationDepth: Int): Boolean {
        if (COMPLEX_KEYWORDS.any { text.contains(it) }) return true
        if (conversationDepth > DEEP_CONVERSATION_THRESHOLD && tokenCount > MODERATE_TOKEN_THRESHOLD) {
            return true
        }
        return tokenCount > LONG_MESSAGE_THRESHOLD
    }

    internal companion object {
        private const val VERY_SHORT_THRESHOLD = 5
        private const val SHORT_MESSAGE_THRESHOLD = 20
        private const val MODERATE_TOKEN_THRESHOLD = 30
        private const val LONG_MESSAGE_THRESHOLD = 200
        private const val DEEP_CONVERSATION_THRESHOLD = 10

        private val SIMPLE_KEYWORDS = listOf(
            "hello", "hi", "hey", "thanks", "thank you", "bye", "goodbye",
            "weather", "time", "date", "yes", "no", "ok", "okay",
        )

        private val COMPLEX_KEYWORDS = listOf(
            "explain", "analyze", "analyse", "compare", "contrast",
            "create", "generate", "write", "compose", "design",
            "summarize", "summarise", "evaluate", "implement",
            "debug", "refactor", "optimize", "review",
        )
    }
}
