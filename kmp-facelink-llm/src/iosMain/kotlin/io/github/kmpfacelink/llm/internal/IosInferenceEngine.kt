package io.github.kmpfacelink.llm.internal

import io.github.kmpfacelink.llm.InferenceEngine
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import platform.Foundation.NSRecursiveLock

/**
 * iOS [InferenceEngine] backed by MLX via Objective-C cinterop.
 *
 * Until the MLX native library is built and linked (via `setup-mlx-ios.sh`),
 * all operations throw [IllegalStateException] with a descriptive message.
 *
 * Once MLX is configured, this engine will delegate to [MLXLlmEngine] via
 * cinterop bindings generated from `mlx_llm.def`.
 */
internal class IosInferenceEngine : InferenceEngine {

    /**
     * Guards all access to model state to prevent concurrent modification.
     * Follows the same thread-safety pattern as WhisperCppBridge.
     */
    private val lock = NSRecursiveLock()
    private var modelLoaded = false

    override suspend fun load(modelPath: String) {
        lock.lock()
        try {
            throwMlxNotConfigured()
        } finally {
            lock.unlock()
        }
    }

    override fun generate(
        prompt: String,
        maxTokens: Int,
        temperature: Double,
    ): Flow<String> = flow {
        lock.lock()
        try {
            throwMlxNotConfigured()
        } finally {
            lock.unlock()
        }
    }

    override suspend fun unload() {
        lock.lock()
        try {
            modelLoaded = false
        } finally {
            lock.unlock()
        }
    }

    override val isLoaded: Boolean
        get() {
            lock.lock()
            try {
                return modelLoaded
            } finally {
                lock.unlock()
            }
        }

    private fun throwMlxNotConfigured(): Nothing {
        error(
            "MLX library not configured. " +
                "Run setup-mlx-ios.sh to build and link the MLX framework, " +
                "then rebuild the project.",
        )
    }
}
