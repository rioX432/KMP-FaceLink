package io.github.kmpfacelink.llm.internal

import io.github.kmpfacelink.llm.ChatMessage
import io.github.kmpfacelink.llm.ConversationHistory
import io.github.kmpfacelink.llm.LlmClient
import io.github.kmpfacelink.llm.LlmConfig
import io.github.kmpfacelink.llm.LlmState
import io.ktor.client.HttpClient
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.onCompletion
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

private const val ANTHROPIC_API_URL = "https://api.anthropic.com/v1/messages"
private const val ANTHROPIC_VERSION = "2023-06-01"

/**
 * Anthropic Messages API client with SSE streaming.
 *
 * Anthropic uses a different SSE event format with `content_block_delta` events
 * containing `delta.text` for streamed text content.
 */
internal class AnthropicLlmClient(private val config: LlmConfig.Anthropic) : LlmClient {

    private val _state = MutableStateFlow<LlmState>(LlmState.Idle)
    override val state: StateFlow<LlmState> = _state.asStateFlow()

    private val client = HttpClient(platformHttpEngine()) {
        expectSuccess = true
    }

    private val json = Json { ignoreUnknownKeys = true }

    override fun chat(history: ConversationHistory): Flow<String> = flow {
        _state.value = LlmState.Streaming
        val response = client.post(ANTHROPIC_API_URL) {
            header("x-api-key", config.apiKey)
            header("anthropic-version", ANTHROPIC_VERSION)
            contentType(ContentType.Application.Json)
            setBody(buildRequestBody(history))
        }

        response.sseDataFlow().collect { data ->
            val event = json.decodeFromString(StreamEvent.serializer(), data)
            if (event.type == "content_block_delta") {
                val text = event.delta?.text
                if (text != null) emit(text)
            }
        }
    }.onCompletion { cause ->
        _state.value = if (cause == null) {
            LlmState.Idle
        } else {
            LlmState.Error(cause.message ?: "Anthropic request failed")
        }
    }

    override suspend fun chatOnce(history: ConversationHistory): String {
        val builder = StringBuilder()
        chat(history).collect { builder.append(it) }
        return builder.toString()
    }

    override fun release() {
        client.close()
    }

    private fun buildRequestBody(history: ConversationHistory): String {
        val allMessages = history.toMessageList()
        val systemPrompt = allMessages
            .firstOrNull { it.role == ChatMessage.Role.System }
            ?.content

        val conversationMessages = allMessages
            .filter { it.role != ChatMessage.Role.System }
            .map { it.toApiMessage() }

        return json.encodeToString(
            Request.serializer(),
            Request(
                model = config.model,
                system = systemPrompt,
                messages = conversationMessages,
                maxTokens = config.maxTokens,
                temperature = config.temperature,
                stream = true,
            ),
        )
    }

    @Serializable
    private data class Request(
        val model: String,
        val system: String? = null,
        val messages: List<ApiMessage>,
        @SerialName("max_tokens") val maxTokens: Int,
        val temperature: Double,
        val stream: Boolean,
    )

    @Serializable
    private data class ApiMessage(
        val role: String,
        val content: String,
    )

    @Serializable
    private data class StreamEvent(
        val type: String = "",
        val delta: Delta? = null,
    )

    @Serializable
    private data class Delta(
        val text: String? = null,
    )

    private fun ChatMessage.toApiMessage(): ApiMessage = ApiMessage(
        role = when (role) {
            ChatMessage.Role.System ->
                error("System messages must be extracted before calling toApiMessage()")
            ChatMessage.Role.User -> "user"
            ChatMessage.Role.Assistant -> "assistant"
        },
        content = content,
    )
}
