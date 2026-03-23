package io.github.kmpfacelink.llm.internal

/**
 * Classification of query complexity for hybrid routing decisions.
 *
 * @property score Normalized complexity score (0.0 = simplest, 1.0 = most complex)
 */
internal enum class QueryComplexity(internal val score: Float) {
    /** Simple query that can be handled by the on-device model (e.g., greetings, short Q&A). */
    SIMPLE(0.2f),

    /** Moderately complex query that sits at the routing threshold boundary. */
    MODERATE(0.5f),

    /** Complex query that benefits from cloud model capabilities (e.g., multi-step reasoning). */
    COMPLEX(0.8f),
}
