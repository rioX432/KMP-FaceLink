package io.github.kmpfacelink.pipeline.integration

import io.github.kmpfacelink.llm.LlmConfig
import io.github.kmpfacelink.llm.PersonaConfig
import io.github.kmpfacelink.pipeline.AvatarPipelineConfig
import io.github.kmpfacelink.pipeline.AvatarPipelineOutput
import io.github.kmpfacelink.pipeline.AvatarPipelineState
import io.github.kmpfacelink.pipeline.DefaultAvatarPipeline
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

private const val STATE_SETTLE_DELAY = 50L
private const val FRAMES_PER_SENTENCE = 3

class PipelineIntegrationTest {

    private val testConfig = AvatarPipelineConfig(
        llmConfig = LlmConfig.OpenAi(apiKey = "test-key"),
        persona = PersonaConfig(systemPrompt = "You are a test assistant."),
    )

    private fun createFullPipeline(
        llmClient: DelayedMockLlmClient = DelayedMockLlmClient(
            tokens = listOf("Hello", " world."),
        ),
        ttsEngine: DelayedMockTtsEngine? = DelayedMockTtsEngine(),
        lipSyncEngine: DelayedMockLipSyncEngine? = DelayedMockLipSyncEngine(
            framesPerSentence = FRAMES_PER_SENTENCE,
        ),
        dispatcher: TestDispatcher = UnconfinedTestDispatcher(),
    ): DefaultAvatarPipeline = DefaultAvatarPipeline(
        config = testConfig,
        llmClient = llmClient,
        ttsEngine = ttsEngine,
        lipSyncEngine = lipSyncEngine,
        scope = CoroutineScope(SupervisorJob() + dispatcher),
    )

    @Test
    fun fullConversationFlowProducesBlendShapeOutput() = runTest {
        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        val pipeline = createFullPipeline(dispatcher = dispatcher)
        pipeline.start()

        val outputs = mutableListOf<AvatarPipelineOutput>()
        val collectJob = launch(dispatcher) { pipeline.output.toList(outputs) }
        yield()

        pipeline.processText("Hello")
        advanceUntilIdle()
        delay(STATE_SETTLE_DELAY)

        collectJob.cancel()
        pipeline.release()

        assertTrue(outputs.isNotEmpty(), "Should emit output frames")
        assertTrue(outputs.any { it.blendShapes.isNotEmpty() }, "Should have blend shapes")
        assertTrue(outputs.any { it.isAudioPlaying }, "Should indicate audio playing")
        assertTrue(outputs.any { it.text != null }, "Should contain text")
        assertTrue(outputs.any { it.emotion != null }, "Should contain emotion")
    }

    @Test
    fun multiTurnConversationAccumulatesHistory() = runTest {
        val llmClient = DelayedMockLlmClient(
            tokens = listOf("Response."),
        )
        val pipeline = createFullPipeline(llmClient = llmClient)
        pipeline.start()

        pipeline.processText("First message")
        advanceUntilIdle()
        delay(STATE_SETTLE_DELAY)

        pipeline.processText("Second message")
        advanceUntilIdle()
        delay(STATE_SETTLE_DELAY)

        assertEquals(2, llmClient.chatCallCount, "Should call chat twice")
        assertEquals(AvatarPipelineState.Idle, pipeline.state.value)
        pipeline.release()
    }

    @Test
    fun textOnlyFallbackWhenNoTtsConfigured() = runTest {
        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        val pipeline = createFullPipeline(
            llmClient = DelayedMockLlmClient(tokens = listOf("Hello", " world.")),
            ttsEngine = null,
            lipSyncEngine = null,
            dispatcher = dispatcher,
        )
        pipeline.start()

        val outputs = mutableListOf<AvatarPipelineOutput>()
        val collectJob = launch(dispatcher) { pipeline.output.toList(outputs) }
        yield()

        pipeline.processText("Hello")
        advanceUntilIdle()
        delay(STATE_SETTLE_DELAY)

        collectJob.cancel()
        pipeline.release()

        assertTrue(outputs.isNotEmpty(), "Should emit text-only output")
        assertTrue(outputs.all { !it.isAudioPlaying }, "Should not play audio")
        assertTrue(outputs.all { it.blendShapes.isEmpty() }, "Should have empty blend shapes")
        assertTrue(outputs.any { it.text != null }, "Should contain text")
    }

    @Test
    fun errorRecoveryAfterLlmFailure() = runTest {
        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        val failingLlm = DelayedMockLlmClient(shouldFail = true)
        val pipeline = createFullPipeline(
            llmClient = failingLlm,
            ttsEngine = null,
            lipSyncEngine = null,
            dispatcher = dispatcher,
        )
        pipeline.start()

        pipeline.processText("Will fail")
        advanceUntilIdle()
        delay(STATE_SETTLE_DELAY)

        assertIs<AvatarPipelineState.Error>(pipeline.state.value)

        // Replace pipeline with a working one to verify recovery pattern
        val workingDispatcher = UnconfinedTestDispatcher(testScheduler)
        val workingPipeline = createFullPipeline(dispatcher = workingDispatcher)
        workingPipeline.start()

        val outputs = mutableListOf<AvatarPipelineOutput>()
        val collectJob = launch(workingDispatcher) { workingPipeline.output.toList(outputs) }
        yield()

        workingPipeline.processText("Recover")
        advanceUntilIdle()
        delay(STATE_SETTLE_DELAY)

        collectJob.cancel()

        assertEquals(AvatarPipelineState.Idle, workingPipeline.state.value)
        assertTrue(outputs.isNotEmpty(), "Should produce output after recovery")

        pipeline.release()
        workingPipeline.release()
    }
}
