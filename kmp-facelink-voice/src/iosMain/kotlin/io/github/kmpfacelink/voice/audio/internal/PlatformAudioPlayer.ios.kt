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
private const val INT32_BYTE3_OFFSET = 3

// WAV header byte offsets
private const val WAV_OFFSET_FILE_SIZE = 4
private const val WAV_OFFSET_WAVE_TAG = 8
private const val WAV_OFFSET_FMT_TAG = 12
private const val WAV_OFFSET_FMT_CHUNK_SIZE_FIELD = 16
private const val WAV_OFFSET_AUDIO_FORMAT = 20
private const val WAV_OFFSET_CHANNELS = 22
private const val WAV_OFFSET_SAMPLE_RATE = 24
private const val WAV_OFFSET_BYTE_RATE = 28
private const val WAV_OFFSET_BLOCK_ALIGN = 32
private const val WAV_OFFSET_BITS_PER_SAMPLE = 34
private const val WAV_OFFSET_DATA_TAG = 36
private const val WAV_OFFSET_DATA_SIZE = 40

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
        header[INT32_BYTE3_OFFSET] = 'F'.code.toByte()
        writeInt32LE(header, WAV_OFFSET_FILE_SIZE, fileSize)
        header[WAV_OFFSET_WAVE_TAG + 0] = 'W'.code.toByte()
        header[WAV_OFFSET_WAVE_TAG + 1] = 'A'.code.toByte()
        header[WAV_OFFSET_WAVE_TAG + 2] = 'V'.code.toByte()
        header[WAV_OFFSET_WAVE_TAG + INT32_BYTE3_OFFSET] = 'E'.code.toByte()

        // fmt chunk
        header[WAV_OFFSET_FMT_TAG + 0] = 'f'.code.toByte()
        header[WAV_OFFSET_FMT_TAG + 1] = 'm'.code.toByte()
        header[WAV_OFFSET_FMT_TAG + 2] = 't'.code.toByte()
        header[WAV_OFFSET_FMT_TAG + INT32_BYTE3_OFFSET] = ' '.code.toByte()
        writeInt32LE(header, WAV_OFFSET_FMT_CHUNK_SIZE_FIELD, WAV_FMT_CHUNK_SIZE)
        writeInt16LE(header, WAV_OFFSET_AUDIO_FORMAT, WAV_PCM_FORMAT)
        writeInt16LE(header, WAV_OFFSET_CHANNELS, audio.format.channels)
        writeInt32LE(header, WAV_OFFSET_SAMPLE_RATE, audio.format.sampleRate)
        writeInt32LE(header, WAV_OFFSET_BYTE_RATE, byteRate)
        writeInt16LE(header, WAV_OFFSET_BLOCK_ALIGN, blockAlign)
        writeInt16LE(header, WAV_OFFSET_BITS_PER_SAMPLE, audio.format.bitsPerSample)

        // data chunk
        header[WAV_OFFSET_DATA_TAG + 0] = 'd'.code.toByte()
        header[WAV_OFFSET_DATA_TAG + 1] = 'a'.code.toByte()
        header[WAV_OFFSET_DATA_TAG + 2] = 't'.code.toByte()
        header[WAV_OFFSET_DATA_TAG + INT32_BYTE3_OFFSET] = 'a'.code.toByte()
        writeInt32LE(header, WAV_OFFSET_DATA_SIZE, dataSize)

        return header + audio.bytes
    }

    private fun writeInt32LE(buffer: ByteArray, offset: Int, value: Int) {
        buffer[offset] = (value and BYTE_MASK).toByte()
        buffer[offset + 1] = ((value shr BIT_SHIFT_8) and BYTE_MASK).toByte()
        buffer[offset + 2] = ((value shr BIT_SHIFT_16) and BYTE_MASK).toByte()
        buffer[offset + INT32_BYTE3_OFFSET] = ((value shr BIT_SHIFT_24) and BYTE_MASK).toByte()
    }

    private fun writeInt16LE(buffer: ByteArray, offset: Int, value: Int) {
        buffer[offset] = (value and BYTE_MASK).toByte()
        buffer[offset + 1] = ((value shr BIT_SHIFT_8) and BYTE_MASK).toByte()
    }
}
