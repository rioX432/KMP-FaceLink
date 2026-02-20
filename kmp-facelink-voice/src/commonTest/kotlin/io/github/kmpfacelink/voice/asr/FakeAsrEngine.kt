package io.github.kmpfacelink.voice.asr

import io.github.kmpfacelink.voice.audio.AudioData
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Fake [AsrEngine] for testing.
 */
class FakeAsrEngine : AsrEngine {

    private val _state = MutableStateFlow<AsrState>(AsrState.Idle)
    override val state: StateFlow<AsrState> = _state.asStateFlow()

    private val _transcriptions = MutableSharedFlow<TranscriptionResult>(extraBufferCapacity = 16)
    override val transcriptions: Flow<TranscriptionResult> = _transcriptions.asSharedFlow()

    /** Result to return from [transcribe]. */
    var resultToReturn = TranscriptionResult(text = "Hello, world!")

    /** Number of times [transcribe] was called. */
    var transcribeCount: Int = 0

    override suspend fun transcribe(audio: AudioData): TranscriptionResult {
        transcribeCount++
        _state.value = AsrState.Processing
        _state.value = AsrState.Idle
        return resultToReturn
    }

    override suspend fun startListening() {
        _state.value = AsrState.Listening
    }

    override suspend fun stopListening() {
        _state.value = AsrState.Idle
    }

    /** Emit a transcription result (for testing continuous listening). */
    fun emitTranscription(result: TranscriptionResult) {
        _transcriptions.tryEmit(result)
    }

    override fun release() {
        _state.value = AsrState.Idle
    }
}
