package io.github.kmpfacelink.voice.audio

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Fake [AudioPlayer] for testing.
 */
class FakeAudioPlayer : AudioPlayer {

    private val _isPlaying = MutableStateFlow(false)
    override val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    /** Last audio data passed to [play]. */
    var lastPlayedAudio: AudioData? = null

    /** Number of times [play] was called. */
    var playCount: Int = 0

    override suspend fun play(audio: AudioData) {
        lastPlayedAudio = audio
        playCount++
        _isPlaying.value = true
        // Immediately finish in tests
        _isPlaying.value = false
    }

    override fun stop() {
        _isPlaying.value = false
    }

    override fun release() {
        stop()
    }
}
