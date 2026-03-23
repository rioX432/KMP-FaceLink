package io.github.kmpfacelink.llm

private const val GEMMA_3_1B_SIZE = 800_000_000L
private const val GEMMA_3_4B_SIZE = 2_500_000_000L
private const val PHI_3_MINI_SIZE = 2_300_000_000L
private const val PHI_4_MINI_SIZE = 2_400_000_000L

private const val HF_BASE = "https://huggingface.co"

private const val GEMMA_3_1B_URL =
    "$HF_BASE/google/gemma-3-1b-it-qat-q4_0-gguf/resolve/main/gemma-3-1b-it-q4_0.gguf"
private const val GEMMA_3_4B_URL =
    "$HF_BASE/google/gemma-3-4b-it-qat-q4_0-gguf/resolve/main/gemma-3-4b-it-q4_0.gguf"
private const val PHI_3_MINI_URL =
    "$HF_BASE/microsoft/Phi-3-mini-4k-instruct-gguf/resolve/main/Phi-3-mini-4k-instruct-q4.gguf"
private const val PHI_4_MINI_URL =
    "$HF_BASE/microsoft/Phi-4-mini-instruct-gguf/resolve/main/Phi-4-mini-instruct-q4.gguf"

/**
 * Registry of available on-device LLM models.
 *
 * Provides a catalog of models that can be downloaded and used for local inference.
 */
public object ModelRegistry {

    /** All available models in the registry. */
    public val models: List<ModelInfo> = listOf(
        ModelInfo(
            id = "gemma-3-1b-int4",
            displayName = "Gemma 3 1B (INT4)",
            sizeBytes = GEMMA_3_1B_SIZE,
            quantization = Quantization.INT4,
            downloadUrl = GEMMA_3_1B_URL,
            sha256Checksum = "placeholder-checksum-gemma-3-1b-int4",
        ),
        ModelInfo(
            id = "gemma-3-4b-int4",
            displayName = "Gemma 3 4B (INT4)",
            sizeBytes = GEMMA_3_4B_SIZE,
            quantization = Quantization.INT4,
            downloadUrl = GEMMA_3_4B_URL,
            sha256Checksum = "placeholder-checksum-gemma-3-4b-int4",
        ),
        ModelInfo(
            id = "phi-3-mini-int4",
            displayName = "Phi-3 Mini (INT4)",
            sizeBytes = PHI_3_MINI_SIZE,
            quantization = Quantization.INT4,
            downloadUrl = PHI_3_MINI_URL,
            sha256Checksum = "placeholder-checksum-phi-3-mini-int4",
        ),
        ModelInfo(
            id = "phi-4-mini-int4",
            displayName = "Phi-4 Mini (INT4)",
            sizeBytes = PHI_4_MINI_SIZE,
            quantization = Quantization.INT4,
            downloadUrl = PHI_4_MINI_URL,
            sha256Checksum = "placeholder-checksum-phi-4-mini-int4",
        ),
    )

    /**
     * Finds a model by its unique identifier.
     *
     * @param id Model identifier (e.g. "gemma-3-1b-int4")
     * @return The matching [ModelInfo], or null if not found
     */
    public fun findById(id: String): ModelInfo? = models.find { it.id == id }
}
