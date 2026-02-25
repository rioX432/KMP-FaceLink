@file:OptIn(ExperimentalForeignApi::class)

package io.github.kmpfacelink.voice.asr.internal

import cnames.structs.whisper_context
import kotlinx.cinterop.CPointer
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.cstr
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.pointed
import kotlinx.cinterop.readValue
import kotlinx.cinterop.toKString
import kotlinx.cinterop.usePinned
import whisper.whisper_free
import whisper.whisper_full
import whisper.whisper_full_default_params
import whisper.whisper_full_get_segment_text
import whisper.whisper_full_n_segments
import whisper.whisper_init_from_file
import whisper.whisper_sampling_strategy

/**
 * iOS cinterop bridge to whisper.cpp.
 *
 * Uses Kotlin/Native cinterop bindings generated from whisper.h.
 */
internal actual class WhisperCppBridge actual constructor() {
    private var ctx: CPointer<whisper_context>? = null

    actual fun initModel(modelPath: String): Boolean {
        release()
        ctx = whisper_init_from_file(modelPath)
        return ctx != null
    }

    actual fun transcribe(samples: FloatArray, language: String?, threads: Int): String {
        val context = ctx
        check(context != null) { "Whisper model not initialized" }

        return memScoped {
            val params = whisper_full_default_params(
                whisper_sampling_strategy.WHISPER_SAMPLING_GREEDY,
            ).getPointer(this).pointed
            params.print_progress = false
            params.print_timestamps = false
            params.n_threads = threads
            params.single_segment = true
            if (language != null) {
                // cstr is allocated in this memScoped arena — valid through whisper_full call
                params.language = language.cstr.getPointer(this)
            }

            val rc = samples.usePinned { pinned ->
                whisper_full(context, params.readValue(), pinned.addressOf(0), samples.size)
            }
            check(rc == 0) { "whisper_full failed with code $rc" }

            val segments = whisper_full_n_segments(context)
            buildString {
                for (i in 0 until segments) {
                    whisper_full_get_segment_text(context, i)?.toKString()?.let(::append)
                }
            }
        }
    }

    actual fun isModelLoaded(): Boolean = ctx != null

    actual fun release() {
        ctx?.let { whisper_free(it) }
        ctx = null
    }
}
