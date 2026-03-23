package io.github.kmpfacelink.pipeline.internal

import io.github.kmpfacelink.pipeline.AvatarPipelineOutput
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlin.time.TimeSource

/**
 * Produces text-only [AvatarPipelineOutput] frames when TTS/LipSync are unavailable.
 *
 * Each LLM token is emitted as an output frame with empty blend shapes and no audio.
 */
internal object TextOnlyProcessor {

    /**
     * Transforms an LLM token stream into text-only avatar output frames.
     *
     * @param llmTokens Flow of text chunks from the LLM
     * @return Flow of avatar output frames with text only
     */
    fun process(llmTokens: Flow<String>): Flow<AvatarPipelineOutput> = flow {
        val startMark = TimeSource.Monotonic.markNow()
        llmTokens.collect { token ->
            emit(
                AvatarPipelineOutput(
                    blendShapes = emptyMap(),
                    emotion = null,
                    text = token,
                    isAudioPlaying = false,
                    timestampMs = startMark.elapsedNow().inWholeMilliseconds,
                ),
            )
        }
    }
}
