package io.github.kmpfacelink.voice.asr

import io.github.kmpfacelink.api.Releasable
import io.github.kmpfacelink.voice.EngineState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlin.io.Closeable

/**
 * State of an ASR engine.
 */
public sealed class AsrState : EngineState {
    /** Engine is idle and ready. */
    public data object Idle : AsrState(), EngineState.Idle

    /** Engine is actively listening for speech. */
    public data object Listening : AsrState(), EngineState.Active

    /** Engine is processing recorded audio. */
    public data object Processing : AsrState(), EngineState.Active

    /** An error occurred. */
    public data class Error(override val message: String) : AsrState(), EngineState.Error
}

/**
 * Speech-to-text engine interface.
 *
 * Supports both one-shot transcription and continuous listening mode.
 *
 * Implements [Releasable] for consistent lifecycle management and [Closeable]
 * so engines can be used with `use {}` blocks. [close] delegates to [release].
 */
public interface AsrEngine : Releasable, Closeable {
    /** Current engine state. */
    public val state: StateFlow<AsrState>

    /**
     * Transcribes a single audio buffer.
     *
     * @param audio Audio data to transcribe
     * @return Transcription result
     */
    public suspend fun transcribe(audio: io.github.kmpfacelink.voice.audio.AudioData): TranscriptionResult

    /** Starts continuous listening mode. Transcriptions are emitted via [transcriptions]. */
    public suspend fun startListening()

    /** Stops continuous listening mode. */
    public suspend fun stopListening()

    /** Flow of transcription results during continuous listening. */
    public val transcriptions: Flow<TranscriptionResult>

    /** Releases all resources held by this engine. Idempotent. */
    public override fun release()

    /** Delegates to [release]. */
    override fun close(): Unit = release()
}
