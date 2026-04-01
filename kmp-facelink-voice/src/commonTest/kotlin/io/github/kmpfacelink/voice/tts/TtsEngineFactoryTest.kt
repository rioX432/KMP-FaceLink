package io.github.kmpfacelink.voice.tts

import io.github.kmpfacelink.voice.tts.internal.TtsEngineFactory
import kotlin.test.Test
import kotlin.test.assertIs
import kotlin.test.assertNotSame

class TtsEngineFactoryTest {

    @Test
    fun createOpenAiTtsEngine() {
        val config = TtsConfig.OpenAiTts(apiKey = "test-key")
        val engine = TtsEngineFactory.create(config)
        assertIs<TtsEngine>(engine)
        assertIs<TtsState.Idle>(engine.state.value)
        engine.release()
    }

    @Test
    fun createElevenLabsEngine() {
        val config = TtsConfig.ElevenLabs(apiKey = "test-key", voiceId = "voice-1")
        val engine = TtsEngineFactory.create(config)
        assertIs<TtsEngine>(engine)
        assertIs<TtsState.Idle>(engine.state.value)
        engine.release()
    }

    @Test
    fun createVoicevoxEngine() {
        val config = TtsConfig.Voicevox()
        val engine = TtsEngineFactory.create(config)
        assertIs<TtsEngine>(engine)
        assertIs<TtsState.Idle>(engine.state.value)
        engine.release()
    }

    @Test
    fun factoryCreatesDistinctInstances() {
        val config = TtsConfig.Voicevox()
        val engine1 = TtsEngineFactory.create(config)
        val engine2 = TtsEngineFactory.create(config)
        assertNotSame(engine1, engine2)
        engine1.release()
        engine2.release()
    }

    @Test
    fun factoryCreatesDistinctOpenAiInstances() {
        val config = TtsConfig.OpenAiTts(apiKey = "key")
        val engine1 = TtsEngineFactory.create(config)
        val engine2 = TtsEngineFactory.create(config)
        assertNotSame(engine1, engine2)
        engine1.release()
        engine2.release()
    }

    @Test
    fun factoryCreatesDistinctElevenLabsInstances() {
        val config = TtsConfig.ElevenLabs(apiKey = "key", voiceId = "v")
        val engine1 = TtsEngineFactory.create(config)
        val engine2 = TtsEngineFactory.create(config)
        assertNotSame(engine1, engine2)
        engine1.release()
        engine2.release()
    }

    @Test
    fun openAiEngineWithCustomBaseUrl() {
        val config = TtsConfig.OpenAiTts(
            apiKey = "key",
            baseUrl = "https://proxy.example.com/v1",
        )
        val engine = TtsEngineFactory.create(config)
        assertIs<TtsEngine>(engine)
        engine.release()
    }

    @Test
    fun elevenLabsEngineWithCustomBaseUrl() {
        val config = TtsConfig.ElevenLabs(
            apiKey = "key",
            baseUrl = "https://proxy.example.com/v1",
            voiceId = "voice-1",
        )
        val engine = TtsEngineFactory.create(config)
        assertIs<TtsEngine>(engine)
        engine.release()
    }

    @Test
    fun voicevoxEngineWithCustomHostAndPort() {
        val config = TtsConfig.Voicevox(
            host = "192.168.1.100",
            port = 8080,
            speakerId = 5,
        )
        val engine = TtsEngineFactory.create(config)
        assertIs<TtsEngine>(engine)
        engine.release()
    }

    @Test
    fun allProviderEnginesStartIdle() {
        val configs: List<TtsConfig> = listOf(
            TtsConfig.OpenAiTts(apiKey = "k"),
            TtsConfig.ElevenLabs(apiKey = "k", voiceId = "v"),
            TtsConfig.Voicevox(),
        )
        val engines = configs.map { TtsEngineFactory.create(it) }
        engines.forEach { engine ->
            assertIs<TtsState.Idle>(engine.state.value)
        }
        engines.forEach { it.release() }
    }
}
