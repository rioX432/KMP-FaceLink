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
 * Constants for building a canonical 44-byte RIFF/WAVE PCM header.
 *
 * Layout reference (byte offsets):
 * ```
 *  0..3   "RIFF"
 *  4..7   ChunkSize  (file size − 8)
 *  8..11  "WAVE"
 * 12..15  "fmt "
 * 16..19  Subchunk1Size  (16 for PCM)
 * 20..21  AudioFormat     (1 = PCM)
 * 22..23  NumChannels
 * 24..27  SampleRate
 * 28..31  ByteRate        (SampleRate × NumChannels × BitsPerSample / 8)
 * 32..33  BlockAlign      (NumChannels × BitsPerSample / 8)
 * 34..35  BitsPerSample
 * 36..39  "data"
 * 40..43  Subchunk2Size   (raw PCM data size)
 * ```
 */
private object WavHeader {
    // ── Encoding constants ──

    /** Mask for extracting a single byte from an Int. */
    const val BYTE_MASK = 0xFF

    /** Number of bits in one byte. */
    const val BITS_PER_BYTE = 8

    /** Total size of the canonical WAV header in bytes. */
    const val HEADER_SIZE = 44

    /** Number of bytes in the header excluding the 8-byte RIFF preamble (ChunkSize value offset). */
    const val EXTRA_HEADER_BYTES = 36

    /** Size of the "fmt " sub-chunk for PCM (no extra params). */
    const val FMT_CHUNK_SIZE = 16

    /** AudioFormat value for uncompressed PCM. */
    const val PCM_FORMAT = 1

    // ── Bit-shift widths for little-endian Int encoding ──
    const val SHIFT_8 = 8
    const val SHIFT_16 = 16
    const val SHIFT_24 = 24

    /** Byte-offset of the fourth byte within a 32-bit word (index 3). */
    const val INT32_BYTE3 = 3

    // ── Byte offsets within the 44-byte header ──

    /** Offset of the ChunkSize field (file size − 8). */
    const val OFFSET_FILE_SIZE = 4

    /** Offset of the "WAVE" format tag. */
    const val OFFSET_WAVE_TAG = 8

    /** Offset of the "fmt " sub-chunk tag. */
    const val OFFSET_FMT_TAG = 12

    /** Offset of the Subchunk1Size field. */
    const val OFFSET_FMT_CHUNK_SIZE = 16

    /** Offset of the AudioFormat field. */
    const val OFFSET_AUDIO_FORMAT = 20

    /** Offset of the NumChannels field. */
    const val OFFSET_CHANNELS = 22

    /** Offset of the SampleRate field. */
    const val OFFSET_SAMPLE_RATE = 24

    /** Offset of the ByteRate field. */
    const val OFFSET_BYTE_RATE = 28

    /** Offset of the BlockAlign field. */
    const val OFFSET_BLOCK_ALIGN = 32

    /** Offset of the BitsPerSample field. */
    const val OFFSET_BITS_PER_SAMPLE = 34

    /** Offset of the "data" sub-chunk tag. */
    const val OFFSET_DATA_TAG = 36

    /** Offset of the Subchunk2Size (data size) field. */
    const val OFFSET_DATA_SIZE = 40
}

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
        val fileSize = dataSize + WavHeader.EXTRA_HEADER_BYTES
        val byteRate =
            audio.format.sampleRate * audio.format.channels * audio.format.bitsPerSample / WavHeader.BITS_PER_BYTE
        val blockAlign = audio.format.channels * audio.format.bitsPerSample / WavHeader.BITS_PER_BYTE

        val header = ByteArray(WavHeader.HEADER_SIZE)
        // RIFF header
        header[0] = 'R'.code.toByte()
        header[1] = 'I'.code.toByte()
        header[2] = 'F'.code.toByte()
        header[WavHeader.INT32_BYTE3] = 'F'.code.toByte()
        writeInt32LE(header, WavHeader.OFFSET_FILE_SIZE, fileSize)
        header[WavHeader.OFFSET_WAVE_TAG + 0] = 'W'.code.toByte()
        header[WavHeader.OFFSET_WAVE_TAG + 1] = 'A'.code.toByte()
        header[WavHeader.OFFSET_WAVE_TAG + 2] = 'V'.code.toByte()
        header[WavHeader.OFFSET_WAVE_TAG + WavHeader.INT32_BYTE3] = 'E'.code.toByte()

        // fmt chunk
        header[WavHeader.OFFSET_FMT_TAG + 0] = 'f'.code.toByte()
        header[WavHeader.OFFSET_FMT_TAG + 1] = 'm'.code.toByte()
        header[WavHeader.OFFSET_FMT_TAG + 2] = 't'.code.toByte()
        header[WavHeader.OFFSET_FMT_TAG + WavHeader.INT32_BYTE3] = ' '.code.toByte()
        writeInt32LE(header, WavHeader.OFFSET_FMT_CHUNK_SIZE, WavHeader.FMT_CHUNK_SIZE)
        writeInt16LE(header, WavHeader.OFFSET_AUDIO_FORMAT, WavHeader.PCM_FORMAT)
        writeInt16LE(header, WavHeader.OFFSET_CHANNELS, audio.format.channels)
        writeInt32LE(header, WavHeader.OFFSET_SAMPLE_RATE, audio.format.sampleRate)
        writeInt32LE(header, WavHeader.OFFSET_BYTE_RATE, byteRate)
        writeInt16LE(header, WavHeader.OFFSET_BLOCK_ALIGN, blockAlign)
        writeInt16LE(header, WavHeader.OFFSET_BITS_PER_SAMPLE, audio.format.bitsPerSample)

        // data chunk
        header[WavHeader.OFFSET_DATA_TAG + 0] = 'd'.code.toByte()
        header[WavHeader.OFFSET_DATA_TAG + 1] = 'a'.code.toByte()
        header[WavHeader.OFFSET_DATA_TAG + 2] = 't'.code.toByte()
        header[WavHeader.OFFSET_DATA_TAG + WavHeader.INT32_BYTE3] = 'a'.code.toByte()
        writeInt32LE(header, WavHeader.OFFSET_DATA_SIZE, dataSize)

        return header + audio.bytes
    }

    private fun writeInt32LE(buffer: ByteArray, offset: Int, value: Int) {
        buffer[offset] = (value and WavHeader.BYTE_MASK).toByte()
        buffer[offset + 1] = ((value shr WavHeader.SHIFT_8) and WavHeader.BYTE_MASK).toByte()
        buffer[offset + 2] = ((value shr WavHeader.SHIFT_16) and WavHeader.BYTE_MASK).toByte()
        buffer[offset + WavHeader.INT32_BYTE3] = ((value shr WavHeader.SHIFT_24) and WavHeader.BYTE_MASK).toByte()
    }

    private fun writeInt16LE(buffer: ByteArray, offset: Int, value: Int) {
        buffer[offset] = (value and WavHeader.BYTE_MASK).toByte()
        buffer[offset + 1] = ((value shr WavHeader.SHIFT_8) and WavHeader.BYTE_MASK).toByte()
    }
}
