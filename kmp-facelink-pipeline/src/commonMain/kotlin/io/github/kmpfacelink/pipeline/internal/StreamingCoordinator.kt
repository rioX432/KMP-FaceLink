package io.github.kmpfacelink.pipeline.internal

import io.github.kmpfacelink.pipeline.AvatarPipelineOutput
import io.github.kmpfacelink.pipeline.expression.ExpressionBlender
import io.github.kmpfacelink.pipeline.expression.ExpressionMapper
import io.github.kmpfacelink.pipeline.expression.TextSentimentAnalyzer
import io.github.kmpfacelink.voice.lipsync.LipSyncEngine
import io.github.kmpfacelink.voice.tts.TtsEngine
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * Coordinates the streaming pipeline: LLM tokens -> sentence buffering -> TTS -> LipSync.
 *
 * Processes sentences sequentially, allowing TTS for sentence N
 * while the LLM generates tokens for sentence N+1.
 */
internal class StreamingCoordinator(
    private val ttsEngine: TtsEngine,
    private val lipSyncEngine: LipSyncEngine,
    private val sentimentAnalyzer: TextSentimentAnalyzer,
    private val expressionMapper: ExpressionMapper,
    private val expressionBlender: ExpressionBlender,
) {

    /**
     * Processes an LLM token stream into avatar pipeline output frames.
     *
     * Tokens are buffered into sentences, then each sentence is processed through
     * sentiment analysis, TTS synthesis, and lip sync animation.
     *
     * @param llmTokens Flow of text chunks from the LLM
     * @return Flow of avatar output frames with blended blend shapes
     */
    fun processLlmStream(
        llmTokens: Flow<String>,
    ): Flow<AvatarPipelineOutput> = flow {
        val buffer = SentenceBuffer()

        llmTokens.collect { token ->
            currentCoroutineContext().ensureActive()
            val sentences = buffer.append(token)
            for (sentence in sentences) {
                emitSentenceFrames(sentence)
            }
        }

        // Flush remaining text after LLM stream completes
        val remaining = buffer.flush()
        if (remaining != null) {
            emitSentenceFrames(remaining)
        }
    }

    private suspend fun kotlinx.coroutines.flow.FlowCollector<AvatarPipelineOutput>.emitSentenceFrames(
        sentence: String,
    ) {
        val sentiment = sentimentAnalyzer.analyze(sentence)
        val emotionShapes = expressionMapper.map(sentiment.emotion, sentiment.intensity)
        val ttsResult = ttsEngine.synthesize(sentence)

        val lipSyncFrames = if (ttsResult.phonemeEvents.isNotEmpty()) {
            lipSyncEngine.animateFromPhonemes(ttsResult.phonemeEvents)
        } else {
            lipSyncEngine.animateFromAudio(ttsResult.audio)
        }

        lipSyncFrames.collect { frame ->
            currentCoroutineContext().ensureActive()
            val blended = expressionBlender.blend(
                lipSync = frame.blendShapes,
                emotion = emotionShapes,
            )
            emit(
                AvatarPipelineOutput(
                    blendShapes = blended,
                    emotion = sentiment.emotion,
                    text = sentence,
                    isAudioPlaying = true,
                    timestampMs = frame.timestampMs,
                ),
            )
        }
    }
}
