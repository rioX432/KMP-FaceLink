package io.github.kmpfacelink.voice.asr.internal

import io.github.kmpfacelink.voice.AudioConstants
import io.github.kmpfacelink.voice.asr.AsrConfig
import io.github.kmpfacelink.voice.asr.AsrEngine
import io.github.kmpfacelink.voice.asr.AsrState
import io.github.kmpfacelink.voice.asr.TranscriptionResult
import io.github.kmpfacelink.voice.audio.AudioData
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

private const val INT16_MAX = 32767f

/**
 * On-device ASR engine using whisper.cpp via platform-specific bridge.
 *
 * Expects audio in 16kHz mono format. Internally converts Int16 PCM to Float32.
 */
internal class WhisperCppAsrEngine(private val config: AsrConfig.WhisperCpp) : AsrEngine {

    private val _state = MutableStateFlow<AsrState>(AsrState.Idle)
    override val state: StateFlow<AsrState> = _state.asStateFlow()

    private val _transcriptions = MutableSharedFlow<TranscriptionResult>(extraBufferCapacity = 16)
    override val transcriptions: Flow<TranscriptionResult> = _transcriptions.asSharedFlow()

    private val bridge = WhisperCppBridge()
    private var initialized = false

    private fun ensureInitialized() {
        if (!initialized) {
            val success = bridge.initModel(config.modelPath)
            check(success) { "Failed to load whisper model from ${config.modelPath}" }
            initialized = true
        }
    }

    override suspend fun transcribe(audio: AudioData): TranscriptionResult {
        _state.value = AsrState.Processing
        return try {
            ensureInitialized()

            // Convert Int16 PCM to Float32 samples
            val samples = convertToFloat32(audio.bytes)
            val text = bridge.transcribe(samples, config.language, config.threads)

            val result = TranscriptionResult(
                text = text.trim(),
                isFinal = true,
                language = config.language,
            )

            _state.value = AsrState.Idle
            result
        } catch (e: Exception) {
            _state.value = AsrState.Error(e.message ?: "Whisper transcription failed")
            throw e
        }
    }

    override suspend fun startListening() {
        _state.value = AsrState.Listening
    }

    override suspend fun stopListening() {
        _state.value = AsrState.Idle
    }

    override fun release() {
        bridge.release()
        initialized = false
    }

    private fun convertToFloat32(bytes: ByteArray): FloatArray {
        val sampleCount = bytes.size / AudioConstants.BYTES_PER_INT16
        val samples = FloatArray(sampleCount)
        for (i in 0 until sampleCount) {
            val lo = bytes[i * AudioConstants.BYTES_PER_INT16].toInt() and 0xFF
            val hi = bytes[i * AudioConstants.BYTES_PER_INT16 + 1].toInt()
            val int16 = (hi shl Byte.SIZE_BITS) or lo
            samples[i] = int16.toFloat() / INT16_MAX
        }
        return samples
    }
}
