package io.github.kmpfacelink.llm.internal

import io.github.kmpfacelink.llm.ChatMessage
import io.github.kmpfacelink.llm.ConversationHistory
import io.github.kmpfacelink.llm.LlmClient
import io.github.kmpfacelink.llm.LlmConfig
import io.github.kmpfacelink.llm.LlmState
import io.ktor.client.HttpClient
import io.ktor.client.request.parameter
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

private const val GEMINI_API_BASE = "https://generativelanguage.googleapis.com/v1beta/models"

/**
 * Google Gemini API client with streaming via `streamGenerateContent`.
 *
 * Uses the `alt=sse` parameter to receive Server-Sent Events.
 * Each chunk contains `candidates[0].content.parts[0].text`.
 */
internal class GeminiLlmClient(private val config: LlmConfig.Gemini) : LlmClient {

    private val _state = MutableStateFlow<LlmState>(LlmState.Idle)
    override val state: StateFlow<LlmState> = _state.asStateFlow()

    private val client = HttpClient(platformHttpEngine()) {
        expectSuccess = true
    }

    private val json = Json { ignoreUnknownKeys = true }

    override fun chat(history: ConversationHistory): Flow<String> = flow {
        _state.value = LlmState.Streaming
        val url = "$GEMINI_API_BASE/${config.model}:streamGenerateContent"
        val response = client.post(url) {
            parameter("key", config.apiKey)
            parameter("alt", "sse")
            contentType(ContentType.Application.Json)
            setBody(buildRequestBody(history))
        }

        response.sseDataFlow().collect { data ->
            val chunk = json.decodeFromString(StreamChunk.serializer(), data)
            val text = chunk.candidates
                .firstOrNull()
                ?.content
                ?.parts
                ?.firstOrNull()
                ?.text
            if (text != null) emit(text)
        }
    }.onCompletion { cause ->
        _state.value = if (cause == null) {
            LlmState.Idle
        } else {
            LlmState.Error(cause.message ?: "Gemini request failed")
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
        val systemInstruction = allMessages
            .firstOrNull { it.role == ChatMessage.Role.System }
            ?.let { SystemInstruction(parts = listOf(Part(text = it.content))) }

        val contents = allMessages
            .filter { it.role != ChatMessage.Role.System }
            .map { msg ->
                Content(
                    role = if (msg.role == ChatMessage.Role.User) "user" else "model",
                    parts = listOf(Part(text = msg.content)),
                )
            }

        return json.encodeToString(
            Request.serializer(),
            Request(
                contents = contents,
                systemInstruction = systemInstruction,
                generationConfig = GenerationConfig(
                    maxOutputTokens = config.maxTokens,
                    temperature = config.temperature,
                ),
            ),
        )
    }

    @Serializable
    private data class Request(
        val contents: List<Content>,
        @SerialName("system_instruction") val systemInstruction: SystemInstruction? = null,
        @SerialName("generation_config") val generationConfig: GenerationConfig,
    )

    @Serializable
    private data class SystemInstruction(val parts: List<Part>)

    @Serializable
    private data class Content(
        val role: String,
        val parts: List<Part>,
    )

    @Serializable
    private data class Part(val text: String)

    @Serializable
    private data class GenerationConfig(
        @SerialName("max_output_tokens") val maxOutputTokens: Int,
        val temperature: Double,
    )

    @Serializable
    private data class StreamChunk(
        val candidates: List<Candidate> = emptyList(),
    )

    @Serializable
    private data class Candidate(
        val content: CandidateContent? = null,
    )

    @Serializable
    private data class CandidateContent(
        val parts: List<Part> = emptyList(),
    )
}
