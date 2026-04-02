package io.github.kmpfacelink.voice.tts.internal

import io.github.kmpfacelink.voice.AudioConstants
import io.github.kmpfacelink.voice.audio.AudioData
import io.github.kmpfacelink.voice.audio.AudioFormat
import io.github.kmpfacelink.voice.tts.PhonemeEvent
import io.github.kmpfacelink.voice.tts.TtsConfig
import io.github.kmpfacelink.voice.tts.TtsEngine
import io.github.kmpfacelink.voice.tts.TtsResult
import io.github.kmpfacelink.voice.tts.TtsState
import io.github.kmpfacelink.voice.withEngineStateTransition
import io.ktor.client.HttpClient
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

/**
 * ElevenLabs TTS engine with character-level timestamp support.
 *
 * Uses the /v1/text-to-speech/{voice_id}/with-timestamps endpoint
 * to get character alignment data for approximate phoneme lip sync.
 */
@OptIn(ExperimentalEncodingApi::class)
internal class ElevenLabsTtsEngine(private val config: TtsConfig.ElevenLabs) : TtsEngine {

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
            ElevenLabsRequest.serializer(),
            ElevenLabsRequest(
                text = text,
                modelId = config.modelId,
                voiceSettings = VoiceSettings(
                    stability = config.stability,
                    similarityBoost = config.similarityBoost,
                ),
            ),
        )

        val response = client.post(
            "${config.baseUrl}/text-to-speech/${config.voiceId}/with-timestamps",
        ) {
            header("xi-api-key", config.apiKey)
            contentType(ContentType.Application.Json)
            setBody(requestBody)
        }

        val responseText = response.bodyAsText()
        val parsed = json.decodeFromString(ElevenLabsResponse.serializer(), responseText)

        val audioBytes = Base64.decode(parsed.audioBase64)
        val format = AudioFormat(
            sampleRate = AudioConstants.SAMPLE_RATE_44K,
            channels = 1,
            bitsPerSample = AudioConstants.BITS_16,
        )
        val durationMs = computeDurationMs(audioBytes.size, format)

        val phonemeEvents = parsed.alignment?.let { alignment ->
            buildPhonemeEvents(alignment)
        } ?: emptyList()

        TtsResult(
            audio = AudioData(bytes = audioBytes, format = format, durationMs = durationMs),
            phonemeEvents = phonemeEvents,
            durationMs = durationMs,
        )
    }

    override fun release() {
        if (released) return
        released = true
        client.close()
    }

    private fun buildPhonemeEvents(
        alignment: Alignment,
    ): List<PhonemeEvent> {
        if (alignment.characters.isEmpty()) return emptyList()

        return alignment.characters.mapIndexedNotNull { index, char ->
            if (char.isWhitespace()) return@mapIndexedNotNull null
            val startMs = (alignment.characterStartTimesSeconds[index] * AudioConstants.MILLIS_PER_SECOND).toLong()
            val endMs = (alignment.characterEndTimesSeconds[index] * AudioConstants.MILLIS_PER_SECOND).toLong()
            PhonemeEvent(
                phoneme = charToApproximatePhoneme(char),
                startMs = startMs,
                endMs = endMs,
            )
        }
    }

    private fun charToApproximatePhoneme(char: Char): String =
        CHAR_TO_PHONEME[char.lowercaseChar()] ?: "sil"

    companion object {
        private val CHAR_TO_PHONEME: Map<Char, String> = mapOf(
            'a' to "a", 'e' to "e", 'i' to "i", 'o' to "o", 'u' to "u",
            'p' to "m", 'b' to "m", 'm' to "m",
            'f' to "f", 'v' to "f",
            't' to "t", 'd' to "t",
            'k' to "k", 'g' to "k", 'c' to "k", 'q' to "k", 'x' to "k",
            's' to "s", 'z' to "s",
            'n' to "n", 'l' to "n",
            'r' to "r", 'w' to "w", 'h' to "h",
            'j' to "y", 'y' to "y",
        )
    }

    private fun computeDurationMs(byteCount: Int, format: AudioFormat): Long {
        val bytesPerSample = format.bitsPerSample / Byte.SIZE_BITS
        val totalSamples = byteCount / bytesPerSample / format.channels
        return totalSamples.toLong() * AudioConstants.MILLIS_PER_SECOND / format.sampleRate
    }

    @Serializable
    private data class ElevenLabsRequest(
        val text: String,
        @SerialName("model_id") val modelId: String,
        @SerialName("voice_settings") val voiceSettings: VoiceSettings,
    )

    @Serializable
    private data class VoiceSettings(
        val stability: Float,
        @SerialName("similarity_boost") val similarityBoost: Float,
    )

    @Serializable
    private data class ElevenLabsResponse(
        @SerialName("audio_base64") val audioBase64: String,
        val alignment: Alignment? = null,
    )

    @Serializable
    private data class Alignment(
        val characters: List<Char>,
        @SerialName("character_start_times_seconds") val characterStartTimesSeconds: List<Double>,
        @SerialName("character_end_times_seconds") val characterEndTimesSeconds: List<Double>,
    )
}
