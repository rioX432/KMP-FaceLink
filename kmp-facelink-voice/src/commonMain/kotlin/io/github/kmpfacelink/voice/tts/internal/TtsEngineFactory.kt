package io.github.kmpfacelink.voice.tts.internal

import io.github.kmpfacelink.voice.tts.TtsConfig
import io.github.kmpfacelink.voice.tts.TtsEngine
import io.ktor.client.HttpClient

/**
 * Factory for creating [TtsEngine] instances from configuration.
 */
internal object TtsEngineFactory {

    fun create(config: TtsConfig, httpClient: HttpClient? = null): TtsEngine = when (config) {
        is TtsConfig.OpenAiTts -> OpenAiTtsEngine(config, httpClient)
        is TtsConfig.ElevenLabs -> ElevenLabsTtsEngine(config, httpClient)
        is TtsConfig.Voicevox -> VoicevoxTtsEngine(config, httpClient)
    }
}
