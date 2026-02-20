package io.github.kmpfacelink.voice.audio

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Fake [AudioRecorder] for testing.
 */
class FakeAudioRecorder : AudioRecorder {

    private val _isRecording = MutableStateFlow(false)
    override val isRecording: StateFlow<Boolean> = _isRecording.asStateFlow()

    private val _audioChunks = MutableSharedFlow<ByteArray>(extraBufferCapacity = 16)
    override val audioChunks: Flow<ByteArray> = _audioChunks.asSharedFlow()

    /** Audio data to return from [stop]. Set before calling [stop]. */
    var audioDataToReturn: AudioData? = null

    override suspend fun start(format: AudioFormat) {
        _isRecording.value = true
    }

    override suspend fun stop(): AudioData? {
        _isRecording.value = false
        return audioDataToReturn
    }

    /** Emit a chunk of audio data (for testing streaming ASR). */
    fun emitChunk(chunk: ByteArray) {
        _audioChunks.tryEmit(chunk)
    }

    override fun release() {
        _isRecording.value = false
    }
}
