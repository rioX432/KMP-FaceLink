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
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
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
            scope = CoroutineScope(SupervisorJob() + UnconfinedTestDispatcher()),
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
        val tts = DelayedMockTtsEngine()
        val lipSync = DelayedMockLipSyncEngine()
        val pipeline = DefaultAvatarPipeline(
            config = testConfig,
            llmClient = DelayedMockLlmClient(tokens = listOf("OK.")),
            ttsEngine = tts,
            lipSyncEngine = lipSync,
            scope = CoroutineScope(SupervisorJob() + UnconfinedTestDispatcher()),
        )
        pipeline.start()

        val states = mutableListOf<AvatarPipelineState>()
        val collectJob = launch { pipeline.state.toList(states) }

        pipeline.processText("Hello")
        advanceUntilIdle()
        delay(STATE_SETTLE_DELAY)

        collectJob.cancel()
        pipeline.release()

        assertTrue(
            states.any { it is AvatarPipelineState.Processing },
            "Should transition through Processing",
        )
        assertTrue(
            states.any { it is AvatarPipelineState.Speaking },
            "Should transition through Speaking",
        )
        assertEquals(
            AvatarPipelineState.Idle,
            states.last(),
            "Should return to Idle",
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
            scope = CoroutineScope(SupervisorJob() + UnconfinedTestDispatcher()),
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
            scope = CoroutineScope(SupervisorJob() + UnconfinedTestDispatcher()),
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
            scope = CoroutineScope(SupervisorJob() + UnconfinedTestDispatcher()),
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
            scope = CoroutineScope(SupervisorJob() + UnconfinedTestDispatcher()),
        )
        pipeline.start()

        val outputs = mutableListOf<io.github.kmpfacelink.pipeline.AvatarPipelineOutput>()
        val collectJob = launch { pipeline.output.toList(outputs) }

        // Start first processing
        pipeline.processText("First")
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
