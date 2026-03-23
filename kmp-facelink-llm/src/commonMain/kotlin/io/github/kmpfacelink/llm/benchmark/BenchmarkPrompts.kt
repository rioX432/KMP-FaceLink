package io.github.kmpfacelink.llm.benchmark

/**
 * Standard prompt set for reproducible LLM benchmarks.
 *
 * Provides prompts of varying complexity to measure model performance
 * across different workload types.
 */
public object BenchmarkPrompts {

    /** Short casual greeting prompt. */
    public const val SHORT_CASUAL: String = "Hi! How are you today?"

    /** Medium-length conversational question prompt. */
    public const val MEDIUM_CONVERSATIONAL: String =
        "Can you explain the difference between supervised and unsupervised machine learning? " +
            "Please give a brief example of each."

    /** Long analytical prompt requiring multi-step reasoning. */
    public const val LONG_ANALYTICAL: String =
        "Analyze the following scenario step by step: A company has 100 employees. " +
            "Each employee works 8 hours per day, 5 days per week. The company wants to " +
            "reduce total working hours by 20% while maintaining the same output. " +
            "What strategies could they employ, and what are the trade-offs of each approach? " +
            "Consider impacts on employee satisfaction, productivity, and operational costs."

    /**
     * Returns the full standard prompt set for benchmarking.
     *
     * The list contains prompts of increasing complexity:
     * short casual, medium conversational, and long analytical.
     */
    public fun standardSet(): List<String> = listOf(
        SHORT_CASUAL,
        MEDIUM_CONVERSATIONAL,
        LONG_ANALYTICAL,
    )
}
