package io.github.kmpfacelink.voice.lipsync.internal

import io.github.kmpfacelink.model.BlendShape
import io.github.kmpfacelink.voice.AudioConstants
import io.github.kmpfacelink.voice.audio.AudioData
import io.github.kmpfacelink.voice.lipsync.AudioFeatures
import io.github.kmpfacelink.voice.lipsync.LipSyncConfig
import io.github.kmpfacelink.voice.lipsync.LipSyncEngine
import io.github.kmpfacelink.voice.lipsync.LipSyncFrame
import io.github.kmpfacelink.voice.tts.PhonemeEvent
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

// Energy thresholds for mouth shape classification
private const val HIGH_ENERGY_THRESHOLD = 0.6f
private const val MEDIUM_ENERGY_THRESHOLD = 0.2f
private const val SILENCE_THRESHOLD = 0.05f

// Zero-crossing rate thresholds
private const val HIGH_ZCR_THRESHOLD = 0.6f
private const val TRANSIENT_ZCR_THRESHOLD = 0.8f

// Blend shape weight constants
private const val JAW_OPEN_MAX = 0.8f
private const val JAW_OPEN_MEDIUM = 0.4f
private const val MOUTH_STRETCH_WEIGHT = 0.4f
private const val MOUTH_FUNNEL_WEIGHT = 0.35f
private const val MOUTH_CLOSE_WEIGHT = 0.4f
private const val MOUTH_PRESS_WEIGHT = 0.5f
private const val MOUTH_LOWER_DOWN_WEIGHT = 0.25f
private const val MOUTH_SMILE_WEIGHT = 0.2f

/**
 * Enhanced amplitude-based lip sync engine.
 *
 * Unlike the basic [AmplitudeAnimator] which only maps volume to jaw open,
 * this engine uses multiple audio features (energy, zero-crossing rate, spectral centroid)
 * to drive a richer set of mouth blend shapes:
 *
 * - **High energy + high frequency**: Wide open mouth (jaw + stretch)
 * - **Medium energy**: Partial jaw open with funnel
 * - **Low energy**: Subtle lip movement (mouth close)
 * - **High transient rate**: Pressed lips (consonant-like)
 *
 * Includes temporal smoothing for natural transitions between frames.
 */
internal class EnhancedAmplitudeLipSync : LipSyncEngine {

    override fun animateFromPhonemes(
        phonemeEvents: List<PhonemeEvent>,
        config: LipSyncConfig,
    ): Flow<LipSyncFrame> = PhonemeAnimator.animate(phonemeEvents, config)

    override fun animateFromAudio(
        audio: AudioData,
        config: LipSyncConfig,
    ): Flow<LipSyncFrame> = flow {
        val features = AudioFeatureExtractor.extract(audio, config.targetFps)
        val frameDurationMs = AudioConstants.MILLIS_PER_SECOND / config.targetFps
        val smoother = TemporalSmoother(config.smoothing)

        for ((index, frame) in features.withIndex()) {
            val scaled = scaleFeatures(frame, config.amplitudeScale)
            val blendShapes = smoother.smooth(mapFeaturesToBlendShapes(scaled))

            emit(
                LipSyncFrame(
                    blendShapes = blendShapes,
                    timestampMs = index.toLong() * frameDurationMs,
                ),
            )
            delay(frameDurationMs)
        }

        // Final silence frame
        emit(LipSyncFrame(blendShapes = emptyMap(), timestampMs = audio.durationMs))
    }

    override fun release(): Unit = Unit
}

internal fun mapFeaturesToBlendShapes(features: AudioFeatures): Map<BlendShape, Float> {
    val energy = features.energy
    val zcr = features.zeroCrossingRate

    if (energy < SILENCE_THRESHOLD) return emptyMap()

    return when {
        zcr > TRANSIENT_ZCR_THRESHOLD -> buildTransientShape(energy)
        energy > HIGH_ENERGY_THRESHOLD -> buildHighEnergyShape(energy, zcr)
        energy > MEDIUM_ENERGY_THRESHOLD -> buildMediumEnergyShape(energy)
        else -> buildLowEnergyShape(energy)
    }
}

private fun buildTransientShape(energy: Float): Map<BlendShape, Float> = buildMap {
    put(BlendShape.MOUTH_PRESS_LEFT, energy * MOUTH_PRESS_WEIGHT)
    put(BlendShape.MOUTH_PRESS_RIGHT, energy * MOUTH_PRESS_WEIGHT)
    put(BlendShape.MOUTH_CLOSE, energy * MOUTH_CLOSE_WEIGHT)
}

private fun buildHighEnergyShape(
    energy: Float,
    zcr: Float,
): Map<BlendShape, Float> = buildMap {
    put(BlendShape.JAW_OPEN, energy * JAW_OPEN_MAX)
    put(BlendShape.MOUTH_LOWER_DOWN_LEFT, energy * MOUTH_LOWER_DOWN_WEIGHT)
    put(BlendShape.MOUTH_LOWER_DOWN_RIGHT, energy * MOUTH_LOWER_DOWN_WEIGHT)
    if (zcr > HIGH_ZCR_THRESHOLD) {
        put(BlendShape.MOUTH_STRETCH_LEFT, energy * MOUTH_STRETCH_WEIGHT)
        put(BlendShape.MOUTH_STRETCH_RIGHT, energy * MOUTH_STRETCH_WEIGHT)
    }
}

private fun buildMediumEnergyShape(energy: Float): Map<BlendShape, Float> = buildMap {
    put(BlendShape.JAW_OPEN, energy * JAW_OPEN_MEDIUM)
    put(BlendShape.MOUTH_FUNNEL, energy * MOUTH_FUNNEL_WEIGHT)
}

private fun buildLowEnergyShape(energy: Float): Map<BlendShape, Float> = buildMap {
    put(BlendShape.MOUTH_CLOSE, energy * MOUTH_CLOSE_WEIGHT)
    put(BlendShape.MOUTH_SMILE_LEFT, energy * MOUTH_SMILE_WEIGHT)
    put(BlendShape.MOUTH_SMILE_RIGHT, energy * MOUTH_SMILE_WEIGHT)
}

private fun scaleFeatures(features: AudioFeatures, scale: Float): AudioFeatures =
    AudioFeatures(
        energy = (features.energy * scale).coerceIn(0f, 1f),
        zeroCrossingRate = features.zeroCrossingRate,
        spectralCentroid = features.spectralCentroid,
    )
