package io.github.kmpfacelink.voice.asr

import io.github.kmpfacelink.voice.asr.internal.AsrEngineFactory
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertIs
import kotlin.test.assertNotNull

class OpenAiAsrEngineTest {

    private fun createEngine(
        apiKey: String = "test-key",
        model: String = "whisper-1",
        language: String? = null,
    ): AsrEngine = AsrEngineFactory.create(
        AsrConfig.OpenAiWhisper(
            apiKey = apiKey,
            model = model,
            language = language,
        ),
    )

    @Test
    fun initialStateIsIdle() {
        val engine = createEngine()
        assertIs<AsrState.Idle>(engine.state.value)
        engine.release()
    }

    @Test
    fun engineIsNotNull() {
        val engine = createEngine()
        assertNotNull(engine)
        engine.release()
    }

    @Test
    fun transcriptionsFlowIsNotNull() {
        val engine = createEngine()
        assertNotNull(engine.transcriptions)
        engine.release()
    }

    @Test
    fun startListeningTransitionsToListeningState() = runTest {
        val engine = createEngine()
        engine.startListening()
        assertIs<AsrState.Listening>(engine.state.value)
        engine.release()
    }

    @Test
    fun stopListeningTransitionsToIdleState() = runTest {
        val engine = createEngine()
        engine.startListening()
        engine.stopListening()
        assertIs<AsrState.Idle>(engine.state.value)
        engine.release()
    }

    @Test
    fun releaseDoesNotThrow() {
        val engine = createEngine()
        engine.release()
        engine.release()
    }

    @Test
    fun multipleInstancesCanBeCreated() {
        val engine1 = createEngine(apiKey = "key-1")
        val engine2 = createEngine(apiKey = "key-2")
        assertIs<AsrState.Idle>(engine1.state.value)
        assertIs<AsrState.Idle>(engine2.state.value)
        engine1.release()
        engine2.release()
    }
}
