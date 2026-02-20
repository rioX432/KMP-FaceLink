package io.github.kmpfacelink.voice.tts.internal

import io.github.kmpfacelink.voice.tts.TtsConfig
import io.github.kmpfacelink.voice.tts.TtsEngine

/**
 * Factory for creating [TtsEngine] instances from configuration.
 */
internal object TtsEngineFactory {

    fun create(config: TtsConfig): TtsEngine = when (config) {
        is TtsConfig.OpenAiTts -> OpenAiTtsEngine(config)
        is TtsConfig.ElevenLabs -> ElevenLabsTtsEngine(config)
        is TtsConfig.Voicevox -> VoicevoxTtsEngine(config)
    }
}
