package io.github.kmpfacelink.pipeline

import io.github.kmpfacelink.llm.ConversationHistory
import io.github.kmpfacelink.llm.LlmClient
import io.github.kmpfacelink.pipeline.expression.DefaultExpressionMapper
import io.github.kmpfacelink.pipeline.expression.ExpressionBlender
import io.github.kmpfacelink.pipeline.expression.TextSentimentAnalyzer
import io.github.kmpfacelink.pipeline.internal.StreamingCoordinator
import io.github.kmpfacelink.pipeline.internal.TextOnlyProcessor
import io.github.kmpfacelink.voice.asr.AsrEngine
import io.github.kmpfacelink.voice.lipsync.LipSyncEngine
import io.github.kmpfacelink.voice.tts.TtsEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

private const val OUTPUT_BUFFER_CAPACITY = 64

/**
 * Default implementation of [AvatarPipeline].
 *
 * Orchestrates ASR -> LLM -> TTS -> lip sync -> avatar output, with graceful
 * degradation when TTS or lip sync engines are not provided (text-only mode).
 *
 * @param config Pipeline configuration including LLM and persona settings
 * @param llmClient LLM streaming client for text generation
 * @param ttsEngine Optional TTS engine; if null, text-only output is produced
 * @param lipSyncEngine Optional lip sync engine; if null, text-only output is produced
 * @param asrEngine Optional ASR engine; if null, [listen] throws [IllegalStateException]
 */
public class DefaultAvatarPipeline(
    private val config: AvatarPipelineConfig,
    private val llmClient: LlmClient,
    private val ttsEngine: TtsEngine? = null,
    private val lipSyncEngine: LipSyncEngine? = null,
    private val asrEngine: AsrEngine? = null,
) : AvatarPipeline {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val _state = MutableStateFlow<AvatarPipelineState>(AvatarPipelineState.Idle)
    private val _output = MutableSharedFlow<AvatarPipelineOutput>(extraBufferCapacity = OUTPUT_BUFFER_CAPACITY)

    private val conversationHistory = ConversationHistory(persona = config.persona)
    private var currentJob: Job? = null

    override val state: StateFlow<AvatarPipelineState> = _state.asStateFlow()
    override val output: Flow<AvatarPipelineOutput> = _output.asSharedFlow()

    override suspend fun start() {
        _state.value = AvatarPipelineState.Idle
    }

    override suspend fun stop() {
        currentJob?.cancel()
        currentJob = null
        _state.value = AvatarPipelineState.Idle
    }

    override suspend fun listen() {
        val asr = asrEngine ?: error("ASR not configured")
        cancelCurrentJob()
        _state.value = AvatarPipelineState.Listening

        currentJob = scope.launch {
            try {
                collectAsrAndProcess(asr)
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                // Catch all non-cancellation exceptions for graceful error reporting
                _state.value = AvatarPipelineState.Error(
                    message = "ASR failed: ${e.message}",
                    cause = e,
                )
            }
        }
    }

    override suspend fun processText(text: String) {
        cancelCurrentJob()
        _state.value = AvatarPipelineState.Processing

        currentJob = scope.launch {
            try {
                executeTextProcessing(text)
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                // Catch all non-cancellation exceptions for graceful error reporting
                _state.value = AvatarPipelineState.Error(
                    message = "LLM failed: ${e.message}",
                    cause = e,
                )
            }
        }
    }

    override fun release() {
        currentJob?.cancel()
        currentJob = null
        scope.cancel()
        runCatching { llmClient.release() }
        runCatching { ttsEngine?.release() }
        runCatching { asrEngine?.release() }
    }

    private fun cancelCurrentJob() {
        currentJob?.cancel()
        currentJob = null
    }

    private suspend fun collectAsrAndProcess(asr: AsrEngine) {
        asr.startListening()
        asr.transcriptions.collect { result ->
            if (result.isFinal && result.text.isNotBlank()) {
                asr.stopListening()
                _state.value = AvatarPipelineState.Processing
                executeTextProcessing(result.text)
                return@collect
            }
        }
    }

    private suspend fun executeTextProcessing(text: String) {
        conversationHistory.addUser(text)
        val llmTokens = llmClient.chat(conversationHistory)
        val responseBuilder = StringBuilder()

        val outputFlow = buildOutputFlow(llmTokens)
        _state.value = AvatarPipelineState.Speaking
        outputFlow.collect { frame ->
            if (frame.text != null) {
                responseBuilder.append(frame.text)
            }
            _output.emit(frame)
        }

        conversationHistory.addAssistant(responseBuilder.toString())
        _state.value = AvatarPipelineState.Idle
    }

    private fun buildOutputFlow(llmTokens: Flow<String>): Flow<AvatarPipelineOutput> {
        if (ttsEngine != null && lipSyncEngine != null) {
            return createStreamingCoordinator().processLlmStream(llmTokens)
        }
        return TextOnlyProcessor.process(llmTokens)
    }

    private fun createStreamingCoordinator(): StreamingCoordinator =
        StreamingCoordinator(
            ttsEngine = ttsEngine!!,
            lipSyncEngine = lipSyncEngine!!,
            sentimentAnalyzer = TextSentimentAnalyzer(),
            expressionMapper = DefaultExpressionMapper(),
            expressionBlender = ExpressionBlender(emotionWeight = config.expressionBlendRatio),
        )
}
