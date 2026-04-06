package io.github.kmpfacelink.llm.internal

import io.github.kmpfacelink.llm.InferenceEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext

/**
 * Hardware acceleration backend for LiteRT-LM engine.
 */
internal enum class LiteRtBackend {
    /** XNNPACK CPU backend, 4-thread. Universal fallback. */
    CPU,

    /** ML Drift GPU engine (OpenCL/OpenGL). Best prefill performance on most devices. */
    GPU,

    /** Qualcomm QNN / MediaTek NeuroPilot. Best overall but limited device support. */
    NPU,
}

/**
 * Android [InferenceEngine] backed by Google's LiteRT-LM SDK.
 *
 * Provides GPU/NPU-accelerated inference for Gemma models using the `.litertlm` format.
 * This is a scaffold implementation — actual SDK integration requires adding the
 * `com.google.ai.edge.litertlm:litertlm-android` dependency (see litert-lm.md section 6.1).
 *
 * Usage:
 * ```kotlin
 * val engine = LiteRtLmEngine(backend = LiteRtBackend.GPU)
 * engine.load("/path/to/model.litertlm")
 * engine.generate("Hello", maxTokens = 256, temperature = 0.7).collect { print(it) }
 * engine.unload()
 * ```
 *
 * @param backend Hardware acceleration backend to use
 * @param cacheDir Directory for LiteRT-LM compiled shader/kernel caches
 */
@Suppress("UnusedPrivateProperty")
internal class LiteRtLmEngine(
    private val backend: LiteRtBackend = LiteRtBackend.GPU,
    private val cacheDir: String? = null,
) : InferenceEngine {

    @Volatile
    private var loaded: Boolean = false

    override suspend fun load(modelPath: String) {
        withContext(Dispatchers.IO) {
            // Scaffold: SDK integration pending litertlm-android dependency.
            // Will use EngineConfig(modelPath, backend, cacheDir) -> Engine.initialize()
            loaded = true
        }
    }

    override fun generate(
        prompt: String,
        maxTokens: Int,
        temperature: Double,
    ): Flow<String> = flow {
        check(loaded) { "LiteRT-LM engine not loaded. Call load() first." }
        // Scaffold: SDK integration pending litertlm-android dependency.
        // Will use ConversationConfig + SamplerConfig -> sendMessageAsync() Flow
    }

    override suspend fun unload() {
        withContext(Dispatchers.IO) {
            // Scaffold: SDK integration pending litertlm-android dependency.
            // Will close Engine and Conversation resources.
            loaded = false
        }
    }

    override val isLoaded: Boolean
        get() = loaded
}
