package io.github.kmpfacelink.llm

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ModelRegistryTest {

    @Test
    fun registryContainsNineModels() {
        assertEquals(9, ModelRegistry.models.size)
    }

    @Test
    fun registryContainsGemma4E2b() {
        val model = ModelRegistry.findById("gemma-4-e2b-int4")
        assertNotNull(model)
        assertEquals("Gemma 4 E2B (INT4)", model.displayName)
        assertEquals(Quantization.INT4, model.quantization)
        assertTrue(model.ramRequirementBytes > 0)
        assertFalse(model.supportsNativeAudio)
    }

    @Test
    fun registryContainsGemma4E4b() {
        val model = ModelRegistry.findById("gemma-4-e4b-int4")
        assertNotNull(model)
        assertEquals("Gemma 4 E4B (INT4)", model.displayName)
        assertEquals(Quantization.INT4, model.quantization)
        assertTrue(model.ramRequirementBytes > 0)
        assertTrue(model.supportsNativeAudio)
    }

    @Test
    fun registryContainsGemma3nE2b() {
        val model = ModelRegistry.findById("gemma-3n-e2b-int4")
        assertNotNull(model)
        assertEquals("Gemma 3n E2B (INT4)", model.displayName)
        assertEquals(Quantization.INT4, model.quantization)
        assertTrue(model.ramRequirementBytes > 0)
    }

    @Test
    fun registryContainsGemma3nE4b() {
        val model = ModelRegistry.findById("gemma-3n-e4b-int4")
        assertNotNull(model)
        assertEquals("Gemma 3n E4B (INT4)", model.displayName)
        assertEquals(Quantization.INT4, model.quantization)
    }

    @Test
    fun registryContainsQwen3() {
        val model = ModelRegistry.findById("qwen3-0.6b-int4")
        assertNotNull(model)
        assertEquals("Qwen3 0.6B (INT4)", model.displayName)
        assertEquals(Quantization.INT4, model.quantization)
    }

    @Test
    fun registryContainsGemma3_1b() {
        val model = ModelRegistry.findById("gemma-3-1b-int4")
        assertNotNull(model)
        assertEquals("Gemma 3 1B (INT4)", model.displayName)
    }

    @Test
    fun registryContainsGemma3_4b() {
        val model = ModelRegistry.findById("gemma-3-4b-int4")
        assertNotNull(model)
        assertEquals("Gemma 3 4B (INT4)", model.displayName)
    }

    @Test
    fun registryContainsPhi3Mini() {
        val model = ModelRegistry.findById("phi-3-mini-int4")
        assertNotNull(model)
        assertEquals("Phi-3 Mini (INT4)", model.displayName)
    }

    @Test
    fun registryContainsPhi4Mini() {
        val model = ModelRegistry.findById("phi-4-mini-int4")
        assertNotNull(model)
        assertEquals("Phi-4 Mini (INT4)", model.displayName)
    }

    @Test
    fun findByIdReturnsNullForUnknown() {
        assertNull(ModelRegistry.findById("nonexistent-model"))
    }

    @Test
    fun allModelsHaveUniqueIds() {
        val ids = ModelRegistry.models.map { it.id }
        assertEquals(ids.size, ids.distinct().size)
    }

    @Test
    fun allModelsHaveNonEmptyDownloadUrl() {
        ModelRegistry.models.forEach { model ->
            assertTrue(model.downloadUrl.isNotBlank(), "Model ${model.id} has empty downloadUrl")
        }
    }

    @Test
    fun allModelsHavePositiveSize() {
        ModelRegistry.models.forEach { model ->
            assertTrue(model.sizeBytes > 0, "Model ${model.id} has non-positive sizeBytes")
        }
    }

    @Test
    fun allModelsHavePositiveRamRequirement() {
        ModelRegistry.models.forEach { model ->
            assertTrue(
                model.ramRequirementBytes > 0,
                "Model ${model.id} has non-positive ramRequirementBytes",
            )
        }
    }

    @Test
    fun defaultModelIsGemma4E2b() {
        val default = ModelRegistry.defaultModel()
        assertEquals("gemma-4-e2b-int4", default.id)
    }

    @Test
    fun recommendForRamBelow1GbReturnsQwen3() {
        val model = ModelRegistry.recommendForRam(512_000_000L)
        assertEquals("qwen3-0.6b-int4", model.id)
    }

    @Test
    fun recommendForRamAt1GbReturnsGemma4E2b() {
        val model = ModelRegistry.recommendForRam(1_000_000_000L)
        assertEquals("gemma-4-e2b-int4", model.id)
    }

    @Test
    fun recommendForRamAt4GbReturnsGemma4E2b() {
        val model = ModelRegistry.recommendForRam(4_000_000_000L)
        assertEquals("gemma-4-e2b-int4", model.id)
    }

    @Test
    fun recommendForRamAbove4GbReturnsGemma4E4b() {
        val model = ModelRegistry.recommendForRam(6_000_000_000L)
        assertEquals("gemma-4-e4b-int4", model.id)
    }

    @Test
    fun recommendForRamAt8GbReturnsGemma4E4b() {
        val model = ModelRegistry.recommendForRam(8_000_000_000L)
        assertEquals("gemma-4-e4b-int4", model.id)
    }

    @Test
    fun audioCapableModelsContainsOnlyGemma4E4b() {
        val audioModels = ModelRegistry.audioCapableModels
        assertEquals(1, audioModels.size)
        assertEquals("gemma-4-e4b-int4", audioModels.first().id)
    }
}
