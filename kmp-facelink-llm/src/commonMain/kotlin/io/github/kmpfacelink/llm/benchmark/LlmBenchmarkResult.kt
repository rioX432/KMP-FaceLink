package io.github.kmpfacelink.llm.benchmark

/**
 * Results from a single LLM benchmark run.
 *
 * @property modelId Identifier of the model that was benchmarked
 * @property tokensPerSecond Average token generation throughput
 * @property firstTokenLatencyMs Time from request start to first token emission
 * @property totalGenerationMs Total wall-clock time for the full generation
 * @property promptTokens Estimated number of tokens in the prompt
 * @property completionTokens Number of tokens generated in the response
 * @property peakMemoryMb Peak memory usage in megabytes (0 if not measured)
 * @property deviceInfo Human-readable device description
 */
public data class LlmBenchmarkResult(
    val modelId: String,
    val tokensPerSecond: Double,
    val firstTokenLatencyMs: Long,
    val totalGenerationMs: Long,
    val promptTokens: Int,
    val completionTokens: Int,
    val peakMemoryMb: Long,
    val deviceInfo: String,
)
