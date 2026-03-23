package io.github.kmpfacelink.llm.internal

import io.github.kmpfacelink.llm.ChatMessage
import io.github.kmpfacelink.llm.ConversationHistory
import io.github.kmpfacelink.llm.InferenceEngine
import io.github.kmpfacelink.llm.LlmClient
import io.github.kmpfacelink.llm.LlmConfig
import io.github.kmpfacelink.llm.LlmState
import io.github.kmpfacelink.llm.ModelManager
import io.github.kmpfacelink.llm.ModelRegistry
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
 * @param config On-device configuration with model ID and generation parameters
 * @param engine Platform-specific inference engine (e.g. llama.cpp on Android)
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
            val prompt = formatPrompt(history)
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

    internal companion object {
        /**
         * Formats a [ConversationHistory] into a chat-ML style prompt string
         * suitable for instruction-tuned GGUF models.
         */
        internal fun formatPrompt(history: ConversationHistory): String {
            val sb = StringBuilder()
            for (message in history.toMessageList()) {
                appendMessage(sb, message)
            }
            sb.append("<|im_start|>assistant\n")
            return sb.toString()
        }

        private fun appendMessage(sb: StringBuilder, message: ChatMessage) {
            val role = when (message.role) {
                ChatMessage.Role.System -> "system"
                ChatMessage.Role.User -> "user"
                ChatMessage.Role.Assistant -> "assistant"
            }
            sb.append("<|im_start|>")
            sb.append(role)
            sb.append('\n')
            sb.append(message.content)
            sb.append("<|im_end|>\n")
        }

        /**
         * Creates an [OnDeviceLlmClient] with the default Android inference engine.
         */
        internal fun createDefault(
            config: LlmConfig.OnDevice,
            modelManager: ModelManager,
        ): OnDeviceLlmClient {
            val modelInfo = ModelRegistry.findById(config.modelId)
            requireNotNull(modelInfo) { "Unknown model ID: ${config.modelId}" }
            val engine = AndroidInferenceEngine()
            return OnDeviceLlmClient(config, engine, modelManager)
        }
    }
}
