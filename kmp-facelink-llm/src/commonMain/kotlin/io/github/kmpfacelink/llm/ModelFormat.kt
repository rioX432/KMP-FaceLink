package io.github.kmpfacelink.llm

/**
 * Format of the on-device model weights file.
 *
 * Determines which inference backend can load the model.
 */
public enum class ModelFormat {
    /** GGUF format used by llama.cpp. Broad model ecosystem support. */
    GGUF,

    /** LiteRT-LM proprietary format. Optimized for Gemma models with GPU/NPU acceleration. */
    LITERT_LM,
}
