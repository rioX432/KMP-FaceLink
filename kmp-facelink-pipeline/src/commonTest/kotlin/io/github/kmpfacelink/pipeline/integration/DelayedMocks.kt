package io.github.kmpfacelink.pipeline.integration

import io.github.kmpfacelink.llm.ConversationHistory
import io.github.kmpfacelink.llm.LlmClient
import io.github.kmpfacelink.llm.LlmState
import io.github.kmpfacelink.model.BlendShape
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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flow

private const val FAKE_DURATION_MS = 100L
private const val JAW_OPEN_VALUE = 0.5f
private const val FRAME_TIMESTAMP_STEP = 16L

/**
 * Mock LLM client that emits tokens with a configurable delay.
 */
internal class DelayedMockLlmClient(
    private val tokens: List<String> = listOf("Hello", " world."),
    private val delayPerTokenMs: Long = 0L,
    private val shouldFail: Boolean = false,
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
            for (token in tokens) {
                if (delayPerTokenMs > 0) delay(delayPerTokenMs)
                emit(token)
            }
        }
    }

    override suspend fun chatOnce(history: ConversationHistory): String =
        tokens.joinToString("")

    override fun release() {
        released = true
    }
}

/**
 * Mock TTS engine that synthesizes with a configurable delay.
 */
internal class DelayedMockTtsEngine(
    private val delayMs: Long = 0L,
) : TtsEngine {
    private val _state = MutableStateFlow<TtsState>(TtsState.Idle)
    override val state: StateFlow<TtsState> = _state.asStateFlow()

    var synthesizeCallCount = 0
        private set

    override suspend fun synthesize(text: String): TtsResult {
        synthesizeCallCount++
        if (delayMs > 0) delay(delayMs)
        return TtsResult(
            audio = AudioData(ByteArray(0), AudioFormat(), FAKE_DURATION_MS),
            phonemeEvents = listOf(PhonemeEvent("a", 0, FAKE_DURATION_MS)),
            durationMs = FAKE_DURATION_MS,
        )
    }

    override fun release() = Unit
}

/**
 * Mock lip sync engine that emits frames with a configurable delay.
 */
internal class DelayedMockLipSyncEngine(
    private val framesPerSentence: Int = 1,
    private val delayPerFrameMs: Long = 0L,
) : LipSyncEngine {

    override fun animateFromPhonemes(
        phonemeEvents: List<PhonemeEvent>,
        config: LipSyncConfig,
    ): Flow<LipSyncFrame> = emitFrames()

    override fun animateFromAudio(
        audio: AudioData,
        config: LipSyncConfig,
    ): Flow<LipSyncFrame> = emitFrames()

    private fun emitFrames(): Flow<LipSyncFrame> = flow {
        for (i in 0 until framesPerSentence) {
            if (delayPerFrameMs > 0) delay(delayPerFrameMs)
            emit(
                LipSyncFrame(
                    blendShapes = mapOf(BlendShape.JAW_OPEN to JAW_OPEN_VALUE),
                    timestampMs = i * FRAME_TIMESTAMP_STEP,
                ),
            )
        }
    }
}
