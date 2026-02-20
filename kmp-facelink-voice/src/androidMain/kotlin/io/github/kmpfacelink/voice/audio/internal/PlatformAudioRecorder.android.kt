package io.github.kmpfacelink.voice.audio.internal

import android.annotation.SuppressLint
import android.media.AudioRecord
import android.media.MediaRecorder
import io.github.kmpfacelink.voice.AudioConstants
import io.github.kmpfacelink.voice.audio.AudioData
import io.github.kmpfacelink.voice.audio.AudioFormat
import io.github.kmpfacelink.voice.audio.AudioRecorder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream

private const val BUFFER_SIZE_FACTOR = 2

/**
 * Android [AudioRecorder] implementation using [AudioRecord].
 *
 * Captures 16kHz mono PCM audio from the microphone.
 */
internal class PlatformAudioRecorder : AudioRecorder {

    private val _isRecording = MutableStateFlow(false)
    override val isRecording: StateFlow<Boolean> = _isRecording.asStateFlow()

    private val _audioChunks = MutableSharedFlow<ByteArray>(extraBufferCapacity = 16)
    override val audioChunks: Flow<ByteArray> = _audioChunks.asSharedFlow()

    private var audioRecord: AudioRecord? = null
    private var recordingJob: Job? = null
    private var recordedData = ByteArrayOutputStream()
    private var currentFormat = AudioFormat()

    @SuppressLint("MissingPermission")
    override suspend fun start(format: AudioFormat) {
        if (_isRecording.value) return

        currentFormat = format
        recordedData.reset()

        val channelConfig = if (format.channels == 1) {
            android.media.AudioFormat.CHANNEL_IN_MONO
        } else {
            android.media.AudioFormat.CHANNEL_IN_STEREO
        }

        val audioEncoding = when (format.bitsPerSample) {
            Byte.SIZE_BITS -> android.media.AudioFormat.ENCODING_PCM_8BIT
            else -> android.media.AudioFormat.ENCODING_PCM_16BIT
        }

        val minBufferSize = AudioRecord.getMinBufferSize(
            format.sampleRate,
            channelConfig,
            audioEncoding,
        )

        audioRecord = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            format.sampleRate,
            channelConfig,
            audioEncoding,
            minBufferSize * BUFFER_SIZE_FACTOR,
        )

        audioRecord?.startRecording()
        _isRecording.value = true

        coroutineScope {
            recordingJob = launch {
                withContext(Dispatchers.IO) {
                    val buffer = ByteArray(minBufferSize)
                    while (isActive && _isRecording.value) {
                        val bytesRead = audioRecord?.read(buffer, 0, buffer.size) ?: -1
                        if (bytesRead > 0) {
                            val chunk = buffer.copyOf(bytesRead)
                            recordedData.write(chunk)
                            _audioChunks.tryEmit(chunk)
                        }
                    }
                }
            }
        }
    }

    override suspend fun stop(): AudioData? {
        if (!_isRecording.value) return null

        _isRecording.value = false
        recordingJob?.cancel()
        recordingJob = null

        audioRecord?.stop()

        val bytes = recordedData.toByteArray()
        if (bytes.isEmpty()) return null

        val bytesPerSample = currentFormat.bitsPerSample / Byte.SIZE_BITS
        val totalSamples = bytes.size / bytesPerSample / currentFormat.channels
        val durationMs = totalSamples.toLong() * AudioConstants.MILLIS_PER_SECOND / currentFormat.sampleRate

        return AudioData(bytes = bytes, format = currentFormat, durationMs = durationMs)
    }

    override fun release() {
        _isRecording.value = false
        recordingJob?.cancel()
        recordingJob = null
        audioRecord?.release()
        audioRecord = null
    }
}
