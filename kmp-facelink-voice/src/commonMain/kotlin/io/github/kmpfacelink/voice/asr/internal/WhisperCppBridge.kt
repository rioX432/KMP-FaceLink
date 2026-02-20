package io.github.kmpfacelink.voice.asr.internal

/**
 * Platform bridge to whisper.cpp native library.
 *
 * Android: JNI wrapper. iOS: cinterop bridge.
 */
internal expect class WhisperCppBridge() {
    /** Initializes the whisper model from the given file path. Returns true on success. */
    fun initModel(modelPath: String): Boolean

    /** Transcribes audio (16kHz mono Float32 PCM). Returns transcribed text. */
    fun transcribe(samples: FloatArray, language: String?, threads: Int): String

    /** Whether a model is currently loaded. */
    fun isModelLoaded(): Boolean

    /** Releases native resources. */
    fun release()
}
