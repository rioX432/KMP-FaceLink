package io.github.kmpfacelink.llm

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ModelRegistryTest {

    @Test
    fun registryContainsFourModels() {
        assertEquals(4, ModelRegistry.models.size)
    }

    @Test
    fun registryContainsGemma3_1b() {
        val model = ModelRegistry.findById("gemma-3-1b-int4")
        assertNotNull(model)
        assertEquals("Gemma 3 1B (INT4)", model.displayName)
        assertEquals(Quantization.INT4, model.quantization)
    }

    @Test
    fun registryContainsGemma3_4b() {
        val model = ModelRegistry.findById("gemma-3-4b-int4")
        assertNotNull(model)
        assertEquals("Gemma 3 4B (INT4)", model.displayName)
        assertEquals(Quantization.INT4, model.quantization)
    }

    @Test
    fun registryContainsPhi3Mini() {
        val model = ModelRegistry.findById("phi-3-mini-int4")
        assertNotNull(model)
        assertEquals("Phi-3 Mini (INT4)", model.displayName)
        assertEquals(Quantization.INT4, model.quantization)
    }

    @Test
    fun registryContainsPhi4Mini() {
        val model = ModelRegistry.findById("phi-4-mini-int4")
        assertNotNull(model)
        assertEquals("Phi-4 Mini (INT4)", model.displayName)
        assertEquals(Quantization.INT4, model.quantization)
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
}
