package io.github.kmpfacelink.llm

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs

class OnDeviceLlmClientTest {

    @Test
    fun initialStateIsIdle() {
        val client = createTestClient()
        assertIs<LlmState.Idle>(client.state.value)
        client.release()
    }

    @Test
    fun chatOnceCollectsAllTokens() = runTest {
        val client = createTestClient(tokens = listOf("Hello", " world", "!"))
        val result = client.chatOnce(simpleHistory())
        assertEquals("Hello world!", result)
        client.release()
    }

    @Test
    fun chatStreamsTokens() = runTest {
        val tokens = listOf("A", "B", "C")
        val client = createTestClient(tokens = tokens)
        val collected = mutableListOf<String>()
        client.chat(simpleHistory()).collect { collected.add(it) }
        assertEquals(tokens, collected)
        client.release()
    }

    @Test
    fun stateReturnsToIdleAfterChat() = runTest {
        val client = createTestClient(tokens = listOf("ok"))
        client.chatOnce(simpleHistory())
        assertIs<LlmState.Idle>(client.state.value)
        client.release()
    }

    @Test
    fun stateBecomesErrorOnFailure() = runTest {
        val client = createTestClient(shouldFail = true)
        assertFailsWith<RuntimeException> {
            client.chatOnce(simpleHistory())
        }
        assertIs<LlmState.Error>(client.state.value)
        client.release()
    }

    @Test
    fun modelNotDownloadedThrows() = runTest {
        val client = createTestClient(modelDownloaded = false)
        assertFailsWith<IllegalStateException> {
            client.chatOnce(simpleHistory())
        }
        client.release()
    }

    private fun simpleHistory(): ConversationHistory {
        val history = ConversationHistory()
        history.addUser("Hello")
        return history
    }

    private fun createTestClient(
        tokens: List<String> = listOf("test"),
        shouldFail: Boolean = false,
        modelDownloaded: Boolean = true,
    ): LlmClient {
        val engine = StubInferenceEngine(tokens, shouldFail)
        val modelManager = StubModelManager(modelDownloaded)
        return StubOnDeviceLlmClient(
            config = LlmConfig.OnDevice(modelId = "gemma-3-1b-int4"),
            engine = engine,
            modelManager = modelManager,
        )
    }
}

/**
 * Fake [InferenceEngine] for testing that emits predefined tokens.
 */
class StubInferenceEngine(
    private val tokens: List<String>,
    private val shouldFail: Boolean,
) : InferenceEngine {
    private var loaded = false

    override suspend fun load(modelPath: String) {
        loaded = true
    }

    override fun generate(prompt: String, maxTokens: Int, temperature: Double): Flow<String> =
        flow {
            if (shouldFail) error("Generation failed")
            tokens.forEach { emit(it) }
        }

    override suspend fun unload() {
        loaded = false
    }

    override val isLoaded: Boolean get() = loaded
}

/**
 * Fake [ModelManager] for testing.
 */
class StubModelManager(
    private val hasModel: Boolean,
) : ModelManager {
    override fun download(modelInfo: ModelInfo): Flow<DownloadProgress> = flow { }
    override suspend fun delete(modelId: String): Boolean = true
    override suspend fun listCached(): List<String> = if (hasModel) listOf("gemma-3-1b-int4") else emptyList()
    override suspend fun isDownloaded(modelId: String): Boolean = hasModel
    override suspend fun getModelPath(modelId: String): String? =
        if (hasModel) "/fake/path/model.gguf" else null
}

/**
 * Minimal on-device LLM client for common tests.
 * Mirrors the real [OnDeviceLlmClient] state management without Android-specific code.
 */
class StubOnDeviceLlmClient(
    private val config: LlmConfig.OnDevice,
    private val engine: InferenceEngine,
    private val modelManager: ModelManager,
) : LlmClient {
    private val _state = kotlinx.coroutines.flow.MutableStateFlow<LlmState>(LlmState.Idle)
    override val state: kotlinx.coroutines.flow.StateFlow<LlmState> = _state

    override fun chat(history: ConversationHistory): Flow<String> = flow {
        _state.value = LlmState.Streaming
        try {
            ensureModelLoaded()
            val prompt = buildPrompt(history)
            engine.generate(prompt, config.maxTokens, config.temperature).collect { emit(it) }
            _state.value = LlmState.Idle
        } catch (e: Exception) {
            _state.value = LlmState.Error(e.message ?: "Unknown error")
            throw e
        }
    }

    override suspend fun chatOnce(history: ConversationHistory): String {
        val builder = StringBuilder()
        chat(history).collect { builder.append(it) }
        return builder.toString()
    }

    override fun release() {
        // No-op for test client
    }

    private suspend fun ensureModelLoaded() {
        if (engine.isLoaded) return
        val path = modelManager.getModelPath(config.modelId)
            ?: error("Model ${config.modelId} is not downloaded.")
        engine.load(path)
    }

    private fun buildPrompt(history: ConversationHistory): String {
        val sb = StringBuilder()
        for (message in history.toMessageList()) {
            val role = when (message.role) {
                ChatMessage.Role.System -> "system"
                ChatMessage.Role.User -> "user"
                ChatMessage.Role.Assistant -> "assistant"
            }
            sb.append("<|im_start|>$role\n${message.content}<|im_end|>\n")
        }
        sb.append("<|im_start|>assistant\n")
        return sb.toString()
    }
}
