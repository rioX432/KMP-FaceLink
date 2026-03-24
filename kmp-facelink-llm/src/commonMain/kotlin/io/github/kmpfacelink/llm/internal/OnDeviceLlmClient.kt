package io.github.kmpfacelink.llm.internal

import io.github.kmpfacelink.llm.ConversationHistory
import io.github.kmpfacelink.llm.InferenceEngine
import io.github.kmpfacelink.llm.LlmClient
import io.github.kmpfacelink.llm.LlmConfig
import io.github.kmpfacelink.llm.LlmState
import io.github.kmpfacelink.llm.ModelManager
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * On-device LLM client that uses a local [InferenceEngine] for text generation.
 *
 * Manages model loading lifecycle and delegates generation to the engine.
 * Follows the same state management pattern as cloud-based LLM clients.
 *
 * Used on Android (llama.cpp). On iOS 26+, prefer [FoundationModelsLlmClient].
 *
 * @param config On-device configuration with model ID and generation parameters
 * @param engine Platform-specific inference engine
 * @param modelManager Model download and cache manager
 */
internal class OnDeviceLlmClient(
    private val config: LlmConfig.OnDevice,
    private val engine: InferenceEngine,
    private val modelManager: ModelManager,
) : LlmClient {

    private val _state = MutableStateFlow<LlmState>(LlmState.Idle)
    override val state: StateFlow<LlmState> = _state.asStateFlow()
    private val chatMutex = Mutex()

    override fun chat(history: ConversationHistory): Flow<String> = flow {
        chatMutex.withLock {
            _state.value = LlmState.Streaming
            ensureModelLoaded()
            val prompt = formatChatMlPrompt(history)
            engine.generate(prompt, config.maxTokens, config.temperature).collect { token ->
                emit(token)
            }
        }
    }.onCompletion { cause ->
        _state.value = if (cause == null) {
            LlmState.Idle
        } else {
            LlmState.Error(cause.message ?: "On-device generation failed")
        }
    }

    override suspend fun chatOnce(history: ConversationHistory): String {
        val builder = StringBuilder()
        chat(history).collect { builder.append(it) }
        return builder.toString()
    }

    override fun release() {
        // Engine unload is a suspend function; release is best-effort synchronous.
        // Callers needing graceful shutdown should call engine.unload() directly.
    }

    private suspend fun ensureModelLoaded() {
        if (engine.isLoaded) return
        val modelPath = modelManager.getModelPath(config.modelId)
            ?: error("Model ${config.modelId} is not downloaded. Download it first via ModelManager.")
        engine.load(modelPath)
    }
}
