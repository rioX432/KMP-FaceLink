package io.github.kmpfacelink.voice.tts.internal

import io.github.kmpfacelink.voice.AudioConstants
import io.github.kmpfacelink.voice.audio.AudioData
import io.github.kmpfacelink.voice.audio.AudioFormat
import io.github.kmpfacelink.voice.tts.PhonemeEvent
import io.github.kmpfacelink.voice.tts.TtsConfig
import io.github.kmpfacelink.voice.tts.TtsEngine
import io.github.kmpfacelink.voice.tts.TtsResult
import io.github.kmpfacelink.voice.tts.TtsState
import io.ktor.client.HttpClient
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsBytes
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

private const val WAV_HEADER_SIZE = 44

/**
 * VOICEVOX TTS engine.
 *
 * Uses the VOICEVOX local HTTP API for high-quality Japanese TTS
 * with mora-level phoneme timing for precise lip sync.
 *
 * Flow: POST /audio_query → parse mora timing → POST /synthesis → WAV audio
 */
internal class VoicevoxTtsEngine(private val config: TtsConfig.Voicevox) : TtsEngine {

    private val _state = MutableStateFlow<TtsState>(TtsState.Idle)
    override val state: StateFlow<TtsState> = _state.asStateFlow()

    private val baseUrl = "http://${config.host}:${config.port}"

    private val client = HttpClient(platformHttpEngine()) {
        expectSuccess = true
    }

    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun synthesize(text: String): TtsResult {
        _state.value = TtsState.Synthesizing
        return try {
            // Step 1: Create audio query (get phoneme timing)
            val queryResponse = client.post("$baseUrl/audio_query") {
                parameter("text", text)
                parameter("speaker", config.speakerId)
            }
            val queryJson = queryResponse.bodyAsText()
            val audioQuery = json.decodeFromString(AudioQuery.serializer(), queryJson)

            // Extract phoneme events from mora timing
            val phonemeEvents = extractPhonemeEvents(audioQuery)

            // Step 2: Synthesize audio
            val synthesisResponse = client.post("$baseUrl/synthesis") {
                parameter("speaker", config.speakerId)
                contentType(ContentType.Application.Json)
                setBody(queryJson)
            }

            val wavBytes = synthesisResponse.bodyAsBytes()
            // Strip WAV header to get raw PCM
            val pcmBytes = if (wavBytes.size > WAV_HEADER_SIZE) {
                wavBytes.copyOfRange(WAV_HEADER_SIZE, wavBytes.size)
            } else {
                wavBytes
            }

            val format = AudioFormat(
                sampleRate = AudioConstants.SAMPLE_RATE_24K,
                channels = 1,
                bitsPerSample = AudioConstants.BITS_16,
            )
            val durationMs = computeDurationMs(pcmBytes.size, format)

            _state.value = TtsState.Idle
            TtsResult(
                audio = AudioData(bytes = pcmBytes, format = format, durationMs = durationMs),
                phonemeEvents = phonemeEvents,
                durationMs = durationMs,
            )
        } catch (e: Exception) {
            _state.value = TtsState.Error(e.message ?: "VOICEVOX TTS failed")
            throw e
        }
    }

    override fun release() {
        client.close()
    }

    private fun extractPhonemeEvents(audioQuery: AudioQuery): List<PhonemeEvent> {
        val events = mutableListOf<PhonemeEvent>()
        var currentTimeMs = 0.0

        for (phrase in audioQuery.accentPhrases) {
            for (mora in phrase.moras) {
                // Consonant part
                if (mora.consonant != null && mora.consonantLength != null) {
                    val durationMs = mora.consonantLength * AudioConstants.MILLIS_PER_SECOND_DOUBLE
                    events.add(
                        PhonemeEvent(
                            phoneme = mora.consonant,
                            startMs = currentTimeMs.toLong(),
                            endMs = (currentTimeMs + durationMs).toLong(),
                        ),
                    )
                    currentTimeMs += durationMs
                }

                // Vowel part
                val vowelDurationMs = mora.vowelLength * AudioConstants.MILLIS_PER_SECOND_DOUBLE
                events.add(
                    PhonemeEvent(
                        phoneme = mora.vowel,
                        startMs = currentTimeMs.toLong(),
                        endMs = (currentTimeMs + vowelDurationMs).toLong(),
                    ),
                )
                currentTimeMs += vowelDurationMs
            }

            // Pause mora
            phrase.pauseMora?.let { pause ->
                val pauseDurationMs = pause.vowelLength * AudioConstants.MILLIS_PER_SECOND_DOUBLE
                events.add(
                    PhonemeEvent(
                        phoneme = "pau",
                        startMs = currentTimeMs.toLong(),
                        endMs = (currentTimeMs + pauseDurationMs).toLong(),
                    ),
                )
                currentTimeMs += pauseDurationMs
            }
        }

        return events
    }

    private fun computeDurationMs(byteCount: Int, format: AudioFormat): Long {
        val bytesPerSample = format.bitsPerSample / Byte.SIZE_BITS
        val totalSamples = byteCount / bytesPerSample / format.channels
        return (totalSamples.toDouble() * AudioConstants.MILLIS_PER_SECOND_DOUBLE / format.sampleRate).toLong()
    }

    @Serializable
    private data class AudioQuery(
        @SerialName("accent_phrases") val accentPhrases: List<AccentPhrase>,
    )

    @Serializable
    private data class AccentPhrase(
        val moras: List<Mora>,
        @SerialName("pause_mora") val pauseMora: Mora? = null,
    )

    @Serializable
    private data class Mora(
        val text: String = "",
        val consonant: String? = null,
        @SerialName("consonant_length") val consonantLength: Double? = null,
        val vowel: String,
        @SerialName("vowel_length") val vowelLength: Double,
    )
}
