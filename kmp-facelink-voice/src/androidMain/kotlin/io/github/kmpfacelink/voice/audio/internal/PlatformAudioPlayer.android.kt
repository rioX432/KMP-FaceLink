package io.github.kmpfacelink.voice.audio.internal

import android.media.AudioTrack
import io.github.kmpfacelink.voice.audio.AudioData
import io.github.kmpfacelink.voice.audio.AudioPlayer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume

/**
 * Android [AudioPlayer] implementation using [AudioTrack].
 */
internal class PlatformAudioPlayer : AudioPlayer {

    private val _isPlaying = MutableStateFlow(false)
    override val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private var audioTrack: AudioTrack? = null

    override suspend fun play(audio: AudioData) {
        stop()

        val channelConfig = if (audio.format.channels == 1) {
            android.media.AudioFormat.CHANNEL_OUT_MONO
        } else {
            android.media.AudioFormat.CHANNEL_OUT_STEREO
        }

        val audioEncoding = when (audio.format.bitsPerSample) {
            Byte.SIZE_BITS -> android.media.AudioFormat.ENCODING_PCM_8BIT
            else -> android.media.AudioFormat.ENCODING_PCM_16BIT
        }

        val bufferSize = AudioTrack.getMinBufferSize(
            audio.format.sampleRate,
            channelConfig,
            audioEncoding,
        )

        val track = AudioTrack.Builder()
            .setAudioFormat(
                android.media.AudioFormat.Builder()
                    .setSampleRate(audio.format.sampleRate)
                    .setChannelMask(channelConfig)
                    .setEncoding(audioEncoding)
                    .build(),
            )
            .setBufferSizeInBytes(maxOf(bufferSize, audio.bytes.size))
            .setTransferMode(AudioTrack.MODE_STATIC)
            .build()

        audioTrack = track
        _isPlaying.value = true

        withContext(Dispatchers.IO) {
            track.write(audio.bytes, 0, audio.bytes.size)
            track.play()

            suspendCancellableCoroutine { cont ->
                track.setPlaybackPositionUpdateListener(
                    object : AudioTrack.OnPlaybackPositionUpdateListener {
                        override fun onMarkerReached(t: AudioTrack?) {
                            _isPlaying.value = false
                            if (cont.isActive) cont.resume(Unit)
                        }

                        override fun onPeriodicNotification(t: AudioTrack?) {
                            // Not used; marker callback handles completion
                        }
                    },
                )

                val bytesPerSample = audio.format.bitsPerSample / Byte.SIZE_BITS
                val totalFrames = audio.bytes.size / bytesPerSample / audio.format.channels
                track.notificationMarkerPosition = totalFrames

                cont.invokeOnCancellation {
                    track.stop()
                    _isPlaying.value = false
                }
            }
        }
    }

    override fun stop() {
        audioTrack?.let { track ->
            try {
                track.stop()
            } catch (_: IllegalStateException) {
                // Already stopped
            }
        }
        _isPlaying.value = false
    }

    override fun release() {
        stop()
        audioTrack?.release()
        audioTrack = null
    }
}
