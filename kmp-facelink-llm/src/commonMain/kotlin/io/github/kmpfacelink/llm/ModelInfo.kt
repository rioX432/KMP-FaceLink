package io.github.kmpfacelink.llm

/**
 * Metadata for a downloadable on-device LLM model.
 *
 * @property id Unique identifier used to reference this model (e.g. "gemma-3n-e2b-int4")
 * @property displayName Human-readable model name for UI display
 * @property sizeBytes Approximate file size in bytes
 * @property quantization Quantization level of this model variant
 * @property downloadUrl URL to download the model weights
 * @property sha256Checksum SHA-256 hex digest for integrity verification
 * @property ramRequirementBytes Minimum device RAM in bytes needed to run this model
 */
public data class ModelInfo(
    val id: String,
    val displayName: String,
    val sizeBytes: Long,
    val quantization: Quantization,
    val downloadUrl: String,
    val sha256Checksum: String,
    val ramRequirementBytes: Long = 0L,
)
