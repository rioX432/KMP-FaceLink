package io.github.kmpfacelink.llm

import kotlinx.coroutines.flow.Flow

/**
 * Provider interface for Apple Foundation Models framework.
 *
 * Swift callers implement this to bridge the FoundationModels framework
 * (which is Swift-only) into the KMP LLM module. The provider handles
 * session management and text generation using [LanguageModelSession].
 *
 * On iOS 26+, use the built-in [FoundationModelsProvider] implementation
 * provided by the iOS sample app or create your own.
 *
 * Usage from Swift:
 * ```swift
 * class MyFoundationModelsProvider: FoundationModelsProvider {
 *     func isAvailable() -> Bool { ... }
 *     func respond(prompt: String, instructions: String, maxTokens: Int32, temperature: Double) async throws -> String { ... }
 *     func streamResponse(prompt: String, instructions: String, maxTokens: Int32, temperature: Double) -> Flow<String> { ... }
 * }
 * ```
 */
public interface FoundationModelsProvider {

    /**
     * Whether the Foundation Models framework is available on this device.
     *
     * Returns true only on iOS 26+ devices with Apple Intelligence enabled
     * and the system language model ready.
     */
    public fun isAvailable(): Boolean

    /**
     * Generates a complete response for the given prompt.
     *
     * @param prompt User prompt text
     * @param instructions System instructions for the session
     * @param maxTokens Maximum tokens in the response
     * @param temperature Sampling temperature
     * @return Complete response text
     */
    public suspend fun respond(
        prompt: String,
        instructions: String,
        maxTokens: Int,
        temperature: Double,
    ): String

    /**
     * Generates a streaming response for the given prompt.
     *
     * @param prompt User prompt text
     * @param instructions System instructions for the session
     * @param maxTokens Maximum tokens in the response
     * @param temperature Sampling temperature
     * @return Flow of response text chunks
     */
    public fun streamResponse(
        prompt: String,
        instructions: String,
        maxTokens: Int,
        temperature: Double,
    ): Flow<String>
}
