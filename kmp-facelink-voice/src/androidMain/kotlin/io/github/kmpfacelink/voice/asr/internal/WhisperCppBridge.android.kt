package io.github.kmpfacelink.voice.asr.internal

/**
 * Android JNI bridge to whisper.cpp.
 *
 * Loads libwhisper_jni.so and delegates to native methods.
 */
internal actual class WhisperCppBridge actual constructor() {
    private var contextPtr: Long = 0L

    actual fun initModel(modelPath: String): Boolean {
        contextPtr = nativeInit(modelPath)
        return contextPtr != 0L
    }

    actual fun transcribe(samples: FloatArray, language: String?, threads: Int): String {
        check(contextPtr != 0L) { "Whisper model not initialized" }
        return nativeTranscribe(contextPtr, samples, language, threads)
    }

    actual fun isModelLoaded(): Boolean = contextPtr != 0L

    actual fun release() {
        if (contextPtr != 0L) {
            nativeFree(contextPtr)
            contextPtr = 0L
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
