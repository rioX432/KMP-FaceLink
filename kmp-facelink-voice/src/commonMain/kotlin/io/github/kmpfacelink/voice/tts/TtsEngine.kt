package io.github.kmpfacelink.voice.tts

import kotlinx.coroutines.flow.StateFlow
import kotlin.io.Closeable
/**
 * State of a TTS engine.
 */
public sealed class TtsState {
    /** Engine is idle and ready. */
    public data object Idle : TtsState()

    /** Engine is synthesizing speech. */
    public data object Synthesizing : TtsState()

    /** An error occurred. */
    public data class Error(val message: String) : TtsState()
}

/**
 * Text-to-speech engine interface.
 *
 * Implements [Closeable] so engines can be used with `use {}` blocks or
 * other resource-management patterns. [close] delegates to [release].
 */
public interface TtsEngine : Closeable {
    /** Current engine state. */
    public val state: StateFlow<TtsState>

    /**
     * Synthesizes speech from text.
     *
     * @param text Text to synthesize
     * @return TTS result with audio and optional phoneme timing
     */
    public suspend fun synthesize(text: String): TtsResult

    /** Releases all resources held by this engine. Idempotent. */
    public fun release()

    /** Delegates to [release]. */
    override fun close(): Unit = release()
}
