package io.github.kmpfacelink.networking.http

import io.ktor.client.engine.HttpClientEngineFactory
import io.ktor.client.engine.okhttp.OkHttp

public actual fun platformHttpEngine(): HttpClientEngineFactory<*> = OkHttp
