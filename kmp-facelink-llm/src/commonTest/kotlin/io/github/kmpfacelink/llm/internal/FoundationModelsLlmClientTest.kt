package io.github.kmpfacelink.llm.internal

import io.github.kmpfacelink.llm.ConversationHistory
import io.github.kmpfacelink.llm.FoundationModelsProvider
import io.github.kmpfacelink.llm.LlmConfig
import io.github.kmpfacelink.llm.LlmState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs

class FoundationModelsLlmClientTest {

    @Test
    fun initialStateIsIdle() {
        val client = createClient()
        assertIs<LlmState.Idle>(client.state.value)
    }

    @Test
    fun chatOnceReturnsFullResponse() = runTest {
        val client = createClient(respondResult = "Hello from Foundation Models!")
        val history = simpleHistory()
        val result = client.chatOnce(history)
        assertEquals("Hello from Foundation Models!", result)
    }

    @Test
    fun chatOnceReturnsToIdleState() = runTest {
        val client = createClient(respondResult = "ok")
        client.chatOnce(simpleHistory())
        assertIs<LlmState.Idle>(client.state.value)
    }

    @Test
    fun chatOnceErrorSetsErrorState() = runTest {
        val client = createClient(shouldFail = true)
        assertFailsWith<RuntimeException> {
            client.chatOnce(simpleHistory())
        }
        assertIs<LlmState.Error>(client.state.value)
    }

    @Test
    fun chatStreamsTokens() = runTest {
        val tokens = listOf("Hello", " ", "world")
        val client = createClient(streamTokens = tokens)
        val collected = client.chat(simpleHistory()).toList()
        assertEquals(tokens, collected)
    }

    @Test
    fun chatStreamReturnsToIdle() = runTest {
        val client = createClient(streamTokens = listOf("ok"))
        client.chat(simpleHistory()).toList()
        assertIs<LlmState.Idle>(client.state.value)
    }

    @Test
    fun chatStreamErrorSetsErrorState() = runTest {
        val client = createClient(shouldFail = true)
        assertFailsWith<RuntimeException> {
            client.chat(simpleHistory()).toList()
        }
        assertIs<LlmState.Error>(client.state.value)
    }

    @Test
    fun instructionsPassedToProvider() = runTest {
        var capturedInstructions = ""
        val provider = object : FakeFoundationModelsProvider() {
            override suspend fun respond(
                prompt: String,
                instructions: String,
                maxTokens: Int,
                temperature: Double,
            ): String {
                capturedInstructions = instructions
                return "ok"
            }
        }
        val config = LlmConfig.FoundationModels(instructions = "Be helpful")
        val client = FoundationModelsLlmClient(config, provider)
        client.chatOnce(simpleHistory())
        assertEquals("Be helpful", capturedInstructions)
    }

    @Test
    fun promptExcludesSystemMessages() = runTest {
        var capturedPrompt = ""
        val provider = object : FakeFoundationModelsProvider() {
            override suspend fun respond(
                prompt: String,
                instructions: String,
                maxTokens: Int,
                temperature: Double,
            ): String {
                capturedPrompt = prompt
                return "ok"
            }
        }
        val config = LlmConfig.FoundationModels()
        val client = FoundationModelsLlmClient(config, provider)
        val history = ConversationHistory(
            persona = io.github.kmpfacelink.llm.PersonaConfig(
                systemPrompt = "You are a cat.",
            ),
        )
        history.addUser("Hello")
        client.chatOnce(history)
        // System message should not appear in prompt
        assertEquals("User: Hello", capturedPrompt)
    }

    @Test
    fun releaseIsNoOp() {
        val client = createClient()
        // Should not throw
        client.release()
        assertIs<LlmState.Idle>(client.state.value)
    }

    private fun simpleHistory(): ConversationHistory {
        val history = ConversationHistory()
        history.addUser("Hello")
        return history
    }

    private fun createClient(
        respondResult: String = "test",
        streamTokens: List<String> = listOf("test"),
        shouldFail: Boolean = false,
    ): FoundationModelsLlmClient {
        val provider = FakeFoundationModelsProvider(
            respondResult = respondResult,
            streamTokens = streamTokens,
            shouldFail = shouldFail,
        )
        val config = LlmConfig.FoundationModels()
        return FoundationModelsLlmClient(config, provider)
    }
}

/**
 * Fake [FoundationModelsProvider] for testing.
 */
private open class FakeFoundationModelsProvider(
    private val respondResult: String = "test",
    private val streamTokens: List<String> = listOf("test"),
    private val shouldFail: Boolean = false,
) : FoundationModelsProvider {

    override fun isAvailable(): Boolean = true

    override suspend fun respond(
        prompt: String,
        instructions: String,
        maxTokens: Int,
        temperature: Double,
    ): String {
        if (shouldFail) error("Foundation Models unavailable")
        return respondResult
    }

    override fun streamResponse(
        prompt: String,
        instructions: String,
        maxTokens: Int,
        temperature: Double,
    ): Flow<String> = flow {
        if (shouldFail) error("Foundation Models unavailable")
        streamTokens.forEach { emit(it) }
    }
}
