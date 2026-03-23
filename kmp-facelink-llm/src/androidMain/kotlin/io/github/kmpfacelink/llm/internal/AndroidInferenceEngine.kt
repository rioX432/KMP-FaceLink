package io.github.kmpfacelink.llm.internal

import io.github.kmpfacelink.llm.InferenceEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.withContext

private const val DEFAULT_N_CTX = 2048
private const val DEFAULT_N_GPU_LAYERS = 0

/**
 * Android [InferenceEngine] backed by llama.cpp via JNI.
 *
 * Loads GGUF models and generates text using the llama.cpp library.
 * Streaming is implemented via [callbackFlow] bridging JNI callbacks to Kotlin Flow.
 *
 * @param nCtx Context window size for the llama.cpp model
 * @param nGpuLayers Number of layers to offload to GPU (0 = CPU only)
 */
internal class AndroidInferenceEngine(
    private val nCtx: Int = DEFAULT_N_CTX,
    private val nGpuLayers: Int = DEFAULT_N_GPU_LAYERS,
) : InferenceEngine {

    private val bridge = LlamaCppBridge()

    override suspend fun load(modelPath: String) {
        withContext(Dispatchers.IO) {
            val success = bridge.initModel(modelPath, nCtx, nGpuLayers)
            check(success) { "Failed to load model from $modelPath" }
        }
    }

    override fun generate(
        prompt: String,
        maxTokens: Int,
        temperature: Double,
    ): Flow<String> = callbackFlow {
        withContext(Dispatchers.IO) {
            bridge.generate(
                prompt = prompt,
                maxTokens = maxTokens,
                temperature = temperature.toFloat(),
            ) { token ->
                trySend(token)
            }
        }
        close()
        awaitClose()
    }

    override suspend fun unload() {
        withContext(Dispatchers.IO) {
            bridge.release()
        }
    }

    override val isLoaded: Boolean
        get() = bridge.isModelLoaded()
}
