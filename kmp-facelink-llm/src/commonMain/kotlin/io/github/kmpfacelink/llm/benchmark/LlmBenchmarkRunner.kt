package io.github.kmpfacelink.llm.benchmark

import io.github.kmpfacelink.llm.ConversationHistory
import io.github.kmpfacelink.llm.LlmClient
import io.github.kmpfacelink.llm.TokenCounter
import kotlin.time.TimeSource

/**
 * Runs benchmarks against an [LlmClient] and collects performance metrics.
 *
 * Measures token throughput, first-token latency, and total generation time.
 * Platform-specific metrics (memory, thermal) are not measured and default to 0.
 *
 * @property client The LLM client to benchmark
 * @property modelId Identifier for the model being benchmarked
 * @property deviceInfo Human-readable device description for result tagging
 */
public class LlmBenchmarkRunner(
    private val client: LlmClient,
    private val modelId: String = "unknown",
    private val deviceInfo: String = "unknown",
) {

    /**
     * Runs a benchmark for each prompt and returns the collected results.
     *
     * Each prompt is sent as a single-turn conversation. The runner measures:
     * - Time to first token emission
     * - Total generation wall-clock time
     * - Number of completion tokens
     * - Tokens-per-second throughput
     *
     * @param prompts List of prompts to benchmark
     * @return List of benchmark results, one per prompt
     */
    public suspend fun runBenchmark(prompts: List<String>): List<LlmBenchmarkResult> =
        prompts.map { prompt -> benchmarkSingle(prompt) }

    private suspend fun benchmarkSingle(prompt: String): LlmBenchmarkResult {
        val history = ConversationHistory()
        history.addUser(prompt)

        val timeSource = TimeSource.Monotonic
        val startMark = timeSource.markNow()
        var firstTokenMark: TimeSource.Monotonic.ValueTimeMark? = null
        var tokenCount = 0

        client.chat(history).collect {
            if (firstTokenMark == null) {
                firstTokenMark = timeSource.markNow()
            }
            tokenCount++
        }

        val endMark = timeSource.markNow()
        val totalMs = (endMark - startMark).inWholeMilliseconds
        val firstTokenMs = firstTokenMark?.let { (it - startMark).inWholeMilliseconds } ?: totalMs
        val tps = calculateTokensPerSecond(tokenCount, totalMs)
        val promptTokens = TokenCounter.estimate(prompt)

        return LlmBenchmarkResult(
            modelId = modelId,
            tokensPerSecond = tps,
            firstTokenLatencyMs = firstTokenMs,
            totalGenerationMs = totalMs,
            promptTokens = promptTokens,
            completionTokens = tokenCount,
            peakMemoryMb = 0L,
            deviceInfo = deviceInfo,
        )
    }

    private fun calculateTokensPerSecond(tokens: Int, totalMs: Long): Double {
        if (totalMs <= 0L) return 0.0
        return tokens.toDouble() / (totalMs.toDouble() / MS_PER_SECOND)
    }

    private companion object {
        const val MS_PER_SECOND = 1000.0
    }
}
