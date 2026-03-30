package io.github.kmpfacelink.voice.asr

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlin.io.Closeable

/**
 * State of an ASR engine.
 */
public sealed class AsrState {
    /** Engine is idle and ready. */
    public data object Idle : AsrState()

    /** Engine is actively listening for speech. */
    public data object Listening : AsrState()

    /** Engine is processing recorded audio. */
    public data object Processing : AsrState()

    /** An error occurred. */
    public data class Error(val message: String) : AsrState()
}

/**
 * Speech-to-text engine interface.
 *
 * Supports both one-shot transcription and continuous listening mode.
 *
 * Implements [Closeable] so engines can be used with `use {}` blocks or
 * other resource-management patterns. [close] delegates to [release].
 */
public interface AsrEngine : Closeable {
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
    public fun release()

    /** Delegates to [release]. */
    override fun close(): Unit = release()
}
