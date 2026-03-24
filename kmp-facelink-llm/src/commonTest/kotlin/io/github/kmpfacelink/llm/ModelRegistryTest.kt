package io.github.kmpfacelink.llm

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ModelRegistryTest {

    @Test
    fun registryContainsSevenModels() {
        assertEquals(7, ModelRegistry.models.size)
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
    fun defaultModelIsGemma3nE2b() {
        val default = ModelRegistry.defaultModel()
        assertEquals("gemma-3n-e2b-int4", default.id)
    }

    @Test
    fun recommendForRamBelow1GbReturnsQwen3() {
        val model = ModelRegistry.recommendForRam(512_000_000L)
        assertEquals("qwen3-0.6b-int4", model.id)
    }

    @Test
    fun recommendForRamAt1GbReturnsGemma3nE2b() {
        val model = ModelRegistry.recommendForRam(1_000_000_000L)
        assertEquals("gemma-3n-e2b-int4", model.id)
    }

    @Test
    fun recommendForRamAt4GbReturnsGemma3nE2b() {
        val model = ModelRegistry.recommendForRam(4_000_000_000L)
        assertEquals("gemma-3n-e2b-int4", model.id)
    }

    @Test
    fun recommendForRamAbove4GbReturnsGemma3nE4b() {
        val model = ModelRegistry.recommendForRam(6_000_000_000L)
        assertEquals("gemma-3n-e4b-int4", model.id)
    }

    @Test
    fun recommendForRamAt8GbReturnsGemma3nE4b() {
        val model = ModelRegistry.recommendForRam(8_000_000_000L)
        assertEquals("gemma-3n-e4b-int4", model.id)
    }
}
