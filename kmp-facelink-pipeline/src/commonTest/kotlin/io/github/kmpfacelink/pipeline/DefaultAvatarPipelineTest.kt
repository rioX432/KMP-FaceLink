package io.github.kmpfacelink.pipeline

import io.github.kmpfacelink.llm.ConversationHistory
import io.github.kmpfacelink.llm.LlmClient
import io.github.kmpfacelink.llm.LlmConfig
import io.github.kmpfacelink.llm.LlmState
import io.github.kmpfacelink.llm.PersonaConfig
import io.github.kmpfacelink.model.BlendShape
import io.github.kmpfacelink.voice.asr.AsrEngine
import io.github.kmpfacelink.voice.asr.AsrState
import io.github.kmpfacelink.voice.asr.TranscriptionResult
import io.github.kmpfacelink.voice.audio.AudioData
import io.github.kmpfacelink.voice.audio.AudioFormat
import io.github.kmpfacelink.voice.lipsync.LipSyncConfig
import io.github.kmpfacelink.voice.lipsync.LipSyncEngine
import io.github.kmpfacelink.voice.lipsync.LipSyncFrame
import io.github.kmpfacelink.voice.tts.PhonemeEvent
import io.github.kmpfacelink.voice.tts.TtsEngine
import io.github.kmpfacelink.voice.tts.TtsResult
import io.github.kmpfacelink.voice.tts.TtsState
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

private const val FAKE_DURATION_MS = 100L
private const val JAW_OPEN_VALUE = 0.5f
private const val STATE_SETTLE_DELAY = 50L

class DefaultAvatarPipelineTest {

    private val testConfig = AvatarPipelineConfig(
        llmConfig = LlmConfig.OpenAi(apiKey = "test-key"),
        persona = PersonaConfig(systemPrompt = "You are a test assistant."),
    )

    // region Mock factories

    private fun createMockLlmClient(
        responseTokens: List<String> = listOf("Hello", " world."),
        shouldFail: Boolean = false,
    ): FakeLlmClient = FakeLlmClient(responseTokens, shouldFail)

    private fun createPipeline(
        llmClient: LlmClient = createMockLlmClient(),
        ttsEngine: TtsEngine? = null,
        lipSyncEngine: LipSyncEngine? = null,
        asrEngine: AsrEngine? = null,
    ): DefaultAvatarPipeline = DefaultAvatarPipeline(
        config = testConfig,
        llmClient = llmClient,
        ttsEngine = ttsEngine,
        lipSyncEngine = lipSyncEngine,
        asrEngine = asrEngine,
        dispatcher = UnconfinedTestDispatcher(),
    )

    // endregion

    @Test
    fun startSetsIdleState() = runTest {
        val pipeline = createPipeline()
        pipeline.start()
        assertEquals(AvatarPipelineState.Idle, pipeline.state.value)
        pipeline.release()
    }

    @Test
    fun processTextTransitionsThroughStates() = runTest {
        val states = mutableListOf<AvatarPipelineState>()
        val pipeline = createPipeline()
        pipeline.start()

        val collectJob = launch { pipeline.state.toList(states) }
        pipeline.processText("Hello")
        advanceUntilIdle()
        delay(STATE_SETTLE_DELAY)

        collectJob.cancel()
        pipeline.release()

        assertTrue(states.any { it is AvatarPipelineState.Processing })
    }

    @Test
    fun processTextEmitsTextOnlyOutputWithoutTts() = runTest {
        val pipeline = createPipeline()
        pipeline.start()

        val outputs = mutableListOf<AvatarPipelineOutput>()
        val collectJob = launch { pipeline.output.toList(outputs) }

        pipeline.processText("Test input")
        advanceUntilIdle()
        delay(STATE_SETTLE_DELAY)

        collectJob.cancel()
        pipeline.release()

        assertTrue(outputs.isNotEmpty(), "Should emit text-only output")
        assertTrue(outputs.all { !it.isAudioPlaying })
        assertTrue(outputs.all { it.blendShapes.isEmpty() })
    }

    @Test
    fun processTextWithTtsEmitsBlendShapes() = runTest {
        val pipeline = createPipeline(
            ttsEngine = FakeTtsEngine(),
            lipSyncEngine = FakeLipSyncEngine(),
        )
        pipeline.start()

        val outputs = mutableListOf<AvatarPipelineOutput>()
        val collectJob = launch { pipeline.output.toList(outputs) }

        pipeline.processText("Hello")
        advanceUntilIdle()
        delay(STATE_SETTLE_DELAY)

        collectJob.cancel()
        pipeline.release()

        assertTrue(outputs.isNotEmpty(), "Should emit output with blend shapes")
        assertTrue(outputs.any { it.blendShapes.isNotEmpty() })
        assertTrue(outputs.any { it.isAudioPlaying })
    }

    @Test
    fun llmErrorTransitionsToErrorState() = runTest {
        val pipeline = createPipeline(
            llmClient = createMockLlmClient(shouldFail = true),
        )
        pipeline.start()

        pipeline.processText("Will fail")
        advanceUntilIdle()
        delay(STATE_SETTLE_DELAY)

        assertIs<AvatarPipelineState.Error>(pipeline.state.value)
        pipeline.release()
    }

    @Test
    fun listenWithoutAsrThrowsError() = runTest {
        val pipeline = createPipeline(asrEngine = null)
        pipeline.start()

        var threw = false
        try {
            pipeline.listen()
        } catch (_: IllegalStateException) {
            threw = true
        }
        assertTrue(threw, "listen() should throw when ASR is not configured")
        pipeline.release()
    }

    @Test
    fun listenSetsListeningState() = runTest {
        val fakeAsr = FakeAsrEngine()
        val pipeline = createPipeline(asrEngine = fakeAsr)
        pipeline.start()

        pipeline.listen()
        advanceUntilIdle()
        delay(STATE_SETTLE_DELAY)

        assertEquals(AvatarPipelineState.Listening, pipeline.state.value)
        pipeline.release()
    }

    @Test
    fun listenProcessesTranscription() = runTest {
        val fakeAsr = FakeAsrEngine()
        val pipeline = createPipeline(asrEngine = fakeAsr)
        pipeline.start()

        val outputs = mutableListOf<AvatarPipelineOutput>()
        val collectJob = launch { pipeline.output.toList(outputs) }

        pipeline.listen()
        advanceUntilIdle()
        delay(STATE_SETTLE_DELAY)

        // Simulate ASR emitting a final transcription
        fakeAsr.emitTranscription(TranscriptionResult(text = "Hello world", isFinal = true))
        advanceUntilIdle()
        delay(STATE_SETTLE_DELAY)

        collectJob.cancel()
        pipeline.release()

        assertTrue(outputs.isNotEmpty(), "Should produce output after ASR transcription")
    }

    @Test
    fun stopCancelsCurrentOperation() = runTest {
        val pipeline = createPipeline(
            llmClient = FakeLlmClient(
                responseTokens = listOf("token"),
                shouldFail = false,
                delayMs = STATE_SETTLE_DELAY,
            ),
        )
        pipeline.start()

        pipeline.processText("Test")
        advanceUntilIdle()

        pipeline.stop()
        advanceUntilIdle()

        assertEquals(AvatarPipelineState.Idle, pipeline.state.value)
        pipeline.release()
    }

    @Test
    fun conversationHistoryMaintainedAcrossTurns() = runTest {
        val llmClient = createMockLlmClient()
        val pipeline = createPipeline(llmClient = llmClient)
        pipeline.start()

        pipeline.processText("First message")
        advanceUntilIdle()
        delay(STATE_SETTLE_DELAY)

        pipeline.processText("Second message")
        advanceUntilIdle()
        delay(STATE_SETTLE_DELAY)

        // Verify two chat calls were made with growing history
        assertEquals(2, llmClient.chatCallCount)
        pipeline.release()
    }

    @Test
    fun releaseCleansUp() = runTest {
        val llmClient = createMockLlmClient()
        val pipeline = createPipeline(llmClient = llmClient)
        pipeline.start()
        pipeline.release()

        assertTrue(llmClient.released, "LLM client should be released")
    }
}

// region Fakes

private class FakeLlmClient(
    private val responseTokens: List<String> = listOf("Hello", " world."),
    private val shouldFail: Boolean = false,
    private val delayMs: Long = 0L,
) : LlmClient {
    private val _state = MutableStateFlow<LlmState>(LlmState.Idle)
    override val state: StateFlow<LlmState> = _state.asStateFlow()

    var chatCallCount = 0
        private set
    var released = false
        private set

    override fun chat(history: ConversationHistory): Flow<String> {
        chatCallCount++
        if (shouldFail) {
            return flow { throw IllegalStateException("LLM error") }
        }
        return flow {
            for (token in responseTokens) {
                if (delayMs > 0) delay(delayMs)
                emit(token)
            }
        }
    }

    override suspend fun chatOnce(history: ConversationHistory): String {
        return responseTokens.joinToString("")
    }

    override fun release() {
        released = true
    }
}

private class FakeTtsEngine : TtsEngine {
    private val _state = MutableStateFlow<TtsState>(TtsState.Idle)
    override val state: StateFlow<TtsState> = _state.asStateFlow()

    override suspend fun synthesize(text: String): TtsResult = TtsResult(
        audio = AudioData(ByteArray(0), AudioFormat(), FAKE_DURATION_MS),
        phonemeEvents = listOf(PhonemeEvent("a", 0, FAKE_DURATION_MS)),
        durationMs = FAKE_DURATION_MS,
    )

    override fun release() = Unit
}

private class FakeLipSyncEngine : LipSyncEngine {
    override fun animateFromPhonemes(
        phonemeEvents: List<PhonemeEvent>,
        config: LipSyncConfig,
    ): Flow<LipSyncFrame> = flowOf(
        LipSyncFrame(mapOf(BlendShape.JAW_OPEN to JAW_OPEN_VALUE), 0L),
    )

    override fun animateFromAudio(
        audio: AudioData,
        config: LipSyncConfig,
    ): Flow<LipSyncFrame> = flowOf(
        LipSyncFrame(mapOf(BlendShape.JAW_OPEN to JAW_OPEN_VALUE), 0L),
    )
}

private class FakeAsrEngine : AsrEngine {
    private val _state = MutableStateFlow<AsrState>(AsrState.Idle)
    override val state: StateFlow<AsrState> = _state.asStateFlow()

    private val _transcriptions = MutableSharedFlow<TranscriptionResult>(extraBufferCapacity = 16)
    override val transcriptions: Flow<TranscriptionResult> = _transcriptions

    var listening = false
        private set

    override suspend fun transcribe(audio: AudioData): TranscriptionResult =
        TranscriptionResult(text = "", isFinal = true)

    override suspend fun startListening() {
        listening = true
        _state.value = AsrState.Listening
    }

    override suspend fun stopListening() {
        listening = false
        _state.value = AsrState.Idle
    }

    fun emitTranscription(result: TranscriptionResult) {
        _transcriptions.tryEmit(result)
    }

    override fun release() = Unit
}

// endregion
