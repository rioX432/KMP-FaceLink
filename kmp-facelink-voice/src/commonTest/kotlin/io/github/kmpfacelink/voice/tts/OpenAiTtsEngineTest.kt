package io.github.kmpfacelink.voice.tts

import io.github.kmpfacelink.voice.tts.internal.TtsEngineFactory
import kotlin.test.Test
import kotlin.test.assertIs
import kotlin.test.assertNotNull

class OpenAiTtsEngineTest {

    private fun createEngine(
        apiKey: String = "test-key",
        voice: String = "alloy",
        model: String = "tts-1",
        speed: Float = 1.0f,
    ): TtsEngine = TtsEngineFactory.create(
        TtsConfig.OpenAiTts(
            apiKey = apiKey,
            voice = voice,
            model = model,
            speed = speed,
        ),
    )

    @Test
    fun initialStateIsIdle() {
        val engine = createEngine()
        assertIs<TtsState.Idle>(engine.state.value)
        engine.release()
    }

    @Test
    fun engineIsNotNull() {
        val engine = createEngine()
        assertNotNull(engine)
        engine.release()
    }

    @Test
    fun releaseDoesNotThrow() {
        val engine = createEngine()
        engine.release()
        // Second release should also not throw
        engine.release()
    }

    @Test
    fun stateFlowIsNotNull() {
        val engine = createEngine()
        assertNotNull(engine.state)
        engine.release()
    }

    @Test
    fun multipleInstancesCanBeCreated() {
        val engine1 = createEngine(apiKey = "key-1")
        val engine2 = createEngine(apiKey = "key-2")
        assertNotNull(engine1)
        assertNotNull(engine2)
        assertIs<TtsState.Idle>(engine1.state.value)
        assertIs<TtsState.Idle>(engine2.state.value)
        engine1.release()
        engine2.release()
    }
}
