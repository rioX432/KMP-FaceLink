package io.github.kmpfacelink.llm

/**
 * Metadata for a downloadable on-device LLM model.
 *
 * @property id Unique identifier used to reference this model (e.g. "gemma-4-e2b-int4")
 * @property displayName Human-readable model name for UI display
 * @property sizeBytes Approximate file size in bytes
 * @property quantization Quantization level of this model variant
 * @property downloadUrl URL to download the model weights
 * @property sha256Checksum SHA-256 hex digest for integrity verification
 * @property ramRequirementBytes Minimum device RAM in bytes needed to run this model
 * @property supportsNativeAudio Whether the model supports native audio input,
 *   enabling single-stage audio-to-text inference (bypassing separate ASR)
 * @property format Model file format (defaults to [ModelFormat.GGUF] for backward compatibility)
 */
public data class ModelInfo(
    val id: String,
    val displayName: String,
    val sizeBytes: Long,
    val quantization: Quantization,
    val downloadUrl: String,
    val sha256Checksum: String,
    val ramRequirementBytes: Long = 0L,
    val supportsNativeAudio: Boolean = false,
    val format: ModelFormat = ModelFormat.GGUF,
)
