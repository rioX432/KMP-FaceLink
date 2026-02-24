package io.github.kmpfacelink.llm

import io.github.kmpfacelink.api.Releasable
import io.github.kmpfacelink.llm.internal.LlmClientFactory
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/**
 * State of an [LlmClient].
 */
public sealed class LlmState {
    /** Client is idle and ready. */
    public data object Idle : LlmState()

    /** Client is streaming a response. */
    public data object Streaming : LlmState()

    /** An error occurred. */
    public data class Error(val message: String) : LlmState()
}

/**
 * LLM streaming client interface.
 *
 * Provides both streaming ([chat]) and single-response ([chatOnce]) APIs
 * for interacting with LLM providers (OpenAI, Anthropic, Gemini).
 *
 * Use [LlmClient.Companion.create] to instantiate from an [LlmConfig].
 */
public interface LlmClient : Releasable {

    /** Current client state. */
    public val state: StateFlow<LlmState>

    /**
     * Sends conversation messages and returns a streaming [Flow] of text chunks.
     *
     * Each emitted string is a token or partial text from the model's response.
     * Collect the flow to receive real-time streaming output.
     *
     * @param history Conversation history to send
     * @return Flow of response text chunks
     */
    public fun chat(history: ConversationHistory): Flow<String>

    /**
     * Sends conversation messages and returns the complete response as a single string.
     *
     * This is a convenience method that collects all streaming chunks internally.
     *
     * @param history Conversation history to send
     * @return Complete response text
     */
    public suspend fun chatOnce(history: ConversationHistory): String

    /** Releases all resources held by this client. */
    public override fun release()

    public companion object {
        /**
         * Creates an [LlmClient] from the given configuration.
         *
         * @param config Provider-specific configuration
         * @return A configured LLM client instance
         */
        public fun create(config: LlmConfig): LlmClient = LlmClientFactory.create(config)
    }
}
