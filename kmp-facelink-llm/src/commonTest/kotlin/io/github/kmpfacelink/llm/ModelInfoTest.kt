package io.github.kmpfacelink.llm

import kotlin.test.Test
import kotlin.test.assertEquals

class ModelInfoTest {

    @Test
    fun modelInfoProperties() {
        val info = ModelInfo(
            id = "test-model",
            displayName = "Test Model",
            sizeBytes = 1_000_000L,
            quantization = Quantization.INT4,
            downloadUrl = "https://example.com/model.gguf",
            sha256Checksum = "abc123",
        )
        assertEquals("test-model", info.id)
        assertEquals("Test Model", info.displayName)
        assertEquals(1_000_000L, info.sizeBytes)
        assertEquals(Quantization.INT4, info.quantization)
        assertEquals("https://example.com/model.gguf", info.downloadUrl)
        assertEquals("abc123", info.sha256Checksum)
    }

    @Test
    fun modelInfoEquality() {
        val a = ModelInfo(
            id = "m1",
            displayName = "M1",
            sizeBytes = 100L,
            quantization = Quantization.FP16,
            downloadUrl = "https://example.com/m1",
            sha256Checksum = "hash1",
        )
        val b = a.copy()
        assertEquals(a, b)
    }

    @Test
    fun quantizationValues() {
        val values = Quantization.entries
        assertEquals(3, values.size)
        assertEquals(Quantization.INT4, values[0])
        assertEquals(Quantization.INT8, values[1])
        assertEquals(Quantization.FP16, values[2])
    }
}
