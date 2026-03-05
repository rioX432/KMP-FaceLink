package io.github.kmpfacelink.voice.tts

import io.github.kmpfacelink.voice.tts.internal.TtsEngineFactory
import kotlin.test.Test
import kotlin.test.assertIs
import kotlin.test.assertNotNull

class VoicevoxTtsEngineTest {

    private fun createEngine(
        host: String = "localhost",
        port: Int = 50021,
        speakerId: Int = 0,
    ): TtsEngine = TtsEngineFactory.create(
        TtsConfig.Voicevox(
            host = host,
            port = port,
            speakerId = speakerId,
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
        engine.release()
    }

    @Test
    fun stateFlowIsNotNull() {
        val engine = createEngine()
        assertNotNull(engine.state)
        engine.release()
    }

    @Test
    fun customHostAndPortAreAccepted() {
        val engine = createEngine(host = "192.168.1.100", port = 50022, speakerId = 3)
        assertIs<TtsState.Idle>(engine.state.value)
        engine.release()
    }

    @Test
    fun multipleInstancesWithDifferentSpeakers() {
        val engine1 = createEngine(speakerId = 0)
        val engine2 = createEngine(speakerId = 5)
        assertIs<TtsState.Idle>(engine1.state.value)
        assertIs<TtsState.Idle>(engine2.state.value)
        engine1.release()
        engine2.release()
    }
}
