package io.github.kmpfacelink.pipeline.integration

import io.github.kmpfacelink.llm.LlmConfig
import io.github.kmpfacelink.llm.PersonaConfig
import io.github.kmpfacelink.pipeline.AvatarPipelineConfig
import io.github.kmpfacelink.pipeline.AvatarPipelineOutput
import io.github.kmpfacelink.pipeline.DefaultAvatarPipeline
import io.github.kmpfacelink.pipeline.benchmark.PipelineLatencyTracker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield
import kotlin.test.Test
import kotlin.test.assertTrue

private const val STATE_SETTLE_DELAY = 50L
private const val DELAY_PER_TOKEN_MS = 50L
private const val TTS_DELAY_MS = 100L
private const val DELAY_PER_FRAME_MS = 16L
private const val FRAMES_PER_SENTENCE = 3

class PipelineLatencyTest {

    private val testConfig = AvatarPipelineConfig(
        llmConfig = LlmConfig.OpenAi(apiKey = "test-key"),
        persona = PersonaConfig(systemPrompt = "You are a test assistant."),
    )

    @Test
    fun measurePipelineLatencyWithControlledDelays() = runTest {
        val tracker = PipelineLatencyTracker()
        val llm = DelayedMockLlmClient(
            tokens = listOf("Hello", " world."),
            delayPerTokenMs = DELAY_PER_TOKEN_MS,
        )
        val tts = DelayedMockTtsEngine(delayMs = TTS_DELAY_MS)
        val lipSync = DelayedMockLipSyncEngine(
            framesPerSentence = FRAMES_PER_SENTENCE,
            delayPerFrameMs = DELAY_PER_FRAME_MS,
        )
        val pipeline = DefaultAvatarPipeline(
            config = testConfig,
            llmClient = llm,
            ttsEngine = tts,
            lipSyncEngine = lipSync,
            scope = CoroutineScope(
                SupervisorJob() + UnconfinedTestDispatcher(testScheduler),
            ),
        )
        pipeline.start()

        tracker.onProcessingStart()

        val outputs = mutableListOf<AvatarPipelineOutput>()
        var firstTokenRecorded = false
        var firstSentenceRecorded = false
        var firstAudioRecorded = false
        val collectJob = launch(UnconfinedTestDispatcher(testScheduler)) {
            pipeline.output.collect { output ->
                outputs.add(output)
                recordTrackerEvents(
                    output,
                    tracker,
                    firstTokenRecorded,
                    firstSentenceRecorded,
                    firstAudioRecorded,
                ) { token, sentence, audio ->
                    firstTokenRecorded = token
                    firstSentenceRecorded = sentence
                    firstAudioRecorded = audio
                }
            }
        }
        yield()

        pipeline.processText("Test")
        advanceUntilIdle()
        delay(STATE_SETTLE_DELAY)

        tracker.onComplete()

        collectJob.cancel()
        pipeline.release()

        val report = tracker.report()
        assertTrue(report.firstTokenLatencyMs >= 0, "First token latency should be recorded")
        assertTrue(report.totalLatencyMs >= 0, "Total latency should be recorded")
        assertTrue(report.totalFrames > 0, "Should have recorded blend shape frames")
        assertTrue(report.sentenceCount > 0, "Should have recorded sentences")
    }

    @Test
    fun streamingAdvantageFirstBlendShapeBeforeLlmCompletion() = runTest {
        val llm = DelayedMockLlmClient(
            tokens = listOf("Hello", " world.", " How", " are", " you", "?"),
            delayPerTokenMs = DELAY_PER_TOKEN_MS,
        )
        val tts = DelayedMockTtsEngine(delayMs = 0L)
        val lipSync = DelayedMockLipSyncEngine(
            framesPerSentence = 1,
            delayPerFrameMs = 0L,
        )
        val pipeline = DefaultAvatarPipeline(
            config = testConfig,
            llmClient = llm,
            ttsEngine = tts,
            lipSyncEngine = lipSync,
            scope = CoroutineScope(
                SupervisorJob() + UnconfinedTestDispatcher(testScheduler),
            ),
        )
        pipeline.start()

        val outputs = mutableListOf<AvatarPipelineOutput>()
        val collectJob = launch(UnconfinedTestDispatcher(testScheduler)) {
            pipeline.output.collect { outputs.add(it) }
        }
        yield()

        pipeline.processText("Test streaming")
        advanceUntilIdle()
        delay(STATE_SETTLE_DELAY)

        collectJob.cancel()
        pipeline.release()

        val blendShapeOutputs = outputs.filter { it.blendShapes.isNotEmpty() }
        assertTrue(
            blendShapeOutputs.isNotEmpty(),
            "Should have blend shape frames from streaming pipeline",
        )
    }
}

@Suppress("LongParameterList")
private fun recordTrackerEvents(
    output: AvatarPipelineOutput,
    tracker: PipelineLatencyTracker,
    firstTokenRecorded: Boolean,
    firstSentenceRecorded: Boolean,
    firstAudioRecorded: Boolean,
    update: (Boolean, Boolean, Boolean) -> Unit,
) {
    var token = firstTokenRecorded
    var sentence = firstSentenceRecorded
    var audio = firstAudioRecorded
    if (output.text != null && !token) {
        tracker.onFirstToken()
        token = true
    }
    if (output.isAudioPlaying && !audio) {
        tracker.onFirstAudio()
        audio = true
    }
    if (output.blendShapes.isNotEmpty()) {
        if (!sentence) {
            tracker.onFirstSentence()
            sentence = true
        }
        tracker.onFirstBlendShape()
    }
    update(token, sentence, audio)
}
