package io.github.kmpfacelink.pipeline.internal

import io.github.kmpfacelink.actions.emotion.Emotion
import io.github.kmpfacelink.model.BlendShape
import io.github.kmpfacelink.pipeline.expression.ExpressionBlender
import io.github.kmpfacelink.pipeline.expression.ExpressionMapper
import io.github.kmpfacelink.pipeline.expression.TextSentimentAnalyzer
import io.github.kmpfacelink.voice.audio.AudioData
import io.github.kmpfacelink.voice.audio.AudioFormat
import io.github.kmpfacelink.voice.lipsync.LipSyncConfig
import io.github.kmpfacelink.voice.lipsync.LipSyncEngine
import io.github.kmpfacelink.voice.lipsync.LipSyncFrame
import io.github.kmpfacelink.voice.tts.PhonemeEvent
import io.github.kmpfacelink.voice.tts.TtsEngine
import io.github.kmpfacelink.voice.tts.TtsResult
import io.github.kmpfacelink.voice.tts.TtsState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

private const val FAKE_DURATION_MS = 100L
private const val FRAME_TIMESTAMP_1 = 0L
private const val FRAME_TIMESTAMP_2 = 16L
private const val JAW_OPEN_VALUE = 0.5f
private const val EYE_WIDE_VALUE = 0.3f

class StreamingCoordinatorTest {

    private val synthesizedTexts = mutableListOf<String>()
    private var phonemeEventsToReturn: List<PhonemeEvent> = listOf(
        PhonemeEvent("a", 0, FAKE_DURATION_MS),
    )

    private val fakeTtsEngine = object : TtsEngine {
        private val _state = MutableStateFlow<TtsState>(TtsState.Idle)
        override val state: StateFlow<TtsState> = _state.asStateFlow()

        override suspend fun synthesize(text: String): TtsResult {
            synthesizedTexts.add(text)
            return TtsResult(
                audio = AudioData(ByteArray(0), AudioFormat(), FAKE_DURATION_MS),
                phonemeEvents = phonemeEventsToReturn,
                durationMs = FAKE_DURATION_MS,
            )
        }

        override fun release() = Unit
    }

    private val lipSyncFrames = listOf(
        LipSyncFrame(mapOf(BlendShape.JAW_OPEN to JAW_OPEN_VALUE), FRAME_TIMESTAMP_1),
        LipSyncFrame(mapOf(BlendShape.JAW_OPEN to JAW_OPEN_VALUE), FRAME_TIMESTAMP_2),
    )
    private var animateFromPhonemesCalled = false
    private var animateFromAudioCalled = false

    private val fakeLipSyncEngine = object : LipSyncEngine {
        override fun animateFromPhonemes(
            phonemeEvents: List<PhonemeEvent>,
            config: LipSyncConfig,
        ): Flow<LipSyncFrame> {
            animateFromPhonemesCalled = true
            return flowOf(*lipSyncFrames.toTypedArray())
        }

        override fun animateFromAudio(
            audio: AudioData,
            config: LipSyncConfig,
        ): Flow<LipSyncFrame> {
            animateFromAudioCalled = true
            return flowOf(*lipSyncFrames.toTypedArray())
        }
    }

    private val mappedEmotions = mutableListOf<Pair<Emotion, Float>>()

    private val fakeExpressionMapper = ExpressionMapper { emotion, intensity ->
        mappedEmotions.add(emotion to intensity)
        mapOf(BlendShape.EYE_WIDE_LEFT to EYE_WIDE_VALUE)
    }

    private val coordinator = StreamingCoordinator(
        ttsEngine = fakeTtsEngine,
        lipSyncEngine = fakeLipSyncEngine,
        sentimentAnalyzer = TextSentimentAnalyzer(),
        expressionMapper = fakeExpressionMapper,
        expressionBlender = ExpressionBlender(),
    )

    @Test
    fun tokensBufferedIntoSentencesThenProcessed() = runTest {
        val tokens = flow {
            emit("Hello ")
            emit("world.")
            emit(" How are ")
            emit("you?")
        }

        val outputs = coordinator.processLlmStream(tokens).toList()

        assertEquals(2, synthesizedTexts.size)
        assertEquals("Hello world.", synthesizedTexts[0])
        assertEquals("How are you?", synthesizedTexts[1])
        // 2 sentences * 2 frames each = 4 outputs
        assertEquals(4, outputs.size)
    }

    @Test
    fun sentimentAnalyzedPerSentence() = runTest {
        // "happy" triggers HAPPY emotion in TextSentimentAnalyzer
        val tokens = flowOf("I am happy! I am sad.")
        coordinator.processLlmStream(tokens).toList()

        assertEquals(2, synthesizedTexts.size)
        assertEquals("I am happy!", synthesizedTexts[0])
        assertEquals("I am sad.", synthesizedTexts[1])
        // Verify emotion was mapped for each sentence
        assertEquals(2, mappedEmotions.size)
    }

    @Test
    fun emotionMappedFromSentimentAnalysis() = runTest {
        // "happy" keyword triggers HAPPY emotion
        val tokens = flowOf("I feel happy!")
        coordinator.processLlmStream(tokens).toList()

        assertEquals(1, mappedEmotions.size)
        assertEquals(Emotion.HAPPY, mappedEmotions[0].first)
    }

    @Test
    fun expressionBlendingCombinesLipSyncAndEmotion() = runTest {
        val tokens = flowOf("Test.")
        val outputs = coordinator.processLlmStream(tokens).toList()

        assertTrue(outputs.isNotEmpty())
        val first = outputs.first()
        // JAW_OPEN is mouth region -> lip sync value
        assertTrue(first.blendShapes.containsKey(BlendShape.JAW_OPEN))
        // EYE_WIDE_LEFT is eye region -> emotion value * 0.7
        assertTrue(first.blendShapes.containsKey(BlendShape.EYE_WIDE_LEFT))
    }

    @Test
    fun outputContainsEmotionAndText() = runTest {
        val tokens = flowOf("I am happy!")
        val outputs = coordinator.processLlmStream(tokens).toList()

        assertTrue(outputs.isNotEmpty())
        assertNotNull(outputs.first().emotion)
        assertEquals("I am happy!", outputs.first().text)
        assertTrue(outputs.all { it.isAudioPlaying })
    }

    @Test
    fun remainingBufferFlushedOnCompletion() = runTest {
        val tokens = flowOf("No terminator here")
        val outputs = coordinator.processLlmStream(tokens).toList()

        assertEquals(1, synthesizedTexts.size)
        assertEquals("No terminator here", synthesizedTexts[0])
        assertEquals(2, outputs.size)
    }

    @Test
    fun emptyStreamProducesNoOutput() = runTest {
        val tokens = emptyFlow<String>()
        val outputs = coordinator.processLlmStream(tokens).toList()
        assertTrue(outputs.isEmpty())
    }

    @Test
    fun usesPhonemeAnimationWhenAvailable() = runTest {
        phonemeEventsToReturn = listOf(PhonemeEvent("b", 0, FAKE_DURATION_MS))
        val tokens = flowOf("Test.")
        coordinator.processLlmStream(tokens).toList()

        assertTrue(animateFromPhonemesCalled)
    }

    @Test
    fun fallsBackToAudioAnimationWithoutPhonemes() = runTest {
        phonemeEventsToReturn = emptyList()
        val tokens = flowOf("Test.")
        coordinator.processLlmStream(tokens).toList()

        assertTrue(animateFromAudioCalled)
    }

    @Test
    fun cancellationStopsProcessing() = runTest {
        val tokens = flow {
            emit("First.")
            emit("Second.")
            emit("Third.")
        }

        // Use take() instead of cancel() to avoid K/N CancellationException propagation
        val collected = coordinator.processLlmStream(tokens).take(2).toList()

        assertEquals(2, collected.size)
        assertTrue(synthesizedTexts.size < 3)
    }

    @Test
    fun timestampPreservedFromLipSyncFrame() = runTest {
        val tokens = flowOf("Test.")
        val outputs = coordinator.processLlmStream(tokens).toList()

        assertEquals(FRAME_TIMESTAMP_1, outputs[0].timestampMs)
        assertEquals(FRAME_TIMESTAMP_2, outputs[1].timestampMs)
    }
}
