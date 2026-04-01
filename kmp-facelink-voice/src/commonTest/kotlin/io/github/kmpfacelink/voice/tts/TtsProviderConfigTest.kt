package io.github.kmpfacelink.voice.tts

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotEquals

class TtsProviderConfigTest {

    // --- OpenAiTts copy & equality ---

    @Test
    fun openAiTtsCopyChangesVoice() {
        val original = TtsConfig.OpenAiTts(apiKey = "key", voice = "alloy")
        val copied = original.copy(voice = "nova")
        assertEquals("nova", copied.voice)
        assertEquals("key", copied.apiKey)
    }

    @Test
    fun openAiTtsCopyChangesModel() {
        val original = TtsConfig.OpenAiTts(apiKey = "key")
        val copied = original.copy(model = "tts-1-hd")
        assertEquals("tts-1-hd", copied.model)
    }

    @Test
    fun openAiTtsCopyChangesSpeed() {
        val original = TtsConfig.OpenAiTts(apiKey = "key")
        val copied = original.copy(speed = 2.0f)
        assertEquals(2.0f, copied.speed)
    }

    @Test
    fun openAiTtsEqualityForSameValues() {
        val a = TtsConfig.OpenAiTts(apiKey = "key", voice = "alloy", model = "tts-1", speed = 1.0f)
        val b = TtsConfig.OpenAiTts(apiKey = "key", voice = "alloy", model = "tts-1", speed = 1.0f)
        assertEquals(a, b)
        assertEquals(a.hashCode(), b.hashCode())
    }

    @Test
    fun openAiTtsInequalityForDifferentKeys() {
        val a = TtsConfig.OpenAiTts(apiKey = "key-1")
        val b = TtsConfig.OpenAiTts(apiKey = "key-2")
        assertNotEquals(a, b)
    }

    @Test
    fun openAiTtsInequalityForDifferentVoices() {
        val a = TtsConfig.OpenAiTts(apiKey = "key", voice = "alloy")
        val b = TtsConfig.OpenAiTts(apiKey = "key", voice = "shimmer")
        assertNotEquals(a, b)
    }

    @Test
    fun openAiTtsMinSpeed() {
        val config = TtsConfig.OpenAiTts(apiKey = "key", speed = 0.25f)
        assertEquals(0.25f, config.speed)
    }

    @Test
    fun openAiTtsMaxSpeed() {
        val config = TtsConfig.OpenAiTts(apiKey = "key", speed = 4.0f)
        assertEquals(4.0f, config.speed)
    }

    @Test
    fun openAiTtsEmptyApiKeyIsAccepted() {
        val config = TtsConfig.OpenAiTts(apiKey = "")
        assertEquals("", config.apiKey)
    }

    @Test
    fun openAiTtsCustomBaseUrlPreservesOtherDefaults() {
        val config = TtsConfig.OpenAiTts(apiKey = "key", baseUrl = "https://custom.api.com")
        assertEquals("https://custom.api.com", config.baseUrl)
        assertEquals("alloy", config.voice)
        assertEquals("tts-1", config.model)
        assertEquals(1.0f, config.speed)
    }

    // --- ElevenLabs copy & equality ---

    @Test
    fun elevenLabsCopyChangesVoiceId() {
        val original = TtsConfig.ElevenLabs(apiKey = "key", voiceId = "voice-1")
        val copied = original.copy(voiceId = "voice-2")
        assertEquals("voice-2", copied.voiceId)
        assertEquals("key", copied.apiKey)
    }

    @Test
    fun elevenLabsCopyChangesStability() {
        val original = TtsConfig.ElevenLabs(apiKey = "key", voiceId = "v")
        val copied = original.copy(stability = 0.8f)
        assertEquals(0.8f, copied.stability)
    }

    @Test
    fun elevenLabsCopyChangesSimilarityBoost() {
        val original = TtsConfig.ElevenLabs(apiKey = "key", voiceId = "v")
        val copied = original.copy(similarityBoost = 0.9f)
        assertEquals(0.9f, copied.similarityBoost)
    }

    @Test
    fun elevenLabsEqualityForSameValues() {
        val a = TtsConfig.ElevenLabs(apiKey = "key", voiceId = "v", stability = 0.5f)
        val b = TtsConfig.ElevenLabs(apiKey = "key", voiceId = "v", stability = 0.5f)
        assertEquals(a, b)
        assertEquals(a.hashCode(), b.hashCode())
    }

    @Test
    fun elevenLabsInequalityForDifferentVoiceIds() {
        val a = TtsConfig.ElevenLabs(apiKey = "key", voiceId = "voice-1")
        val b = TtsConfig.ElevenLabs(apiKey = "key", voiceId = "voice-2")
        assertNotEquals(a, b)
    }

    @Test
    fun elevenLabsMinStability() {
        val config = TtsConfig.ElevenLabs(apiKey = "key", voiceId = "v", stability = 0.0f)
        assertEquals(0.0f, config.stability)
    }

    @Test
    fun elevenLabsMaxStability() {
        val config = TtsConfig.ElevenLabs(apiKey = "key", voiceId = "v", stability = 1.0f)
        assertEquals(1.0f, config.stability)
    }

    @Test
    fun elevenLabsMinSimilarityBoost() {
        val config = TtsConfig.ElevenLabs(apiKey = "key", voiceId = "v", similarityBoost = 0.0f)
        assertEquals(0.0f, config.similarityBoost)
    }

    @Test
    fun elevenLabsMaxSimilarityBoost() {
        val config = TtsConfig.ElevenLabs(apiKey = "key", voiceId = "v", similarityBoost = 1.0f)
        assertEquals(1.0f, config.similarityBoost)
    }

    @Test
    fun elevenLabsCustomBaseUrlPreservesOtherDefaults() {
        val config = TtsConfig.ElevenLabs(
            apiKey = "key",
            baseUrl = "https://proxy.local",
            voiceId = "v",
        )
        assertEquals("https://proxy.local", config.baseUrl)
        assertEquals("eleven_multilingual_v2", config.modelId)
        assertEquals(0.5f, config.stability)
        assertEquals(0.75f, config.similarityBoost)
    }

    @Test
    fun elevenLabsEmptyApiKeyIsAccepted() {
        val config = TtsConfig.ElevenLabs(apiKey = "", voiceId = "v")
        assertEquals("", config.apiKey)
    }

    @Test
    fun elevenLabsCopyChangesModelId() {
        val original = TtsConfig.ElevenLabs(apiKey = "key", voiceId = "v")
        val copied = original.copy(modelId = "eleven_turbo_v2_5")
        assertEquals("eleven_turbo_v2_5", copied.modelId)
    }

    // --- Voicevox copy & equality ---

    @Test
    fun voicevoxCopyChangesHost() {
        val original = TtsConfig.Voicevox()
        val copied = original.copy(host = "10.0.0.1")
        assertEquals("10.0.0.1", copied.host)
        assertEquals(50021, copied.port)
    }

    @Test
    fun voicevoxCopyChangesPort() {
        val original = TtsConfig.Voicevox()
        val copied = original.copy(port = 8080)
        assertEquals(8080, copied.port)
    }

    @Test
    fun voicevoxCopyChangesSpeakerId() {
        val original = TtsConfig.Voicevox()
        val copied = original.copy(speakerId = 10)
        assertEquals(10, copied.speakerId)
    }

    @Test
    fun voicevoxEqualityForSameValues() {
        val a = TtsConfig.Voicevox(host = "localhost", port = 50021, speakerId = 0)
        val b = TtsConfig.Voicevox(host = "localhost", port = 50021, speakerId = 0)
        assertEquals(a, b)
        assertEquals(a.hashCode(), b.hashCode())
    }

    @Test
    fun voicevoxInequalityForDifferentHosts() {
        val a = TtsConfig.Voicevox(host = "localhost")
        val b = TtsConfig.Voicevox(host = "192.168.1.1")
        assertNotEquals(a, b)
    }

    @Test
    fun voicevoxInequalityForDifferentPorts() {
        val a = TtsConfig.Voicevox(port = 50021)
        val b = TtsConfig.Voicevox(port = 50022)
        assertNotEquals(a, b)
    }

    @Test
    fun voicevoxLargeSpeakerId() {
        val config = TtsConfig.Voicevox(speakerId = 999)
        assertEquals(999, config.speakerId)
    }

    @Test
    fun voicevoxEmptyHostIsAccepted() {
        val config = TtsConfig.Voicevox(host = "")
        assertEquals("", config.host)
    }

    // --- Cross-type inequality ---

    @Test
    fun differentTtsConfigTypesAreNotEqual() {
        val openai: TtsConfig = TtsConfig.OpenAiTts(apiKey = "key")
        val elevenlabs: TtsConfig = TtsConfig.ElevenLabs(apiKey = "key", voiceId = "v")
        val voicevox: TtsConfig = TtsConfig.Voicevox()
        assertNotEquals(openai, elevenlabs)
        assertNotEquals(openai, voicevox)
        assertNotEquals(elevenlabs, voicevox)
    }

    // --- Sealed class exhaustiveness ---

    @Test
    fun allConfigTypesCanBeMatchedExhaustively() {
        val configs: List<TtsConfig> = listOf(
            TtsConfig.OpenAiTts(apiKey = "k"),
            TtsConfig.ElevenLabs(apiKey = "k", voiceId = "v"),
            TtsConfig.Voicevox(),
        )
        val types = configs.map { config ->
            when (config) {
                is TtsConfig.OpenAiTts -> "openai"
                is TtsConfig.ElevenLabs -> "elevenlabs"
                is TtsConfig.Voicevox -> "voicevox"
            }
        }
        assertEquals(3, types.size)
        assertEquals(listOf("openai", "elevenlabs", "voicevox"), types)
    }

    @Test
    fun configsAreInstancesOfSealedParent() {
        assertIs<TtsConfig>(TtsConfig.OpenAiTts(apiKey = "k"))
        assertIs<TtsConfig>(TtsConfig.ElevenLabs(apiKey = "k", voiceId = "v"))
        assertIs<TtsConfig>(TtsConfig.Voicevox())
    }
}
