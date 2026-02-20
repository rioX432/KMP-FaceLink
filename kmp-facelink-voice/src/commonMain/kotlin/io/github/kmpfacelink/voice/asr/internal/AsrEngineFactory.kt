package io.github.kmpfacelink.voice.asr.internal

import io.github.kmpfacelink.voice.asr.AsrConfig
import io.github.kmpfacelink.voice.asr.AsrEngine

/**
 * Factory for creating [AsrEngine] instances from configuration.
 */
internal object AsrEngineFactory {

    fun create(config: AsrConfig): AsrEngine = when (config) {
        is AsrConfig.OpenAiWhisper -> OpenAiAsrEngine(config)
        is AsrConfig.WhisperCpp -> WhisperCppAsrEngine(config)
    }
}
