package io.github.kmpfacelink.voice.asr.internal

/**
 * Android JNI bridge to whisper.cpp.
 *
 * Loads libwhisper_jni.so and delegates to native methods.
 *
 * All access to [contextPtr] is guarded by [lock] to prevent use-after-free or
 * double-free crashes when [initModel], [transcribe], and [release] are called
 * concurrently from different threads.
 */
internal actual class WhisperCppBridge actual constructor() {
    private val lock = Any()
    private var contextPtr: Long = 0L

    actual fun initModel(modelPath: String): Boolean =
        synchronized(lock) {
            contextPtr = nativeInit(modelPath)
            contextPtr != 0L
        }

    actual fun transcribe(samples: FloatArray, language: String?, threads: Int): String =
        synchronized(lock) {
            check(contextPtr != 0L) { "Whisper model not initialized" }
            nativeTranscribe(contextPtr, samples, language, threads)
        }

    actual fun isModelLoaded(): Boolean =
        synchronized(lock) { contextPtr != 0L }

    actual fun release() {
        synchronized(lock) {
            if (contextPtr != 0L) {
                nativeFree(contextPtr)
                contextPtr = 0L
            }
        }
    }

    private companion object {
        init {
            System.loadLibrary("whisper_jni")
        }
    }

    private external fun nativeInit(modelPath: String): Long
    private external fun nativeTranscribe(
        ctx: Long,
        samples: FloatArray,
        language: String?,
        threads: Int,
    ): String
    private external fun nativeFree(ctx: Long)
}
