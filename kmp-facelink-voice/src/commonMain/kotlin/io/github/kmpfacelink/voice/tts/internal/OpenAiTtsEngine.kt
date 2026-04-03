package io.github.kmpfacelink.voice.tts.internal

import io.github.kmpfacelink.networking.http.platformHttpEngine
import io.github.kmpfacelink.voice.AudioConstants
import io.github.kmpfacelink.voice.audio.AudioData
import io.github.kmpfacelink.voice.audio.AudioFormat
import io.github.kmpfacelink.voice.tts.TtsConfig
import io.github.kmpfacelink.voice.tts.TtsEngine
import io.github.kmpfacelink.voice.tts.TtsResult
import io.github.kmpfacelink.voice.tts.TtsState
import io.github.kmpfacelink.voice.withEngineStateTransition
import io.ktor.client.HttpClient
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsBytes
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * OpenAI TTS engine using /v1/audio/speech endpoint.
 *
 * Returns audio only — no phoneme timing data.
 * Lip sync uses amplitude-based animation as fallback.
 *
 * **Exception contract for [synthesize]:**
 * - `io.ktor.client.plugins.ResponseException` — HTTP error returned by the API (4xx/5xx).
 * - `kotlinx.serialization.SerializationException` — request serialization or response parsing
 *   failed.
 * - `kotlinx.coroutines.CancellationException` — the coroutine was cancelled; always re-thrown.
 * The catch clause intentionally keeps a generic `Exception` catch to avoid leaking internal
 * Ktor/serialization types into the public [TtsEngine] interface signature.
 */
internal class OpenAiTtsEngine(private val config: TtsConfig.OpenAiTts) : TtsEngine {

    private val _state = MutableStateFlow<TtsState>(TtsState.Idle)
    override val state: StateFlow<TtsState> = _state.asStateFlow()

    private val client = HttpClient(platformHttpEngine()) {
        expectSuccess = true
    }

    @Volatile private var released = false

    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun synthesize(text: String): TtsResult = withEngineStateTransition(
        state = _state,
        processingState = TtsState.Synthesizing,
        idleState = TtsState.Idle,
        errorState = { TtsState.Error(it) },
    ) {
        val requestBody = json.encodeToString(
            OpenAiTtsRequest.serializer(),
            OpenAiTtsRequest(
                model = config.model,
                input = text,
                voice = config.voice,
                speed = config.speed,
                responseFormat = "pcm",
            ),
        )

        val response = client.post("${config.baseUrl}/audio/speech") {
            header("Authorization", "Bearer ${config.apiKey}")
            contentType(ContentType.Application.Json)
            setBody(requestBody)
        }

        val audioBytes = response.bodyAsBytes()
        val format = AudioFormat(
            sampleRate = AudioConstants.SAMPLE_RATE_24K,
            channels = 1,
            bitsPerSample = AudioConstants.BITS_16,
        )
        val durationMs = computeDurationMs(audioBytes.size, format)

        TtsResult(
            audio = AudioData(bytes = audioBytes, format = format, durationMs = durationMs),
            phonemeEvents = emptyList(),
            durationMs = durationMs,
        )
    }

    override fun release() {
        if (released) return
        released = true
        client.close()
    }

    private fun computeDurationMs(byteCount: Int, format: AudioFormat): Long {
        val bytesPerSample = format.bitsPerSample / Byte.SIZE_BITS
        val totalSamples = byteCount / bytesPerSample / format.channels
        return totalSamples.toLong() * AudioConstants.MILLIS_PER_SECOND / format.sampleRate
    }

    @Serializable
    private data class OpenAiTtsRequest(
        val model: String,
        val input: String,
        val voice: String,
        val speed: Float,
        @Suppress("PropertyName")
        val responseFormat: String,
    )
}
