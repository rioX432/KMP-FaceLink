package io.github.kmpfacelink.llm.benchmark

import io.github.kmpfacelink.llm.ConversationHistory
import io.github.kmpfacelink.llm.LlmClient
import io.github.kmpfacelink.llm.LlmState
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LlmBenchmarkRunnerTest {

    @Test
    fun benchmarkCountsTokensCorrectly() = runTest {
        val tokens = listOf("Hello", " ", "world", "!")
        val client = FakeBenchmarkClient(tokens)
        val runner = LlmBenchmarkRunner(client, modelId = "test-model", deviceInfo = "test-device")

        val results = runner.runBenchmark(listOf("Hi"))

        assertEquals(1, results.size)
        val result = results.first()
        assertEquals(tokens.size, result.completionTokens)
        assertEquals("test-model", result.modelId)
        assertEquals("test-device", result.deviceInfo)
    }

    @Test
    fun benchmarkRunsMultiplePrompts() = runTest {
        val client = FakeBenchmarkClient(listOf("ok"))
        val runner = LlmBenchmarkRunner(client, modelId = "test")

        val results = runner.runBenchmark(BenchmarkPrompts.standardSet())

        assertEquals(BenchmarkPrompts.standardSet().size, results.size)
    }

    @Test
    fun benchmarkMeasuresPositiveTiming() = runTest {
        val client = FakeBenchmarkClient(listOf("a", "b", "c"), tokenDelayMs = DELAY_MS)
        val runner = LlmBenchmarkRunner(client, modelId = "test")

        val results = runner.runBenchmark(listOf("prompt"))
        val result = results.first()

        assertTrue(result.totalGenerationMs >= 0L, "Total time should be non-negative")
        assertTrue(result.firstTokenLatencyMs >= 0L, "First token latency should be non-negative")
        assertTrue(
            result.firstTokenLatencyMs <= result.totalGenerationMs,
            "First token latency should not exceed total time",
        )
    }

    @Test
    fun benchmarkCalculatesTokensPerSecond() = runTest {
        val client = FakeBenchmarkClient(listOf("a", "b", "c", "d"), tokenDelayMs = DELAY_MS)
        val runner = LlmBenchmarkRunner(client, modelId = "test")

        val results = runner.runBenchmark(listOf("prompt"))
        val result = results.first()

        assertTrue(result.tokensPerSecond >= 0.0, "TPS should be non-negative")
    }

    @Test
    fun benchmarkReportsZeroMemory() = runTest {
        val client = FakeBenchmarkClient(listOf("token"))
        val runner = LlmBenchmarkRunner(client, modelId = "test")

        val results = runner.runBenchmark(listOf("prompt"))

        assertEquals(0L, results.first().peakMemoryMb)
    }

    @Test
    fun benchmarkHandlesEmptyTokenStream() = runTest {
        val client = FakeBenchmarkClient(emptyList())
        val runner = LlmBenchmarkRunner(client, modelId = "test")

        val results = runner.runBenchmark(listOf("prompt"))
        val result = results.first()

        assertEquals(0, result.completionTokens)
        assertEquals(0.0, result.tokensPerSecond)
    }

    @Test
    fun benchmarkEstimatesPromptTokens() = runTest {
        val client = FakeBenchmarkClient(listOf("ok"))
        val runner = LlmBenchmarkRunner(client, modelId = "test")

        val results = runner.runBenchmark(listOf("Hello world"))
        val result = results.first()

        assertTrue(result.promptTokens > 0, "Prompt tokens should be positive")
    }

    private companion object {
        const val DELAY_MS = 10L
    }
}

/**
 * Fake [LlmClient] for benchmark testing that emits tokens at a controlled rate.
 */
private class FakeBenchmarkClient(
    private val tokens: List<String>,
    private val tokenDelayMs: Long = 0L,
) : LlmClient {
    private val _state = MutableStateFlow<LlmState>(LlmState.Idle)
    override val state: StateFlow<LlmState> = _state.asStateFlow()

    override fun chat(history: ConversationHistory): Flow<String> = flow {
        _state.value = LlmState.Streaming
        for (token in tokens) {
            if (tokenDelayMs > 0L) delay(tokenDelayMs)
            emit(token)
        }
        _state.value = LlmState.Idle
    }

    override suspend fun chatOnce(history: ConversationHistory): String {
        val builder = StringBuilder()
        chat(history).collect { builder.append(it) }
        return builder.toString()
    }

    override fun release() {
        // No-op
    }
}
