package io.github.kmpfacelink.voice.asr.internal

import io.github.kmpfacelink.voice.asr.AsrConfig
import io.github.kmpfacelink.voice.asr.AsrEngine
import io.github.kmpfacelink.voice.asr.AsrState
import io.github.kmpfacelink.voice.asr.TranscriptionResult
import io.github.kmpfacelink.voice.audio.AudioData
import io.github.kmpfacelink.voice.tts.internal.platformHttpEngine
import io.github.kmpfacelink.voice.withEngineStateTransition
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
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * OpenAI Whisper cloud ASR engine.
 *
 * Uses /v1/audio/transcriptions endpoint with multipart form data.
 *
 * **Exception contract for [transcribe]:**
 * - `io.ktor.client.plugins.ResponseException` — HTTP error returned by the API (4xx/5xx).
 * - `kotlinx.io.IOException` — network-level I/O failure.
 * - `kotlinx.serialization.SerializationException` — response body could not be parsed.
 * - `kotlinx.coroutines.CancellationException` — the coroutine was cancelled; always re-thrown.
 */
internal class OpenAiAsrEngine(private val config: AsrConfig.OpenAiWhisper) : AsrEngine {

    private val _state = MutableStateFlow<AsrState>(AsrState.Idle)
    override val state: StateFlow<AsrState> = _state.asStateFlow()

    private val _transcriptions = MutableSharedFlow<TranscriptionResult>(extraBufferCapacity = 16)
    override val transcriptions: Flow<TranscriptionResult> = _transcriptions.asSharedFlow()

    private val client = HttpClient(platformHttpEngine()) {
        expectSuccess = true
    }

    @Volatile private var released = false
    private val releaseMutex = Mutex()

    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun transcribe(audio: AudioData): TranscriptionResult =
        releaseMutex.withLock {
            check(!released) { "Cannot transcribe: engine has been released" }
            performTranscription(audio)
        }

    private suspend fun performTranscription(audio: AudioData): TranscriptionResult =
        withEngineStateTransition(
            state = _state,
            processingState = AsrState.Processing,
            idleState = AsrState.Idle,
            errorState = { AsrState.Error(it) },
        ) {
            executeTranscriptionRequest(audio)
        }

    private suspend fun executeTranscriptionRequest(audio: AudioData): TranscriptionResult {
        val response = client.submitFormWithBinaryData(
            url = "${config.baseUrl}/audio/transcriptions",
            formData = buildFormData(audio),
        ) {
            header("Authorization", "Bearer ${config.apiKey}")
        }
        val responseText = response.bodyAsText()
        val parsed = json.decodeFromString(WhisperResponse.serializer(), responseText)
        return TranscriptionResult(
            text = parsed.text,
            isFinal = true,
            language = parsed.language,
        )
    }

    private fun buildFormData(audio: AudioData) = formData {
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
    }

    override suspend fun startListening() {
        _state.value = AsrState.Listening
    }

    override suspend fun stopListening() {
        _state.value = AsrState.Idle
    }

    override fun release() {
        if (released) return
        released = true
        if (releaseMutex.tryLock()) {
            try {
                client.close()
            } finally {
                releaseMutex.unlock()
            }
        }
        // If the mutex is held by transcribe(), the client will remain open until that
        // call completes. Subsequent transcribe() calls will see the released flag and fail.
    }

    @Serializable
    private data class WhisperResponse(
        val text: String,
        val language: String? = null,
    )
}
