package io.github.kmpfacelink.voice.tts

import io.github.kmpfacelink.voice.audio.AudioData
import io.github.kmpfacelink.voice.audio.AudioFormat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

private const val FAKE_DURATION_MS = 500L

/**
 * Contract tests for the [TtsEngine] interface.
 */
class TtsEngineContractTest {

    private fun createEngine(): FakeTtsEngineForContract = FakeTtsEngineForContract()

    @Test
    fun initialStateIsIdle() {
        val engine = createEngine()
        assertIs<TtsState.Idle>(engine.state.value)
    }

    @Test
    fun synthesizeReturnsAudioWithDuration() = runTest {
        val engine = createEngine()
        val result = engine.synthesize("Hello world")
        assertTrue(result.durationMs > 0)
        assertTrue(result.audio.bytes.isNotEmpty())
    }

    @Test
    fun synthesizeReturnsMatchingDuration() = runTest {
        val engine = createEngine()
        val result = engine.synthesize("Test text")
        assertEquals(result.durationMs, result.audio.durationMs)
    }

    @Test
    fun releaseCanBeCalledSafely() {
        val engine = createEngine()
        engine.release()
        // Should not throw
    }

    @Test
    fun ttsStateIdleIsSingleton() {
        assertEquals(TtsState.Idle, TtsState.Idle)
    }

    @Test
    fun ttsStateSynthesizingIsSingleton() {
        assertEquals(TtsState.Synthesizing, TtsState.Synthesizing)
    }

    @Test
    fun ttsStateErrorHoldsMessage() {
        val error = TtsState.Error("synthesis failed")
        assertEquals("synthesis failed", error.message)
    }

    @Test
    fun allTtsStatesAreSubtypes() {
        val states: List<TtsState> = listOf(
            TtsState.Idle,
            TtsState.Synthesizing,
            TtsState.Error("err"),
        )
        assertIs<TtsState.Idle>(states[0])
        assertIs<TtsState.Synthesizing>(states[1])
        assertIs<TtsState.Error>(states[2])
    }
}

private class FakeTtsEngineForContract : TtsEngine {
    private val _state = MutableStateFlow<TtsState>(TtsState.Idle)
    override val state: StateFlow<TtsState> = _state.asStateFlow()

    override suspend fun synthesize(text: String): TtsResult {
        _state.value = TtsState.Synthesizing
        val result = TtsResult(
            audio = AudioData(ByteArray(16000), AudioFormat(), FAKE_DURATION_MS),
            phonemeEvents = listOf(
                PhonemeEvent("h", 0L, 100L),
                PhonemeEvent("e", 100L, 200L),
            ),
            durationMs = FAKE_DURATION_MS,
        )
        _state.value = TtsState.Idle
        return result
    }

    override fun release() {
        _state.value = TtsState.Idle
    }
}
