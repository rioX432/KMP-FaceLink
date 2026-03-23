package io.github.kmpfacelink.pipeline

import io.github.kmpfacelink.llm.LlmConfig
import io.github.kmpfacelink.llm.PersonaConfig
import kotlin.test.Test
import kotlin.test.assertEquals

class AvatarPipelineConfigTest {

    @Test
    fun defaultValues() {
        val config = AvatarPipelineConfig(
            llmConfig = LlmConfig.OpenAi(apiKey = "test-key"),
        )
        assertEquals(PersonaConfig(), config.persona)
        assertEquals(0.7f, config.expressionBlendRatio)
    }

    @Test
    fun customValues() {
        val persona = PersonaConfig(
            systemPrompt = "You are a cheerful VTuber.",
            name = "Miku",
            language = "ja",
        )
        val config = AvatarPipelineConfig(
            llmConfig = LlmConfig.Anthropic(apiKey = "test-key"),
            persona = persona,
            expressionBlendRatio = 0.5f,
        )
        assertEquals(persona, config.persona)
        assertEquals(0.5f, config.expressionBlendRatio)
    }

    @Test
    fun copyPreservesValues() {
        val original = AvatarPipelineConfig(
            llmConfig = LlmConfig.OpenAi(apiKey = "key"),
            expressionBlendRatio = 0.3f,
        )
        val copied = original.copy(expressionBlendRatio = 0.9f)
        assertEquals(0.9f, copied.expressionBlendRatio)
        assertEquals(original.llmConfig, copied.llmConfig)
        assertEquals(original.persona, copied.persona)
    }
}
