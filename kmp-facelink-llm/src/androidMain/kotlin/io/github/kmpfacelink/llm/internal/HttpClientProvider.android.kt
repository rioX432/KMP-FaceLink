package io.github.kmpfacelink.llm.internal

import io.ktor.client.engine.HttpClientEngineFactory
import io.ktor.client.engine.okhttp.OkHttp

internal actual fun platformHttpEngine(): HttpClientEngineFactory<*> = OkHttp
