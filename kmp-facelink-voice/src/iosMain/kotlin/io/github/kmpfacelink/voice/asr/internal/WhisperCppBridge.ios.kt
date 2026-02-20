package io.github.kmpfacelink.voice.asr.internal

// Stub: wire up cinterop bindings once whisper.def is configured.
// Throws UnsupportedOperationException until native library is linked.

/**
 * iOS cinterop bridge to whisper.cpp.
 *
 * Uses Kotlin/Native cinterop bindings generated from whisper.h.
 */
internal actual class WhisperCppBridge actual constructor() {
    private var initialized = false

    actual fun initModel(modelPath: String): Boolean {
        // Requires whisper_init_from_file via cinterop
        throw UnsupportedOperationException(
            "whisper.cpp iOS cinterop not yet configured. Add whisper.def and libwhisper.a.",
        )
    }

    actual fun transcribe(samples: FloatArray, language: String?, threads: Int): String {
        check(initialized) { "Whisper model not initialized" }
        // Requires whisper_full via cinterop
        throw UnsupportedOperationException("whisper.cpp iOS cinterop not yet configured")
    }

    actual fun isModelLoaded(): Boolean = initialized

    actual fun release() {
        initialized = false
    }
}
