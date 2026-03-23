package io.github.kmpfacelink.voice.tts

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class TtsConfigTest {

    // --- OpenAiTts ---

    @Test
    fun openAiTtsDefaultVoiceIsAlloy() {
        val config = TtsConfig.OpenAiTts(apiKey = "key")
        assertEquals("alloy", config.voice)
    }

    @Test
    fun openAiTtsDefaultModelIsTts1() {
        val config = TtsConfig.OpenAiTts(apiKey = "key")
        assertEquals("tts-1", config.model)
    }

    @Test
    fun openAiTtsDefaultSpeedIs1() {
        val config = TtsConfig.OpenAiTts(apiKey = "key")
        assertEquals(1.0f, config.speed)
    }

    @Test
    fun openAiTtsDefaultBaseUrl() {
        val config = TtsConfig.OpenAiTts(apiKey = "key")
        assertEquals("https://api.openai.com/v1", config.baseUrl)
    }

    @Test
    fun openAiTtsCustomBaseUrl() {
        val config = TtsConfig.OpenAiTts(apiKey = "key", baseUrl = "https://proxy.example.com/v1")
        assertEquals("https://proxy.example.com/v1", config.baseUrl)
    }

    @Test
    fun openAiTtsApiKeyIsStored() {
        val config = TtsConfig.OpenAiTts(apiKey = "sk-test-key")
        assertEquals("sk-test-key", config.apiKey)
    }

    @Test
    fun openAiTtsCustomValuesAreStored() {
        val config = TtsConfig.OpenAiTts(
            apiKey = "key",
            voice = "nova",
            model = "tts-1-hd",
            speed = 1.5f,
        )
        assertEquals("nova", config.voice)
        assertEquals("tts-1-hd", config.model)
        assertEquals(1.5f, config.speed)
    }

    @Test
    fun openAiTtsIsSubtypeOfTtsConfig() {
        val config: TtsConfig = TtsConfig.OpenAiTts(apiKey = "key")
        assertIs<TtsConfig.OpenAiTts>(config)
    }

    // --- ElevenLabs ---

    @Test
    fun elevenLabsDefaultModelId() {
        val config = TtsConfig.ElevenLabs(apiKey = "key", voiceId = "voice-1")
        assertEquals("eleven_multilingual_v2", config.modelId)
    }

    @Test
    fun elevenLabsDefaultStability() {
        val config = TtsConfig.ElevenLabs(apiKey = "key", voiceId = "voice-1")
        assertEquals(0.5f, config.stability)
    }

    @Test
    fun elevenLabsDefaultSimilarityBoost() {
        val config = TtsConfig.ElevenLabs(apiKey = "key", voiceId = "voice-1")
        assertEquals(0.75f, config.similarityBoost)
    }

    @Test
    fun elevenLabsDefaultBaseUrl() {
        val config = TtsConfig.ElevenLabs(apiKey = "key", voiceId = "voice-1")
        assertEquals("https://api.elevenlabs.io/v1", config.baseUrl)
    }

    @Test
    fun elevenLabsCustomBaseUrl() {
        val config = TtsConfig.ElevenLabs(
            apiKey = "key",
            baseUrl = "https://proxy.example.com/v1",
            voiceId = "voice-1",
        )
        assertEquals("https://proxy.example.com/v1", config.baseUrl)
    }

    @Test
    fun elevenLabsApiKeyAndVoiceIdAreStored() {
        val config = TtsConfig.ElevenLabs(apiKey = "el-key", voiceId = "voice-abc")
        assertEquals("el-key", config.apiKey)
        assertEquals("voice-abc", config.voiceId)
    }

    @Test
    fun elevenLabsIsSubtypeOfTtsConfig() {
        val config: TtsConfig = TtsConfig.ElevenLabs(apiKey = "key", voiceId = "v")
        assertIs<TtsConfig.ElevenLabs>(config)
    }

    // --- Voicevox ---

    @Test
    fun voicevoxDefaultHost() {
        val config = TtsConfig.Voicevox()
        assertEquals("localhost", config.host)
    }

    @Test
    fun voicevoxDefaultPort() {
        val config = TtsConfig.Voicevox()
        assertEquals(50021, config.port)
    }

    @Test
    fun voicevoxDefaultSpeakerId() {
        val config = TtsConfig.Voicevox()
        assertEquals(0, config.speakerId)
    }

    @Test
    fun voicevoxCustomValuesAreStored() {
        val config = TtsConfig.Voicevox(host = "192.168.1.10", port = 50022, speakerId = 3)
        assertEquals("192.168.1.10", config.host)
        assertEquals(50022, config.port)
        assertEquals(3, config.speakerId)
    }

    @Test
    fun voicevoxIsSubtypeOfTtsConfig() {
        val config: TtsConfig = TtsConfig.Voicevox()
        assertIs<TtsConfig.Voicevox>(config)
    }

    // --- When exhaustive ---

    @Test
    fun whenExpressionCoversAllSubtypes() {
        val configs: List<TtsConfig> = listOf(
            TtsConfig.OpenAiTts(apiKey = "k"),
            TtsConfig.ElevenLabs(apiKey = "k", voiceId = "v"),
            TtsConfig.Voicevox(),
        )
        val names = configs.map { config ->
            when (config) {
                is TtsConfig.OpenAiTts -> "openai"
                is TtsConfig.ElevenLabs -> "elevenlabs"
                is TtsConfig.Voicevox -> "voicevox"
            }
        }
        assertEquals(listOf("openai", "elevenlabs", "voicevox"), names)
    }
}
