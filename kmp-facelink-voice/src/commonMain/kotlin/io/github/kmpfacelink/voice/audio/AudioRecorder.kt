package io.github.kmpfacelink.voice.audio

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/**
 * Platform audio recorder for capturing microphone input.
 */
public interface AudioRecorder {
    /** Whether the recorder is currently capturing audio. */
    public val isRecording: StateFlow<Boolean>

    /**
     * Starts recording audio with the given format.
     *
     * @param format Target audio format (default: 16kHz mono 16-bit)
     */
    public suspend fun start(format: AudioFormat = AudioFormat())

    /**
     * Stops recording and returns the complete captured audio.
     *
     * @return Captured audio data, or null if no audio was recorded
     */
    public suspend fun stop(): AudioData?

    /** Flow of raw audio chunks emitted during recording. Useful for streaming ASR. */
    public val audioChunks: Flow<ByteArray>

    /** Releases all resources held by this recorder. */
    public fun release()
}
