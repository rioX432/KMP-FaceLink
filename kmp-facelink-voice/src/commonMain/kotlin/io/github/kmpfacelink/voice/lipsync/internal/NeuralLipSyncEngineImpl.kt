package io.github.kmpfacelink.voice.lipsync.internal

import io.github.kmpfacelink.voice.AudioConstants
import io.github.kmpfacelink.voice.audio.AudioData
import io.github.kmpfacelink.voice.lipsync.LipSyncConfig
import io.github.kmpfacelink.voice.lipsync.LipSyncFrame
import io.github.kmpfacelink.voice.lipsync.NeuralLipSyncConfig
import io.github.kmpfacelink.voice.lipsync.NeuralLipSyncEngine
import io.github.kmpfacelink.voice.lipsync.NeuralModelBridge
import io.github.kmpfacelink.voice.tts.PhonemeEvent
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

private const val BITS_PER_BYTE = 8
private const val BYTE_MASK = 0xFF
private const val BYTES_PER_SAMPLE_16BIT = 2

/**
 * Neural lip sync engine implementation.
 *
 * Uses a platform-specific [NeuralModelBridge] for ML inference and
 * falls back to [EnhancedAmplitudeLipSync] when the model is unavailable.
 *
 * The inference pipeline:
 * 1. Decode PCM audio to float samples
 * 2. Extract mel spectrogram features via [SpectrogramExtractor]
 * 3. Run per-frame inference through [NeuralModelBridge]
 * 4. Map model output to ARKit blend shapes via [BlendShapeOutputMap]
 * 5. Apply temporal smoothing for natural transitions
 *
 * @param modelBridge Platform-specific model inference bridge
 * @param neuralConfig Configuration for the neural model
 */
internal class NeuralLipSyncEngineImpl(
    private val modelBridge: NeuralModelBridge,
    private val neuralConfig: NeuralLipSyncConfig,
) : NeuralLipSyncEngine {

    private val fallback = EnhancedAmplitudeLipSync()

    override val isModelLoaded: Boolean
        get() = modelBridge.isLoaded

    override suspend fun loadModel(modelPath: String) {
        val config = neuralConfig.copy(modelPath = modelPath)
        modelBridge.load(config)
    }

    override fun unloadModel() {
        modelBridge.release()
    }

    override fun predictFromAudio(
        audio: AudioData,
        config: LipSyncConfig,
    ): Flow<LipSyncFrame> {
        if (!modelBridge.isLoaded) {
            return fallback.animateFromAudio(audio, config)
        }
        return neuralInferenceFlow(audio, config)
    }

    override fun animateFromPhonemes(
        phonemeEvents: List<PhonemeEvent>,
        config: LipSyncConfig,
    ): Flow<LipSyncFrame> = PhonemeAnimator.animate(phonemeEvents, config)

    override fun animateFromAudio(
        audio: AudioData,
        config: LipSyncConfig,
    ): Flow<LipSyncFrame> = predictFromAudio(audio, config)

    override fun release() {
        modelBridge.release()
        fallback.release()
    }

    private fun neuralInferenceFlow(
        audio: AudioData,
        config: LipSyncConfig,
    ): Flow<LipSyncFrame> = flow {
        val samples = decodePcmSamples(audio)
        val hopSize = audio.format.sampleRate / config.targetFps
        val melFrames = SpectrogramExtractor.extract(
            samples = samples,
            sampleRate = audio.format.sampleRate,
            hopSize = hopSize,
            numMelBins = neuralConfig.outputBlendShapeCount,
        )

        if (melFrames.isEmpty()) return@flow

        val frameDurationMs = AudioConstants.MILLIS_PER_SECOND / config.targetFps
        val smoother = TemporalSmoother(config.smoothing)

        emitInferenceFrames(melFrames, smoother, frameDurationMs)

        // Final silence frame
        emit(LipSyncFrame(blendShapes = emptyMap(), timestampMs = audio.durationMs))
    }

    private suspend fun kotlinx.coroutines.flow.FlowCollector<LipSyncFrame>.emitInferenceFrames(
        melFrames: List<FloatArray>,
        smoother: TemporalSmoother,
        frameDurationMs: Long,
    ) {
        for ((index, melFrame) in melFrames.withIndex()) {
            val rawOutput = modelBridge.infer(melFrame)
            val blendShapes = BlendShapeOutputMap.toBlendShapeMap(rawOutput)
            val smoothed = smoother.smooth(blendShapes)

            emit(
                LipSyncFrame(
                    blendShapes = smoothed,
                    timestampMs = index.toLong() * frameDurationMs,
                ),
            )
            delay(frameDurationMs)
        }
    }

    private fun decodePcmSamples(audio: AudioData): FloatArray {
        val bytesPerSample = audio.format.bitsPerSample / BITS_PER_BYTE
        val totalSamples = audio.bytes.size / bytesPerSample
        val samples = FloatArray(totalSamples)

        for (i in 0 until totalSamples) {
            samples[i] = decodeSample(audio.bytes, i, bytesPerSample)
        }
        return samples
    }

    private fun decodeSample(bytes: ByteArray, index: Int, bytesPerSample: Int): Float {
        if (bytesPerSample == BYTES_PER_SAMPLE_16BIT) {
            val offset = index * BYTES_PER_SAMPLE_16BIT
            if (offset + 1 >= bytes.size) return 0f
            val lo = bytes[offset].toInt() and BYTE_MASK
            val hi = bytes[offset + 1].toInt()
            val int16 = (hi shl BITS_PER_BYTE) or lo
            return int16.toFloat() / Short.MAX_VALUE
        }
        return if (index < bytes.size) bytes[index].toFloat() / Byte.MAX_VALUE else 0f
    }
}
