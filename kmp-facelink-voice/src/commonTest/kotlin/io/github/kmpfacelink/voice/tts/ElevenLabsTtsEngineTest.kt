package io.github.kmpfacelink.voice.tts

import io.github.kmpfacelink.voice.tts.internal.TtsEngineFactory
import kotlin.test.Test
import kotlin.test.assertIs
import kotlin.test.assertNotNull

class ElevenLabsTtsEngineTest {

    private fun createEngine(
        apiKey: String = "el-test-key",
        voiceId: String = "voice-abc",
        modelId: String = "eleven_multilingual_v2",
        stability: Float = 0.5f,
        similarityBoost: Float = 0.75f,
    ): TtsEngine = TtsEngineFactory.create(
        TtsConfig.ElevenLabs(
            apiKey = apiKey,
            voiceId = voiceId,
            modelId = modelId,
            stability = stability,
            similarityBoost = similarityBoost,
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
    fun multipleInstancesCanBeCreated() {
        val engine1 = createEngine(voiceId = "voice-1")
        val engine2 = createEngine(voiceId = "voice-2")
        assertIs<TtsState.Idle>(engine1.state.value)
        assertIs<TtsState.Idle>(engine2.state.value)
        engine1.release()
        engine2.release()
    }
}
