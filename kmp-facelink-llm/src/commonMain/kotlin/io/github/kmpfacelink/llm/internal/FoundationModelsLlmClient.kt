package io.github.kmpfacelink.llm.internal

import io.github.kmpfacelink.llm.ChatMessage
import io.github.kmpfacelink.llm.ConversationHistory
import io.github.kmpfacelink.llm.FoundationModelsProvider
import io.github.kmpfacelink.llm.LlmClient
import io.github.kmpfacelink.llm.LlmConfig
import io.github.kmpfacelink.llm.LlmState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.onCompletion

/**
 * LLM client backed by Apple Foundation Models framework.
 *
 * Delegates all generation to the [FoundationModelsProvider] which bridges
 * to the Swift FoundationModels framework via SKIE interop.
 *
 * Unlike [OnDeviceLlmClient], no model download or loading is required —
 * the system language model is built into iOS 26+.
 *
 * @param config Foundation Models configuration
 * @param provider Swift-side implementation of the Foundation Models bridge
 */
internal class FoundationModelsLlmClient(
    private val config: LlmConfig.FoundationModels,
    private val provider: FoundationModelsProvider,
) : LlmClient {

    private val _state = MutableStateFlow<LlmState>(LlmState.Idle)
    override val state: StateFlow<LlmState> = _state.asStateFlow()

    override fun chat(history: ConversationHistory): Flow<String> = flow {
        _state.value = LlmState.Streaming
        val prompt = buildPrompt(history)
        provider.streamResponse(
            prompt = prompt,
            instructions = config.instructions,
            maxTokens = config.maxTokens,
            temperature = config.temperature,
        ).collect { emit(it) }
    }.onCompletion { cause ->
        _state.value = if (cause == null) {
            LlmState.Idle
        } else {
            LlmState.Error(cause.message ?: "Foundation Models generation failed")
        }
    }

    override suspend fun chatOnce(history: ConversationHistory): String {
        _state.value = LlmState.Streaming
        return try {
            val prompt = buildPrompt(history)
            val result = provider.respond(
                prompt = prompt,
                instructions = config.instructions,
                maxTokens = config.maxTokens,
                temperature = config.temperature,
            )
            _state.value = LlmState.Idle
            result
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            _state.value = LlmState.Error(e.message ?: "Foundation Models request failed")
            throw e
        }
    }

    override fun release() {
        // No resources to release — sessions are lightweight and GC'd.
    }
}

/**
 * Builds a plain-text prompt from conversation history for Foundation Models.
 *
 * Foundation Models uses its own chat format internally, so we pass a
 * concatenated conversation as a single prompt string. The system instructions
 * are passed separately via the session configuration.
 */
internal fun buildPrompt(history: ConversationHistory): String {
    val messages = history.toMessageList()
    // Skip system messages — they're passed as session instructions
    val userMessages = messages.filter { it.role != ChatMessage.Role.System }
    if (userMessages.isEmpty()) return ""
    return buildString {
        for (message in userMessages) {
            val role = when (message.role) {
                ChatMessage.Role.User -> "User"
                ChatMessage.Role.Assistant -> "Assistant"
                ChatMessage.Role.System -> continue
            }
            append(role)
            append(": ")
            appendLine(message.content)
        }
    }.trimEnd()
}
