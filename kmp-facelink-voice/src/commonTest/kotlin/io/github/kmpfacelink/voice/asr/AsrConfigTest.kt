package io.github.kmpfacelink.voice.asr

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

class AsrConfigTest {

    // --- OpenAiWhisper ---

    @Test
    fun openAiWhisperDefaultModel() {
        val config = AsrConfig.OpenAiWhisper(apiKey = "key")
        assertEquals("whisper-1", config.model)
    }

    @Test
    fun openAiWhisperDefaultLanguageIsNull() {
        val config = AsrConfig.OpenAiWhisper(apiKey = "key")
        assertNull(config.language)
    }

    @Test
    fun openAiWhisperDefaultBaseUrl() {
        val config = AsrConfig.OpenAiWhisper(apiKey = "key")
        assertEquals("https://api.openai.com/v1", config.baseUrl)
    }

    @Test
    fun openAiWhisperCustomBaseUrl() {
        val config = AsrConfig.OpenAiWhisper(
            apiKey = "key",
            baseUrl = "https://proxy.example.com/v1",
        )
        assertEquals("https://proxy.example.com/v1", config.baseUrl)
    }

    @Test
    fun openAiWhisperApiKeyIsStored() {
        val config = AsrConfig.OpenAiWhisper(apiKey = "sk-whisper-key")
        assertEquals("sk-whisper-key", config.apiKey)
    }

    @Test
    fun openAiWhisperLanguageIsStored() {
        val config = AsrConfig.OpenAiWhisper(apiKey = "key", language = "ja")
        assertEquals("ja", config.language)
    }

    @Test
    fun openAiWhisperIsSubtypeOfAsrConfig() {
        val config: AsrConfig = AsrConfig.OpenAiWhisper(apiKey = "key")
        assertIs<AsrConfig.OpenAiWhisper>(config)
    }

    @Test
    fun openAiWhisperEqualityByValue() {
        val a = AsrConfig.OpenAiWhisper(apiKey = "key", model = "whisper-1", language = "en")
        val b = AsrConfig.OpenAiWhisper(apiKey = "key", model = "whisper-1", language = "en")
        assertEquals(a, b)
    }

    // --- WhisperCpp ---

    @Test
    fun whisperCppModelPathIsStored() {
        val config = AsrConfig.WhisperCpp(modelPath = "/data/models/ggml-base.bin")
        assertEquals("/data/models/ggml-base.bin", config.modelPath)
    }

    @Test
    fun whisperCppDefaultLanguageIsNull() {
        val config = AsrConfig.WhisperCpp(modelPath = "/model.bin")
        assertNull(config.language)
    }

    @Test
    fun whisperCppDefaultThreads() {
        val config = AsrConfig.WhisperCpp(modelPath = "/model.bin")
        assertEquals(4, config.threads)
    }

    @Test
    fun whisperCppCustomValuesAreStored() {
        val config = AsrConfig.WhisperCpp(
            modelPath = "/model.bin",
            language = "en",
            threads = 8,
        )
        assertEquals("en", config.language)
        assertEquals(8, config.threads)
    }

    @Test
    fun whisperCppIsSubtypeOfAsrConfig() {
        val config: AsrConfig = AsrConfig.WhisperCpp(modelPath = "/model.bin")
        assertIs<AsrConfig.WhisperCpp>(config)
    }

    // --- When exhaustive ---

    @Test
    fun whenExpressionCoversAllSubtypes() {
        val configs: List<AsrConfig> = listOf(
            AsrConfig.OpenAiWhisper(apiKey = "k"),
            AsrConfig.WhisperCpp(modelPath = "/m"),
        )
        val names = configs.map { config ->
            when (config) {
                is AsrConfig.OpenAiWhisper -> "openai"
                is AsrConfig.WhisperCpp -> "whispercpp"
            }
        }
        assertEquals(listOf("openai", "whispercpp"), names)
    }
}
