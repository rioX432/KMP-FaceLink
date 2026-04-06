package io.github.kmpfacelink.llm.internal

private const val DEFAULT_CONTEXT_SIZE = 4096
private const val DEFAULT_GPU_LAYERS = 32

/**
 * Android JNI bridge to llama.cpp.
 *
 * Loads libllama_jni.so and delegates to native methods.
 *
 * All access to [contextPtr] is guarded by [lock] to prevent use-after-free or
 * double-free crashes when [initModel], [generate], and [release] are called
 * concurrently from different threads.
 */
internal class LlamaCppBridge {
    private val lock = Any()
    private var contextPtr: Long = 0L

    fun initModel(
        modelPath: String,
        nCtx: Int = DEFAULT_CONTEXT_SIZE,
        nGpuLayers: Int = DEFAULT_GPU_LAYERS,
    ): Boolean =
        synchronized(lock) {
            contextPtr = nativeInit(modelPath, nCtx, nGpuLayers)
            contextPtr != 0L
        }

    fun generate(
        prompt: String,
        maxTokens: Int,
        temperature: Float,
        callback: LlamaCallback,
    ): String =
        synchronized(lock) {
            check(contextPtr != 0L) { "Llama model not initialized" }
            nativeGenerate(contextPtr, prompt, maxTokens, temperature, callback)
        }

    fun isModelLoaded(): Boolean =
        synchronized(lock) { contextPtr != 0L }

    fun release() {
        synchronized(lock) {
            if (contextPtr != 0L) {
                nativeFree(contextPtr)
                contextPtr = 0L
            }
        }
    }

    private companion object {
        init {
            System.loadLibrary("llama_jni")
        }
    }

    private external fun nativeInit(modelPath: String, nCtx: Int, nGpuLayers: Int): Long
    private external fun nativeGenerate(
        ctx: Long,
        prompt: String,
        maxTokens: Int,
        temperature: Float,
        callback: LlamaCallback,
    ): String
    private external fun nativeFree(ctx: Long)
}
