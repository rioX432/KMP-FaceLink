package io.github.kmpfacelink.voice.asr.internal

import io.github.kmpfacelink.voice.asr.AsrConfig
import io.github.kmpfacelink.voice.asr.AsrEngine
import io.github.kmpfacelink.voice.asr.AsrState
import io.github.kmpfacelink.voice.asr.TranscriptionResult
import io.github.kmpfacelink.voice.audio.AudioData
import io.github.kmpfacelink.voice.tts.internal.platformHttpEngine
import io.ktor.client.HttpClient
import io.ktor.client.request.forms.formData
import io.ktor.client.request.forms.submitFormWithBinaryData
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

private const val OPENAI_TRANSCRIPTION_URL = "https://api.openai.com/v1/audio/transcriptions"

/**
 * OpenAI Whisper cloud ASR engine.
 *
 * Uses /v1/audio/transcriptions endpoint with multipart form data.
 */
internal class OpenAiAsrEngine(private val config: AsrConfig.OpenAiWhisper) : AsrEngine {

    private val _state = MutableStateFlow<AsrState>(AsrState.Idle)
    override val state: StateFlow<AsrState> = _state.asStateFlow()

    private val _transcriptions = MutableSharedFlow<TranscriptionResult>(extraBufferCapacity = 16)
    override val transcriptions: Flow<TranscriptionResult> = _transcriptions.asSharedFlow()

    private val client = HttpClient(platformHttpEngine()) {
        expectSuccess = true
    }

    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun transcribe(audio: AudioData): TranscriptionResult {
        _state.value = AsrState.Processing
        return try {
            val response = client.submitFormWithBinaryData(
                url = OPENAI_TRANSCRIPTION_URL,
                formData = formData {
                    append(
                        "file",
                        audio.bytes,
                        Headers.build {
                            append(HttpHeaders.ContentDisposition, "filename=\"audio.wav\"")
                            append(HttpHeaders.ContentType, "audio/wav")
                        },
                    )
                    append("model", config.model)
                    append("response_format", "verbose_json")
                    config.language?.let { append("language", it) }
                },
            ) {
                header("Authorization", "Bearer ${config.apiKey}")
            }

            val responseText = response.bodyAsText()
            val parsed = json.decodeFromString(WhisperResponse.serializer(), responseText)

            val result = TranscriptionResult(
                text = parsed.text,
                isFinal = true,
                language = parsed.language,
            )

            _state.value = AsrState.Idle
            result
        } catch (e: Exception) {
            _state.value = AsrState.Error(e.message ?: "Transcription failed")
            throw e
        }
    }

    override suspend fun startListening() {
        _state.value = AsrState.Listening
    }

    override suspend fun stopListening() {
        _state.value = AsrState.Idle
    }

    override fun release() {
        client.close()
    }

    @Serializable
    private data class WhisperResponse(
        val text: String,
        val language: String? = null,
    )
}
