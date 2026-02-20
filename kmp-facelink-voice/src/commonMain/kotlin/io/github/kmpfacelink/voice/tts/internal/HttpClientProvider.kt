package io.github.kmpfacelink.voice.tts.internal

import io.ktor.client.engine.HttpClientEngineFactory

internal expect fun platformHttpEngine(): HttpClientEngineFactory<*>
