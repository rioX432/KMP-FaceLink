package io.github.kmpfacelink.voice.audio

import io.github.kmpfacelink.api.Releasable
import kotlinx.coroutines.flow.StateFlow

/**
 * Platform audio player for PCM playback.
 *
 * Implements [Releasable] for consistent lifecycle management.
 */
public interface AudioPlayer : Releasable {
    /** Whether audio is currently playing. */
    public val isPlaying: StateFlow<Boolean>

    /**
     * Plays the given audio data. Suspends until playback completes.
     *
     * @param audio Audio data to play
     */
    public suspend fun play(audio: AudioData)

    /** Stops any currently playing audio. */
    public fun stop()

    /** Releases all resources held by this player. */
    public override fun release()
}
