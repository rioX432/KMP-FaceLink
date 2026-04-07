package io.github.kmpfacelink.pipeline.integration

import io.github.kmpfacelink.llm.LlmConfig
import io.github.kmpfacelink.llm.PersonaConfig
import io.github.kmpfacelink.pipeline.AvatarPipelineConfig
import io.github.kmpfacelink.pipeline.AvatarPipelineState
import io.github.kmpfacelink.pipeline.DefaultAvatarPipeline
import io.github.kmpfacelink.voice.asr.AsrEngine
import io.github.kmpfacelink.voice.asr.AsrState
import io.github.kmpfacelink.voice.asr.TranscriptionResult
import io.github.kmpfacelink.voice.audio.AudioData
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

private const val STATE_SETTLE_DELAY = 50L
private const val SLOW_TOKEN_DELAY_MS = 100L
private const val EXTRA_BUFFER_CAPACITY = 16

class PipelineStateTest {

    private val testConfig = AvatarPipelineConfig(
        llmConfig = LlmConfig.OpenAi(apiKey = "test-key"),
        persona = PersonaConfig(systemPrompt = "Test."),
    )

    @Test
    fun idleToListeningWhenAsrAvailable() = runTest {
        val asr = FakeAsrEngine()
        val pipeline = DefaultAvatarPipeline(
            config = testConfig,
            llmClient = DelayedMockLlmClient(),
            asrEngine = asr,
            scope = CoroutineScope(
                SupervisorJob() + UnconfinedTestDispatcher(testScheduler),
            ),
        )
        pipeline.start()
        assertEquals(AvatarPipelineState.Idle, pipeline.state.value)

        pipeline.listen()
        advanceUntilIdle()
        delay(STATE_SETTLE_DELAY)

        assertEquals(AvatarPipelineState.Listening, pipeline.state.value)
        pipeline.release()
    }

    @Test
    fun idleToProcessingToSpeakingToIdle() = runTest {
        // Use a small lip-sync delay so the Speaking state persists long enough
        // for StateFlow to emit it before being conflated with Idle.
        // On K/N, zero-delay transitions are conflated by StateFlow.
        val tts = DelayedMockTtsEngine()
        val lipSync = DelayedMockLipSyncEngine(
            framesPerSentence = 2,
            delayPerFrameMs = 1L,
        )
        val pipeline = DefaultAvatarPipeline(
            config = testConfig,
            llmClient = DelayedMockLlmClient(tokens = listOf("OK.")),
            ttsEngine = tts,
            lipSyncEngine = lipSync,
            scope = CoroutineScope(
                SupervisorJob() + UnconfinedTestDispatcher(testScheduler),
            ),
        )
        pipeline.start()

        val states = mutableListOf<AvatarPipelineState>()
        val collectJob = launch(UnconfinedTestDispatcher(testScheduler)) {
            pipeline.state.collect { states.add(it) }
        }
        yield()

        pipeline.processText("Hello")
        advanceUntilIdle()
        delay(STATE_SETTLE_DELAY)

        collectJob.cancel()
        pipeline.release()

        assertTrue(
            states.any { it is AvatarPipelineState.Processing },
            "Should transition through Processing, got: $states",
        )
        assertTrue(
            states.any { it is AvatarPipelineState.Speaking },
            "Should transition through Speaking, got: $states",
        )
        assertEquals(
            AvatarPipelineState.Idle,
            states.last(),
            "Should return to Idle, got: $states",
        )
    }

    @Test
    fun anyStateToIdleOnStop() = runTest {
        val pipeline = DefaultAvatarPipeline(
            config = testConfig,
            llmClient = DelayedMockLlmClient(
                tokens = listOf("Slow", " response."),
                delayPerTokenMs = SLOW_TOKEN_DELAY_MS,
            ),
            scope = CoroutineScope(
                SupervisorJob() + UnconfinedTestDispatcher(testScheduler),
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
    fun errorStateRecoversByProcessingNewText() = runTest {
        val failingLlm = DelayedMockLlmClient(shouldFail = true)
        val pipeline = DefaultAvatarPipeline(
            config = testConfig,
            llmClient = failingLlm,
            scope = CoroutineScope(
                SupervisorJob() + UnconfinedTestDispatcher(testScheduler),
            ),
        )
        pipeline.start()

        pipeline.processText("Will fail")
        advanceUntilIdle()
        delay(STATE_SETTLE_DELAY)

        assertIs<AvatarPipelineState.Error>(pipeline.state.value)
        pipeline.release()

        // New pipeline demonstrates recovery from error
        val workingLlm = DelayedMockLlmClient(tokens = listOf("Recovered."))
        val recoveredPipeline = DefaultAvatarPipeline(
            config = testConfig,
            llmClient = workingLlm,
            scope = CoroutineScope(
                SupervisorJob() + UnconfinedTestDispatcher(testScheduler),
            ),
        )
        recoveredPipeline.start()

        recoveredPipeline.processText("Try again")
        advanceUntilIdle()
        delay(STATE_SETTLE_DELAY)

        assertEquals(AvatarPipelineState.Idle, recoveredPipeline.state.value)
        recoveredPipeline.release()
    }

    @Test
    fun concurrentProcessTextCancelsPrevious() = runTest {
        val slowLlm = DelayedMockLlmClient(
            tokens = listOf("Slow", " response."),
            delayPerTokenMs = SLOW_TOKEN_DELAY_MS,
        )
        val pipeline = DefaultAvatarPipeline(
            config = testConfig,
            llmClient = slowLlm,
            scope = CoroutineScope(
                SupervisorJob() + UnconfinedTestDispatcher(testScheduler),
            ),
        )
        pipeline.start()

        val outputs = mutableListOf<io.github.kmpfacelink.pipeline.AvatarPipelineOutput>()
        val collectJob = launch(UnconfinedTestDispatcher(testScheduler)) {
            pipeline.output.collect { outputs.add(it) }
        }
        yield()

        // Start first processing
        pipeline.processText("First")
        yield() // let the first job start
        // Immediately start second — should cancel first
        pipeline.processText("Second")
        advanceUntilIdle()
        delay(STATE_SETTLE_DELAY)

        collectJob.cancel()
        pipeline.release()

        // Pipeline should end in Idle after second completes
        assertEquals(AvatarPipelineState.Idle, pipeline.state.value)
        // Should have called chat twice (first cancelled, second completed)
        assertEquals(2, slowLlm.chatCallCount)
    }
}

// region Test Fakes

private class FakeAsrEngine : AsrEngine {
    private val _state = MutableStateFlow<AsrState>(AsrState.Idle)
    override val state: StateFlow<AsrState> = _state.asStateFlow()

    private val _transcriptions = MutableSharedFlow<TranscriptionResult>(
        extraBufferCapacity = EXTRA_BUFFER_CAPACITY,
    )
    override val transcriptions: Flow<TranscriptionResult> = _transcriptions

    override suspend fun transcribe(audio: AudioData): TranscriptionResult =
        TranscriptionResult(text = "", isFinal = true)

    override suspend fun startListening() {
        _state.value = AsrState.Listening
    }

    override suspend fun stopListening() {
        _state.value = AsrState.Idle
    }

    override fun release() = Unit
}

// endregion
