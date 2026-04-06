package io.github.kmpfacelink.llm

/**
 * Preferred inference backend for on-device LLM execution.
 *
 * Used by [LlmConfig.OnDevice] to select which engine handles inference.
 */
public enum class OnDeviceBackend {
    /**
     * Automatically select the best available backend.
     *
     * Selection logic:
     * - Gemma models with `.litertlm` available -> LiteRT-LM (GPU)
     * - All other models -> llama.cpp (GGUF)
     */
    AUTO,

    /** Force llama.cpp backend (GGUF format). Universal model support. */
    LLAMA_CPP,

    /** Force LiteRT-LM backend (.litertlm format). GPU/NPU-accelerated Gemma inference. */
    LITERT_LM,
}
