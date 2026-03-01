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

// WAV format constants
private const val BYTE_MASK = 0xFF
private const val BITS_PER_BYTE = 8
private const val WAV_HEADER_SIZE = 44
private const val WAV_EXTRA_HEADER_BYTES = 36
private const val WAV_FMT_CHUNK_SIZE = 16
private const val WAV_PCM_FORMAT = 1
private const val BIT_SHIFT_8 = 8
private const val BIT_SHIFT_16 = 16
private const val BIT_SHIFT_24 = 24

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

    private fun createWavData(audio: AudioData): ByteArray {
        val dataSize = audio.bytes.size
        val fileSize = dataSize + WAV_EXTRA_HEADER_BYTES
        val byteRate = audio.format.sampleRate * audio.format.channels * audio.format.bitsPerSample / BITS_PER_BYTE
        val blockAlign = audio.format.channels * audio.format.bitsPerSample / BITS_PER_BYTE

        val header = ByteArray(WAV_HEADER_SIZE)
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
        writeInt32LE(header, 16, WAV_FMT_CHUNK_SIZE)
        writeInt16LE(header, 20, WAV_PCM_FORMAT)
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

    private fun writeInt32LE(buffer: ByteArray, offset: Int, value: Int) {
        buffer[offset] = (value and BYTE_MASK).toByte()
        buffer[offset + 1] = ((value shr BIT_SHIFT_8) and BYTE_MASK).toByte()
        buffer[offset + 2] = ((value shr BIT_SHIFT_16) and BYTE_MASK).toByte()
        buffer[offset + 3] = ((value shr BIT_SHIFT_24) and BYTE_MASK).toByte()
    }

    private fun writeInt16LE(buffer: ByteArray, offset: Int, value: Int) {
        buffer[offset] = (value and BYTE_MASK).toByte()
        buffer[offset + 1] = ((value shr BIT_SHIFT_8) and BYTE_MASK).toByte()
    }
}
