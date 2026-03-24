package io.github.kmpfacelink.voice.asr

import io.github.kmpfacelink.voice.audio.AudioData
import io.github.kmpfacelink.voice.audio.AudioFormat
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * Contract tests for the [AsrEngine] interface.
 */
class AsrEngineContractTest {

    private fun createEngine(): FakeAsrEngineForContract = FakeAsrEngineForContract()

    @Test
    fun initialStateIsIdle() {
        val engine = createEngine()
        assertIs<AsrState.Idle>(engine.state.value)
    }

    @Test
    fun startListeningSetsListeningState() = runTest {
        val engine = createEngine()
        engine.startListening()
        assertIs<AsrState.Listening>(engine.state.value)
    }

    @Test
    fun stopListeningReturnsToIdle() = runTest {
        val engine = createEngine()
        engine.startListening()
        engine.stopListening()
        assertIs<AsrState.Idle>(engine.state.value)
    }

    @Test
    fun transcribeReturnsFinalResult() = runTest {
        val engine = createEngine()
        val audio = AudioData(ByteArray(16000), AudioFormat(), durationMs = 1000L)
        val result = engine.transcribe(audio)
        assertTrue(result.isFinal)
        assertTrue(result.text.isNotEmpty())
    }

    @Test
    fun transcriptionsFlowEmitsDuringListening() = runTest {
        val engine = createEngine()
        engine.startListening()
        engine.emitTranscription(TranscriptionResult(text = "hello", isFinal = true))
        val result = engine.transcriptions.first()
        assertEquals("hello", result.text)
        engine.stopListening()
    }

    @Test
    fun asrStateIdleIsSingleton() {
        assertIs<AsrState.Idle>(AsrState.Idle)
        assertEquals(AsrState.Idle, AsrState.Idle)
    }

    @Test
    fun asrStateListeningIsSingleton() {
        assertEquals(AsrState.Listening, AsrState.Listening)
    }

    @Test
    fun asrStateProcessingIsSingleton() {
        assertEquals(AsrState.Processing, AsrState.Processing)
    }

    @Test
    fun asrStateErrorHoldsMessage() {
        val error = AsrState.Error("mic unavailable")
        assertEquals("mic unavailable", error.message)
    }

    @Test
    fun allAsrStatesAreSubtypes() {
        val states: List<AsrState> = listOf(
            AsrState.Idle,
            AsrState.Listening,
            AsrState.Processing,
            AsrState.Error("err"),
        )
        assertIs<AsrState.Idle>(states[0])
        assertIs<AsrState.Listening>(states[1])
        assertIs<AsrState.Processing>(states[2])
        assertIs<AsrState.Error>(states[3])
    }
}

private class FakeAsrEngineForContract : AsrEngine {
    private val _state = MutableStateFlow<AsrState>(AsrState.Idle)
    private val _transcriptions = MutableSharedFlow<TranscriptionResult>(extraBufferCapacity = 16)

    override val state: StateFlow<AsrState> = _state.asStateFlow()
    override val transcriptions: Flow<TranscriptionResult> = _transcriptions

    override suspend fun transcribe(audio: AudioData): TranscriptionResult =
        TranscriptionResult(text = "transcribed text", isFinal = true)

    override suspend fun startListening() {
        _state.value = AsrState.Listening
    }

    override suspend fun stopListening() {
        _state.value = AsrState.Idle
    }

    fun emitTranscription(result: TranscriptionResult) {
        _transcriptions.tryEmit(result)
    }

    override fun release() {
        _state.value = AsrState.Idle
    }
}
