package io.github.kmpfacelink.voice.lipsync.internal

import io.github.kmpfacelink.model.BlendShape
import io.github.kmpfacelink.voice.audio.AudioData
import io.github.kmpfacelink.voice.lipsync.LipSyncConfig
import io.github.kmpfacelink.voice.lipsync.LipSyncFrame
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

private const val JAW_OPEN_WEIGHT = 0.7f
private const val MOUTH_FUNNEL_WEIGHT = 0.3f
private const val MILLIS_PER_SECOND = 1000L

/**
 * Generates lip sync frames from audio amplitude.
 *
 * Maps volume → jawOpen + mouthFunnel for a simple but effective mouth animation.
 * Used as fallback when TTS doesn't provide phoneme timing (e.g., OpenAI TTS).
 */
internal object AmplitudeAnimator {

    /**
     * Produces lip sync frames based on amplitude envelope.
     *
     * @param audio Audio data to analyze
     * @param config Lip sync configuration
     */
    fun animate(audio: AudioData, config: LipSyncConfig): Flow<LipSyncFrame> = flow {
        val amplitudes = AmplitudeAnalyzer.analyze(audio, config.targetFps)
        val frameDurationMs = MILLIS_PER_SECOND / config.targetFps

        var prevJaw = 0f
        var prevFunnel = 0f

        for ((index, amplitude) in amplitudes.withIndex()) {
            val scaled = (amplitude * config.amplitudeScale).coerceIn(0f, 1f)

            val targetJaw = scaled * JAW_OPEN_WEIGHT
            val targetFunnel = scaled * MOUTH_FUNNEL_WEIGHT

            // Smooth with exponential moving average
            val alpha = 1f - config.smoothing
            val jaw = prevJaw + alpha * (targetJaw - prevJaw)
            val funnel = prevFunnel + alpha * (targetFunnel - prevFunnel)

            prevJaw = jaw
            prevFunnel = funnel

            val blendShapes = buildMap<BlendShape, Float> {
                if (jaw > 0.01f) put(BlendShape.JAW_OPEN, jaw)
                if (funnel > 0.01f) put(BlendShape.MOUTH_FUNNEL, funnel)
            }

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
}
