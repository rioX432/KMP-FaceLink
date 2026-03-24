package io.github.kmpfacelink.llm

import kotlinx.coroutines.flow.Flow

/**
 * Engine for running on-device LLM inference with downloaded GGUF models.
 *
 * Platform implementations (e.g. llama.cpp on Android) implement this interface
 * and are injected via factory.
 *
 * Note: On iOS 26+, prefer [LlmConfig.FoundationModels] which uses Apple's
 * built-in system language model instead of downloaded models.
 */
public interface InferenceEngine {

    /**
     * Loads the model into memory, preparing it for inference.
     *
     * Must be called before [generate]. Calling on an already-loaded model is a no-op.
     *
     * @param modelPath Absolute path to the model file on disk
     */
    public suspend fun load(modelPath: String)

    /**
     * Generates text from the given prompt, streaming tokens as they are produced.
     *
     * The model must be loaded via [load] before calling this method.
     *
     * @param prompt Input prompt text
     * @param maxTokens Maximum number of tokens to generate
     * @param temperature Sampling temperature
     * @return Flow of generated text tokens
     */
    public fun generate(prompt: String, maxTokens: Int, temperature: Double): Flow<String>

    /**
     * Unloads the model from memory, releasing all resources.
     *
     * After calling this, [load] must be called again before [generate].
     */
    public suspend fun unload()

    /**
     * Whether a model is currently loaded and ready for inference.
     */
    public val isLoaded: Boolean
}
