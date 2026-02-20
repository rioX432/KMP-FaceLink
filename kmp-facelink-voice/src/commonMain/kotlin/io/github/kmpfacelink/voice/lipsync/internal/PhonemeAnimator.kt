package io.github.kmpfacelink.voice.lipsync.internal

import io.github.kmpfacelink.model.BlendShape
import io.github.kmpfacelink.voice.AudioConstants
import io.github.kmpfacelink.voice.lipsync.LipSyncConfig
import io.github.kmpfacelink.voice.lipsync.LipSyncFrame
import io.github.kmpfacelink.voice.lipsync.VisemeBlendShapes
import io.github.kmpfacelink.voice.tts.PhonemeEvent
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
private const val HALF = 0.5f
private const val TRANSITION_OVERLAP_MS = 60L

/**
 * Generates lip sync animation frames from phoneme timing events.
 *
 * Uses cosine interpolation between viseme keyframes for smooth transitions.
 */
internal object PhonemeAnimator {

    /**
     * Produces a [Flow] of [LipSyncFrame] by interpolating between phoneme-derived visemes.
     *
     * @param phonemeEvents Phoneme events with timing from TTS
     * @param config Animation configuration
     */
    fun animate(
        phonemeEvents: List<PhonemeEvent>,
        config: LipSyncConfig,
    ): Flow<LipSyncFrame> = flow {
        if (phonemeEvents.isEmpty()) return@flow

        val frameDurationMs = AudioConstants.MILLIS_PER_SECOND / config.targetFps
        val totalDurationMs = phonemeEvents.maxOf { it.endMs }

        // Pre-compute viseme keyframes
        val keyframes = phonemeEvents.map { event ->
            Keyframe(
                viseme = VisemeMapper.map(event.phoneme),
                startMs = event.startMs,
                endMs = event.endMs,
            )
        }

        var currentTimeMs = 0L
        while (currentTimeMs <= totalDurationMs) {
            val blendShapes = interpolateAt(currentTimeMs, keyframes, config.smoothing)
            emit(LipSyncFrame(blendShapes = blendShapes, timestampMs = currentTimeMs))
            currentTimeMs += frameDurationMs
            delay(frameDurationMs)
        }

        // Emit final silence frame
        emit(LipSyncFrame(blendShapes = emptyMap(), timestampMs = totalDurationMs))
    }

    private fun interpolateAt(
        timeMs: Long,
        keyframes: List<Keyframe>,
        smoothing: Float,
    ): Map<BlendShape, Float> {
        // Find the active keyframe (the one whose time range contains current time)
        val active = keyframes.lastOrNull { it.startMs <= timeMs && it.endMs > timeMs }
            ?: return emptyMap()

        val activeShapes = VisemeBlendShapes.blendShapesFor(active.viseme)

        // Find the next keyframe for transition blending
        val next = keyframes.firstOrNull { it.startMs > active.startMs && it.startMs <= timeMs + TRANSITION_OVERLAP_MS }

        if (next == null) return activeShapes

        // Cosine interpolation in the transition overlap zone
        val overlapStart = max(active.endMs - TRANSITION_OVERLAP_MS, active.startMs)
        val overlapEnd = min(active.endMs, next.startMs + TRANSITION_OVERLAP_MS)
        if (timeMs < overlapStart || overlapEnd <= overlapStart) return activeShapes

        val t = (timeMs - overlapStart).toFloat() / (overlapEnd - overlapStart).toFloat()
        val cosT = (1f - cos(t * PI).toFloat()) * HALF * (1f - smoothing) + t * smoothing

        val nextShapes = VisemeBlendShapes.blendShapesFor(next.viseme)
        return lerpBlendShapes(activeShapes, nextShapes, cosT)
    }

    private fun lerpBlendShapes(
        from: Map<BlendShape, Float>,
        to: Map<BlendShape, Float>,
        t: Float,
    ): Map<BlendShape, Float> {
        val allKeys = from.keys + to.keys
        return allKeys.associateWith { key ->
            val a = from[key] ?: 0f
            val b = to[key] ?: 0f
            a + (b - a) * t
        }
    }

    private data class Keyframe(
        val viseme: io.github.kmpfacelink.voice.lipsync.Viseme,
        val startMs: Long,
        val endMs: Long,
    )
}
