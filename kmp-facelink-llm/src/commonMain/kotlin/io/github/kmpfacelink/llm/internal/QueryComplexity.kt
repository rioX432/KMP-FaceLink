package io.github.kmpfacelink.llm.internal

/**
 * Classification of query complexity for hybrid routing decisions.
 *
 * @property score Normalized complexity score (0.0 = simplest, 1.0 = most complex)
 */
internal enum class QueryComplexity(internal val score: Float) {
    SIMPLE(0.2f),
    MODERATE(0.5f),
    COMPLEX(0.8f),
}
