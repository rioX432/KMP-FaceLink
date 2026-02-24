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
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * OpenAI Chat Completions API client with SSE streaming.
 */
internal class OpenAiLlmClient(private val config: LlmConfig.OpenAi) : LlmClient {

    private val _state = MutableStateFlow<LlmState>(LlmState.Idle)
    override val state: StateFlow<LlmState> = _state.asStateFlow()
    private val chatMutex = Mutex()

    private val client = HttpClient(platformHttpEngine()) {
        expectSuccess = false
    }

    private val json = Json { ignoreUnknownKeys = true }

    override fun chat(history: ConversationHistory): Flow<String> = flow {
        chatMutex.withLock {
            _state.value = LlmState.Streaming
            val response = client.post("${config.baseUrl}/chat/completions") {
                header("Authorization", "Bearer ${config.apiKey}")
                contentType(ContentType.Application.Json)
                setBody(buildRequestBody(history))
            }

            if (!response.status.isSuccess()) {
                val body = response.bodyAsText()
                error("OpenAI API error ${response.status.value}: $body")
            }

            response.sseDataFlow().collect { data ->
                val chunk = json.decodeFromString(StreamChunk.serializer(), data)
                val content = chunk.choices.firstOrNull()?.delta?.content
                if (content != null) emit(content)
            }
        }
    }.onCompletion { cause ->
        _state.value = if (cause == null) {
            LlmState.Idle
        } else {
            LlmState.Error(cause.message ?: "OpenAI request failed")
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

    private fun buildRequestBody(history: ConversationHistory): String =
        json.encodeToString(
            Request.serializer(),
            Request(
                model = config.model,
                messages = history.toMessageList().map { it.toApiMessage() },
                maxTokens = config.maxTokens,
                temperature = config.temperature,
                stream = true,
            ),
        )

    @Serializable
    private data class Request(
        val model: String,
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
    private data class StreamChunk(
        val choices: List<Choice> = emptyList(),
    )

    @Serializable
    private data class Choice(
        val delta: Delta = Delta(),
    )

    @Serializable
    private data class Delta(
        val content: String? = null,
    )

    private fun ChatMessage.toApiMessage(): ApiMessage = ApiMessage(
        role = when (role) {
            ChatMessage.Role.System -> "system"
            ChatMessage.Role.User -> "user"
            ChatMessage.Role.Assistant -> "assistant"
        },
        content = content,
    )
}
