package io.github.kmpfacelink.voice.asr.internal

import io.github.kmpfacelink.voice.asr.AsrConfig
import io.github.kmpfacelink.voice.asr.AsrEngine
import io.ktor.client.HttpClient

/**
 * Factory for creating [AsrEngine] instances from configuration.
 */
internal object AsrEngineFactory {

    fun create(config: AsrConfig, httpClient: HttpClient? = null): AsrEngine = when (config) {
        is AsrConfig.OpenAiWhisper -> OpenAiAsrEngine(config, httpClient)
        is AsrConfig.WhisperCpp -> WhisperCppAsrEngine(config)
    }
}
