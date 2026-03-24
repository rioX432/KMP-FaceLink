package io.github.kmpfacelink.llm

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class LlmConfigTest {

    // OpenAi defaults
    @Test
    fun openAiDefaultModel() {
        val config = LlmConfig.OpenAi(apiKey = "k")
        assertEquals("gpt-4o-mini", config.model)
    }

    @Test
    fun openAiDefaultBaseUrl() {
        val config = LlmConfig.OpenAi(apiKey = "k")
        assertEquals("https://api.openai.com/v1", config.baseUrl)
    }

    @Test
    fun openAiDefaultMaxTokens() {
        val config = LlmConfig.OpenAi(apiKey = "k")
        assertEquals(1024, config.maxTokens)
    }

    @Test
    fun openAiDefaultTemperature() {
        val config = LlmConfig.OpenAi(apiKey = "k")
        assertEquals(0.7, config.temperature)
    }

    @Test
    fun openAiCustomValues() {
        val config = LlmConfig.OpenAi(
            apiKey = "my-key",
            model = "gpt-4o",
            baseUrl = "https://proxy.example.com/v1",
            maxTokens = 512,
            temperature = 1.5,
        )
        assertEquals("my-key", config.apiKey)
        assertEquals("gpt-4o", config.model)
        assertEquals("https://proxy.example.com/v1", config.baseUrl)
        assertEquals(512, config.maxTokens)
        assertEquals(1.5, config.temperature)
    }

    // Anthropic defaults
    @Test
    fun anthropicDefaultModel() {
        val config = LlmConfig.Anthropic(apiKey = "k")
        assertEquals("claude-haiku-4-5-20251001", config.model)
    }

    @Test
    fun anthropicDefaultMaxTokens() {
        val config = LlmConfig.Anthropic(apiKey = "k")
        assertEquals(1024, config.maxTokens)
    }

    @Test
    fun anthropicDefaultTemperature() {
        val config = LlmConfig.Anthropic(apiKey = "k")
        assertEquals(0.7, config.temperature)
    }

    @Test
    fun anthropicCustomValues() {
        val config = LlmConfig.Anthropic(
            apiKey = "ant-key",
            model = "claude-opus-4-6",
            maxTokens = 2000,
            temperature = 0.0,
        )
        assertEquals("ant-key", config.apiKey)
        assertEquals("claude-opus-4-6", config.model)
        assertEquals(2000, config.maxTokens)
        assertEquals(0.0, config.temperature)
    }

    // Gemini defaults
    @Test
    fun geminiDefaultModel() {
        val config = LlmConfig.Gemini(apiKey = "k")
        assertEquals("gemini-2.0-flash", config.model)
    }

    @Test
    fun geminiDefaultMaxTokens() {
        val config = LlmConfig.Gemini(apiKey = "k")
        assertEquals(1024, config.maxTokens)
    }

    @Test
    fun geminiDefaultTemperature() {
        val config = LlmConfig.Gemini(apiKey = "k")
        assertEquals(0.7, config.temperature)
    }

    @Test
    fun geminiCustomValues() {
        val config = LlmConfig.Gemini(
            apiKey = "gem-key",
            model = "gemini-2.0-pro",
            maxTokens = 4000,
            temperature = 0.9,
        )
        assertEquals("gem-key", config.apiKey)
        assertEquals("gemini-2.0-pro", config.model)
        assertEquals(4000, config.maxTokens)
        assertEquals(0.9, config.temperature)
    }

    // Sealed class structure
    @Test
    fun openAiIsSubclassOfLlmConfig() {
        val config: LlmConfig = LlmConfig.OpenAi(apiKey = "k")
        assertIs<LlmConfig.OpenAi>(config)
    }

    @Test
    fun anthropicIsSubclassOfLlmConfig() {
        val config: LlmConfig = LlmConfig.Anthropic(apiKey = "k")
        assertIs<LlmConfig.Anthropic>(config)
    }

    @Test
    fun geminiIsSubclassOfLlmConfig() {
        val config: LlmConfig = LlmConfig.Gemini(apiKey = "k")
        assertIs<LlmConfig.Gemini>(config)
    }

    // FoundationModels defaults
    @Test
    fun foundationModelsDefaultInstructions() {
        val config = LlmConfig.FoundationModels()
        assertEquals("", config.instructions)
    }

    @Test
    fun foundationModelsDefaultMaxTokens() {
        val config = LlmConfig.FoundationModels()
        assertEquals(1024, config.maxTokens)
    }

    @Test
    fun foundationModelsDefaultTemperature() {
        val config = LlmConfig.FoundationModels()
        assertEquals(0.7, config.temperature)
    }

    @Test
    fun foundationModelsCustomValues() {
        val config = LlmConfig.FoundationModels(
            instructions = "Be helpful",
            maxTokens = 2048,
            temperature = 0.5,
        )
        assertEquals("Be helpful", config.instructions)
        assertEquals(2048, config.maxTokens)
        assertEquals(0.5, config.temperature)
    }

    @Test
    fun foundationModelsIsSubclassOfLlmConfig() {
        val config: LlmConfig = LlmConfig.FoundationModels()
        assertIs<LlmConfig.FoundationModels>(config)
    }
}
