package io.github.kmpfacelink.voice.tts.internal

import io.ktor.client.engine.HttpClientEngineFactory
import io.ktor.client.engine.okhttp.OkHttp

internal actual fun platformHttpEngine(): HttpClientEngineFactory<*> = OkHttp
