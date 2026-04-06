package io.github.kmpfacelink.voice.tts

import io.github.kmpfacelink.api.Releasable
import io.github.kmpfacelink.voice.EngineState
import kotlinx.coroutines.flow.StateFlow

/**
 * State of a TTS engine.
 */
public sealed class TtsState : EngineState {
    /** Engine is idle and ready. */
    public data object Idle : TtsState(), EngineState.Idle

    /** Engine is synthesizing speech. */
    public data object Synthesizing : TtsState(), EngineState.Active

    /** An error occurred. */
    public data class Error(override val message: String) : TtsState(), EngineState.Error
}

/**
 * Text-to-speech engine interface.
 *
 * Implements [Releasable] for consistent lifecycle management and [Closeable]
 * so engines can be used with `use {}` blocks. [close] delegates to [release].
 */
public interface TtsEngine : Releasable, AutoCloseable {
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
    public override fun release()

    /** Delegates to [release]. */
    override fun close(): Unit = release()
}
