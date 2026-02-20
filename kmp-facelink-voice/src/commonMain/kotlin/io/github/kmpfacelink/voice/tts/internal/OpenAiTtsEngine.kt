package io.github.kmpfacelink.voice.tts.internal

import io.github.kmpfacelink.voice.AudioConstants
import io.github.kmpfacelink.voice.audio.AudioData
import io.github.kmpfacelink.voice.audio.AudioFormat
import io.github.kmpfacelink.voice.tts.TtsConfig
import io.github.kmpfacelink.voice.tts.TtsEngine
import io.github.kmpfacelink.voice.tts.TtsResult
import io.github.kmpfacelink.voice.tts.TtsState
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

private const val OPENAI_TTS_URL = "https://api.openai.com/v1/audio/speech"

/**
 * OpenAI TTS engine using /v1/audio/speech endpoint.
 *
 * Returns audio only — no phoneme timing data.
 * Lip sync uses amplitude-based animation as fallback.
 */
internal class OpenAiTtsEngine(private val config: TtsConfig.OpenAiTts) : TtsEngine {

    private val _state = MutableStateFlow<TtsState>(TtsState.Idle)
    override val state: StateFlow<TtsState> = _state.asStateFlow()

    private val client = HttpClient(platformHttpEngine()) {
        expectSuccess = true
    }

    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun synthesize(text: String): TtsResult {
        _state.value = TtsState.Synthesizing
        return try {
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

            val response = client.post(OPENAI_TTS_URL) {
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

            _state.value = TtsState.Idle
            TtsResult(
                audio = AudioData(bytes = audioBytes, format = format, durationMs = durationMs),
                phonemeEvents = emptyList(),
                durationMs = durationMs,
            )
        } catch (e: Exception) {
            _state.value = TtsState.Error(e.message ?: "OpenAI TTS failed")
            throw e
        }
    }

    override fun release() {
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
