package io.github.kmpfacelink.voice.audio.internal

import io.github.kmpfacelink.voice.audio.AudioData
import io.github.kmpfacelink.voice.audio.AudioPlayer
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import platform.AVFAudio.AVAudioPlayer
import platform.AVFAudio.AVAudioSession
import platform.AVFAudio.AVAudioSessionCategoryPlayback
import platform.AVFAudio.setActive
import platform.Foundation.NSData
import platform.Foundation.create

private const val POLL_INTERVAL_MS = 50L

/**
 * iOS [AudioPlayer] implementation using [AVAudioPlayer].
 */
@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
internal class PlatformAudioPlayer : AudioPlayer {

    private val _isPlaying = MutableStateFlow(false)
    override val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private var player: AVAudioPlayer? = null

    override suspend fun play(audio: AudioData) {
        stop()

        val session = AVAudioSession.sharedInstance()
        session.setCategory(AVAudioSessionCategoryPlayback, null)
        session.setActive(true, null)

        // Create WAV data with header for AVAudioPlayer
        val wavBytes = createWavData(audio)
        val nsData = wavBytes.usePinned { pinned ->
            NSData.create(
                bytes = pinned.addressOf(0),
                length = wavBytes.size.toULong(),
            )
        }

        val audioPlayer = AVAudioPlayer(nsData, null)
        player = audioPlayer
        _isPlaying.value = true

        audioPlayer.prepareToPlay()
        audioPlayer.play()

        // Wait for playback to complete
        while (audioPlayer.isPlaying()) {
            delay(POLL_INTERVAL_MS)
        }

        _isPlaying.value = false
    }

    override fun stop() {
        player?.stop()
        player = null
        _isPlaying.value = false
    }

    override fun release() {
        stop()
    }

    @Suppress("MagicNumber")
    private fun createWavData(audio: AudioData): ByteArray {
        val dataSize = audio.bytes.size
        val fileSize = dataSize + 36
        val byteRate = audio.format.sampleRate * audio.format.channels * audio.format.bitsPerSample / 8
        val blockAlign = audio.format.channels * audio.format.bitsPerSample / 8

        val header = ByteArray(44)
        // RIFF header
        header[0] = 'R'.code.toByte()
        header[1] = 'I'.code.toByte()
        header[2] = 'F'.code.toByte()
        header[3] = 'F'.code.toByte()
        writeInt32LE(header, 4, fileSize)
        header[8] = 'W'.code.toByte()
        header[9] = 'A'.code.toByte()
        header[10] = 'V'.code.toByte()
        header[11] = 'E'.code.toByte()

        // fmt chunk
        header[12] = 'f'.code.toByte()
        header[13] = 'm'.code.toByte()
        header[14] = 't'.code.toByte()
        header[15] = ' '.code.toByte()
        writeInt32LE(header, 16, 16)
        writeInt16LE(header, 20, 1) // PCM format
        writeInt16LE(header, 22, audio.format.channels)
        writeInt32LE(header, 24, audio.format.sampleRate)
        writeInt32LE(header, 28, byteRate)
        writeInt16LE(header, 32, blockAlign)
        writeInt16LE(header, 34, audio.format.bitsPerSample)

        // data chunk
        header[36] = 'd'.code.toByte()
        header[37] = 'a'.code.toByte()
        header[38] = 't'.code.toByte()
        header[39] = 'a'.code.toByte()
        writeInt32LE(header, 40, dataSize)

        return header + audio.bytes
    }

    @Suppress("MagicNumber")
    private fun writeInt32LE(buffer: ByteArray, offset: Int, value: Int) {
        buffer[offset] = (value and 0xFF).toByte()
        buffer[offset + 1] = ((value shr 8) and 0xFF).toByte()
        buffer[offset + 2] = ((value shr 16) and 0xFF).toByte()
        buffer[offset + 3] = ((value shr 24) and 0xFF).toByte()
    }

    @Suppress("MagicNumber")
    private fun writeInt16LE(buffer: ByteArray, offset: Int, value: Int) {
        buffer[offset] = (value and 0xFF).toByte()
        buffer[offset + 1] = ((value shr 8) and 0xFF).toByte()
    }
}
