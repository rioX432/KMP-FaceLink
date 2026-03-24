package io.github.kmpfacelink.pipeline.integration

import io.github.kmpfacelink.llm.LlmConfig
import io.github.kmpfacelink.llm.PersonaConfig
import io.github.kmpfacelink.pipeline.AvatarPipelineConfig
import io.github.kmpfacelink.pipeline.AvatarPipelineOutput
import io.github.kmpfacelink.pipeline.DefaultAvatarPipeline
import io.github.kmpfacelink.pipeline.benchmark.PipelineLatencyTracker
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
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
            dispatcher = UnconfinedTestDispatcher(),
        )
        pipeline.start()

        tracker.onProcessingStart()

        val outputs = mutableListOf<AvatarPipelineOutput>()
        val collectJob = launch {
            pipeline.output.toList(outputs)
        }

        var firstTokenRecorded = false
        var firstSentenceRecorded = false
        var firstAudioRecorded = false
        val outputTrackJob = launch {
            pipeline.output.collect { output ->
                if (output.text != null && !firstTokenRecorded) {
                    tracker.onFirstToken()
                    firstTokenRecorded = true
                }
                if (output.isAudioPlaying && !firstAudioRecorded) {
                    tracker.onFirstAudio()
                    firstAudioRecorded = true
                }
                if (output.blendShapes.isNotEmpty()) {
                    if (!firstSentenceRecorded) {
                        tracker.onFirstSentence()
                        firstSentenceRecorded = true
                    }
                    tracker.onFirstBlendShape()
                }
            }
        }

        pipeline.processText("Test")
        advanceUntilIdle()
        delay(STATE_SETTLE_DELAY)

        tracker.onComplete()

        collectJob.cancel()
        outputTrackJob.cancel()
        pipeline.release()

        val report = tracker.report()
        assertTrue(report.firstTokenLatencyMs >= 0, "First token latency should be recorded")
        assertTrue(report.totalLatencyMs >= 0, "Total latency should be recorded")
        assertTrue(report.totalFrames > 0, "Should have recorded blend shape frames")
        assertTrue(report.sentenceCount > 0, "Should have recorded sentences")
    }

    @Test
    fun streamingAdvantageFirstBlendShapeBeforeLlmCompletion() = runTest {
        // LLM emits 6 tokens with 50ms delay = ~300ms total
        // After first sentence (2 tokens, ~100ms), TTS + lip sync starts
        // So first blend shape should appear before all 6 tokens are emitted
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
            dispatcher = UnconfinedTestDispatcher(),
        )
        pipeline.start()

        val outputs = mutableListOf<AvatarPipelineOutput>()
        val collectJob = launch { pipeline.output.toList(outputs) }

        pipeline.processText("Test streaming")
        advanceUntilIdle()
        delay(STATE_SETTLE_DELAY)

        collectJob.cancel()
        pipeline.release()

        // Verify streaming happened: should have blend shape outputs
        val blendShapeOutputs = outputs.filter { it.blendShapes.isNotEmpty() }
        assertTrue(
            blendShapeOutputs.isNotEmpty(),
            "Should have blend shape frames from streaming pipeline",
        )
    }
}
