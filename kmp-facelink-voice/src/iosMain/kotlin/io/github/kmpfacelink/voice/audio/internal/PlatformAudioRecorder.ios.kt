package io.github.kmpfacelink.voice.audio.internal

import io.github.kmpfacelink.voice.AudioConstants
import io.github.kmpfacelink.voice.audio.AudioData
import io.github.kmpfacelink.voice.audio.AudioFormat
import io.github.kmpfacelink.voice.audio.AudioRecorder
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.ObjCObjectVar
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.alloc
import kotlinx.cinterop.get
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.usePinned
import kotlinx.cinterop.value
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import platform.AVFAudio.AVAudioEngine
import platform.AVFAudio.AVAudioPCMBuffer
import platform.AVFAudio.AVAudioSession
import platform.AVFAudio.AVAudioSessionCategoryRecord
import platform.AVFAudio.setActive
import platform.Foundation.NSError
import platform.Foundation.NSMutableData
import platform.Foundation.NSRecursiveLock
import platform.Foundation.appendBytes
import platform.posix.memcpy

private const val BUFFER_SIZE = 4096u
private const val BYTE_MASK = 0xFF

/**
 * iOS [AudioRecorder] implementation using [AVAudioEngine].
 */
@OptIn(ExperimentalForeignApi::class)
internal class PlatformAudioRecorder : AudioRecorder {

    private val _isRecording = MutableStateFlow(false)
    override val isRecording: StateFlow<Boolean> = _isRecording.asStateFlow()

    private val _audioChunks = MutableSharedFlow<ByteArray>(extraBufferCapacity = 16)
    override val audioChunks: Flow<ByteArray> = _audioChunks.asSharedFlow()

    private var audioEngine: AVAudioEngine? = null
    private var recordedData = NSMutableData()
    private var currentFormat = AudioFormat()

    /** Guards [recordedData] against concurrent access from the AVAudioEngine callback thread. */
    private val dataLock = NSRecursiveLock()

    override suspend fun start(format: AudioFormat) {
        if (_isRecording.value) return
        currentFormat = format

        val session = AVAudioSession.sharedInstance()
        session.setCategory(AVAudioSessionCategoryRecord, null)

        memScoped {
            val sessionErr = alloc<ObjCObjectVar<NSError?>>()
            val sessionOk = session.setActive(true, sessionErr.ptr)
            if (!sessionOk) {
                val msg = sessionErr.value?.localizedDescription ?: "Failed to activate AVAudioSession"
                throw IllegalStateException(msg)
            }
        }

        val engine = AVAudioEngine()
        audioEngine = engine

        val inputNode = engine.inputNode
        val inputFormat = inputNode.outputFormatForBus(0u)

        dataLock.lock()
        try {
            recordedData = NSMutableData()
        } finally {
            dataLock.unlock()
        }

        inputNode.installTapOnBus(0u, BUFFER_SIZE, inputFormat) { buffer, _ ->
            buffer?.let { processBuffer(it) }
        }

        engine.prepare()

        memScoped {
            val engineErr = alloc<ObjCObjectVar<NSError?>>()
            val engineOk = engine.startAndReturnError(engineErr.ptr)
            if (!engineOk) {
                val msg = engineErr.value?.localizedDescription ?: "Failed to start AVAudioEngine"
                throw IllegalStateException(msg)
            }
        }

        _isRecording.value = true
    }

    override suspend fun stop(): AudioData? {
        if (!_isRecording.value) return null

        audioEngine?.inputNode?.removeTapOnBus(0u)
        audioEngine?.stop()
        _isRecording.value = false

        dataLock.lock()
        val length: Int
        val snapshot: NSMutableData
        try {
            length = recordedData.length.toInt()
            snapshot = recordedData
        } finally {
            dataLock.unlock()
        }

        if (length == 0) return null

        val bytes = ByteArray(length)
        bytes.usePinned { pinned ->
            memcpy(pinned.addressOf(0), snapshot.bytes, length.toULong())
        }

        val bytesPerSample = currentFormat.bitsPerSample / Byte.SIZE_BITS
        val totalSamples = length / bytesPerSample / currentFormat.channels
        val durationMs = totalSamples.toLong() * AudioConstants.MILLIS_PER_SECOND / currentFormat.sampleRate

        return AudioData(bytes = bytes, format = currentFormat, durationMs = durationMs)
    }

    override fun release() {
        audioEngine?.inputNode?.removeTapOnBus(0u)
        audioEngine?.stop()
        audioEngine = null
        _isRecording.value = false
    }

    private fun processBuffer(buffer: AVAudioPCMBuffer) {
        val frameLength = buffer.frameLength.toInt()
        if (frameLength == 0) return

        val int16Ptr = buffer.int16ChannelData
        if (int16Ptr != null) {
            val channelData = int16Ptr.get(0) ?: return
            val byteCount = frameLength * AudioConstants.BYTES_PER_INT16
            val bytes = ByteArray(byteCount)
            for (i in 0 until frameLength) {
                val sample = channelData.get(i)
                bytes[i * AudioConstants.BYTES_PER_INT16] = (sample.toInt() and BYTE_MASK).toByte()
                bytes[i * AudioConstants.BYTES_PER_INT16 + 1] = (sample.toInt() shr Byte.SIZE_BITS).toByte()
            }
            bytes.usePinned { pinned ->
                dataLock.lock()
                try {
                    recordedData.appendBytes(pinned.addressOf(0), byteCount.toULong())
                } finally {
                    dataLock.unlock()
                }
            }
            _audioChunks.tryEmit(bytes)
        }
    }
}
