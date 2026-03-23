package io.github.kmpfacelink.llm

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class OnDeviceConfigTest {

    @Test
    fun onDeviceDefaultMaxTokens() {
        val config = LlmConfig.OnDevice(modelId = "gemma-3-1b-int4")
        assertEquals(1024, config.maxTokens)
    }

    @Test
    fun onDeviceDefaultTemperature() {
        val config = LlmConfig.OnDevice(modelId = "gemma-3-1b-int4")
        assertEquals(0.7, config.temperature)
    }

    @Test
    fun onDeviceCustomValues() {
        val config = LlmConfig.OnDevice(
            modelId = "phi-4-mini-int4",
            maxTokens = 512,
            temperature = 0.5,
        )
        assertEquals("phi-4-mini-int4", config.modelId)
        assertEquals(512, config.maxTokens)
        assertEquals(0.5, config.temperature)
    }

    @Test
    fun onDeviceIsSubclassOfLlmConfig() {
        val config: LlmConfig = LlmConfig.OnDevice(modelId = "gemma-3-1b-int4")
        assertIs<LlmConfig.OnDevice>(config)
    }

    // Note: LlmClient.create(OnDevice) behavior is platform-specific.
    // Android creates a real OnDeviceLlmClient (requires native lib).
    // iOS throws UnsupportedOperationException (MLX not yet implemented).
    // Platform-specific tests cover this in their respective source sets.
}
