package io.github.kmpfacelink.llm

private const val GEMMA_4_E2B_SIZE = 1_500_000_000L
private const val GEMMA_4_E4B_SIZE = 2_800_000_000L
private const val GEMMA_3N_E2B_SIZE = 1_800_000_000L
private const val GEMMA_3N_E4B_SIZE = 3_200_000_000L
private const val QWEN3_06B_SIZE = 500_000_000L
private const val GEMMA_3_1B_SIZE = 800_000_000L
private const val GEMMA_3_4B_SIZE = 2_500_000_000L
private const val PHI_3_MINI_SIZE = 2_300_000_000L
private const val PHI_4_MINI_SIZE = 2_400_000_000L

private const val GEMMA_4_E2B_RAM = 2_000_000_000L
private const val GEMMA_4_E4B_RAM = 4_000_000_000L
private const val GEMMA_3N_E2B_RAM = 2_000_000_000L
private const val GEMMA_3N_E4B_RAM = 4_500_000_000L
private const val QWEN3_06B_RAM = 800_000_000L
private const val GEMMA_3_1B_RAM = 1_500_000_000L
private const val GEMMA_3_4B_RAM = 4_000_000_000L
private const val PHI_3_MINI_RAM = 4_000_000_000L
private const val PHI_4_MINI_RAM = 4_000_000_000L

/** RAM threshold: devices with this amount or less use E2B, above use E4B. */
private const val RAM_THRESHOLD_4GB = 4_000_000_000L

/** RAM threshold: devices below this use the ultra-lightweight fallback. */
private const val RAM_THRESHOLD_1GB = 1_000_000_000L

private const val HF_BASE = "https://huggingface.co"

private const val GEMMA_4_E2B_URL =
    "$HF_BASE/google/gemma-4-E2B-it-qat-q4_0-gguf/resolve/main/gemma-4-E2B-it-q4_0.gguf"
private const val GEMMA_4_E4B_URL =
    "$HF_BASE/google/gemma-4-E4B-it-qat-q4_0-gguf/resolve/main/gemma-4-E4B-it-q4_0.gguf"
private const val GEMMA_3N_E2B_URL =
    "$HF_BASE/google/gemma-3n-E2B-it-qat-q4_0-gguf/resolve/main/gemma-3n-E2B-it-q4_0.gguf"
private const val GEMMA_3N_E4B_URL =
    "$HF_BASE/google/gemma-3n-E4B-it-qat-q4_0-gguf/resolve/main/gemma-3n-E4B-it-q4_0.gguf"
private const val QWEN3_06B_URL =
    "$HF_BASE/Qwen/Qwen3-0.6B-GGUF/resolve/main/qwen3-0.6b-q4_0.gguf"
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
 * The default model is Gemma 4 E2B, which offers improved quality over Gemma 3n
 * with a smaller download size (~1.5 GB) and native audio input support on E4B.
 */
public object ModelRegistry {

    /** All available models in the registry, ordered by recommendation priority. */
    public val models: List<ModelInfo> = listOf(
        ModelInfo(
            id = "gemma-4-e2b-int4",
            displayName = "Gemma 4 E2B (INT4)",
            sizeBytes = GEMMA_4_E2B_SIZE,
            quantization = Quantization.INT4,
            downloadUrl = GEMMA_4_E2B_URL,
            sha256Checksum = null,
            ramRequirementBytes = GEMMA_4_E2B_RAM,
        ),
        ModelInfo(
            id = "gemma-4-e4b-int4",
            displayName = "Gemma 4 E4B (INT4)",
            sizeBytes = GEMMA_4_E4B_SIZE,
            quantization = Quantization.INT4,
            downloadUrl = GEMMA_4_E4B_URL,
            sha256Checksum = null,
            ramRequirementBytes = GEMMA_4_E4B_RAM,
            supportsNativeAudio = true,
        ),
        ModelInfo(
            id = "gemma-3n-e2b-int4",
            displayName = "Gemma 3n E2B (INT4)",
            sizeBytes = GEMMA_3N_E2B_SIZE,
            quantization = Quantization.INT4,
            downloadUrl = GEMMA_3N_E2B_URL,
            sha256Checksum = null,
            ramRequirementBytes = GEMMA_3N_E2B_RAM,
        ),
        ModelInfo(
            id = "gemma-3n-e4b-int4",
            displayName = "Gemma 3n E4B (INT4)",
            sizeBytes = GEMMA_3N_E4B_SIZE,
            quantization = Quantization.INT4,
            downloadUrl = GEMMA_3N_E4B_URL,
            sha256Checksum = null,
            ramRequirementBytes = GEMMA_3N_E4B_RAM,
        ),
        ModelInfo(
            id = "qwen3-0.6b-int4",
            displayName = "Qwen3 0.6B (INT4)",
            sizeBytes = QWEN3_06B_SIZE,
            quantization = Quantization.INT4,
            downloadUrl = QWEN3_06B_URL,
            sha256Checksum = null,
            ramRequirementBytes = QWEN3_06B_RAM,
        ),
        ModelInfo(
            id = "gemma-3-1b-int4",
            displayName = "Gemma 3 1B (INT4)",
            sizeBytes = GEMMA_3_1B_SIZE,
            quantization = Quantization.INT4,
            downloadUrl = GEMMA_3_1B_URL,
            sha256Checksum = null,
            ramRequirementBytes = GEMMA_3_1B_RAM,
        ),
        ModelInfo(
            id = "gemma-3-4b-int4",
            displayName = "Gemma 3 4B (INT4)",
            sizeBytes = GEMMA_3_4B_SIZE,
            quantization = Quantization.INT4,
            downloadUrl = GEMMA_3_4B_URL,
            sha256Checksum = null,
            ramRequirementBytes = GEMMA_3_4B_RAM,
        ),
        ModelInfo(
            id = "phi-3-mini-int4",
            displayName = "Phi-3 Mini (INT4)",
            sizeBytes = PHI_3_MINI_SIZE,
            quantization = Quantization.INT4,
            downloadUrl = PHI_3_MINI_URL,
            sha256Checksum = null,
            ramRequirementBytes = PHI_3_MINI_RAM,
        ),
        ModelInfo(
            id = "phi-4-mini-int4",
            displayName = "Phi-4 Mini (INT4)",
            sizeBytes = PHI_4_MINI_SIZE,
            quantization = Quantization.INT4,
            downloadUrl = PHI_4_MINI_URL,
            sha256Checksum = null,
            ramRequirementBytes = PHI_4_MINI_RAM,
        ),
    )

    /** All models that support native audio input (single-stage audio-to-text). */
    public val audioCapableModels: List<ModelInfo> = models.filter { it.supportsNativeAudio }

    /**
     * Finds a model by its unique identifier.
     *
     * @param id Model identifier (e.g. "gemma-4-e2b-int4")
     * @return The matching [ModelInfo], or null if not found
     */
    public fun findById(id: String): ModelInfo? = models.find { it.id == id }

    /**
     * Recommends the best model for the given device RAM.
     *
     * Selection logic (Gemma 4 preferred over 3n for improved quality):
     * - RAM < 1 GB → Qwen3 0.6B (ultra-lightweight fallback)
     * - RAM ≤ 4 GB → Gemma 4 E2B (default, 2.3B params, ~1.5 GB)
     * - RAM > 4 GB → Gemma 4 E4B (high-quality, 4B params, native audio)
     *
     * @param deviceRamBytes Total device RAM in bytes
     * @return The recommended [ModelInfo] for the device
     */
    public fun recommendForRam(deviceRamBytes: Long): ModelInfo {
        return when {
            deviceRamBytes < RAM_THRESHOLD_1GB -> requireNotNull(findById("qwen3-0.6b-int4")) {
                "Built-in model 'qwen3-0.6b-int4' not found in registry; registry may be corrupted"
            }
            deviceRamBytes <= RAM_THRESHOLD_4GB -> requireNotNull(findById("gemma-4-e2b-int4")) {
                "Built-in model 'gemma-4-e2b-int4' not found in registry; registry may be corrupted"
            }
            else -> requireNotNull(findById("gemma-4-e4b-int4")) {
                "Built-in model 'gemma-4-e4b-int4' not found in registry; registry may be corrupted"
            }
        }
    }

    /**
     * Returns the default model (Gemma 4 E2B).
     *
     * Use [recommendForRam] for device-adaptive selection instead.
     */
    public fun defaultModel(): ModelInfo = requireNotNull(findById("gemma-4-e2b-int4")) {
        "Built-in model 'gemma-4-e2b-int4' not found in registry; registry may be corrupted"
    }
}
