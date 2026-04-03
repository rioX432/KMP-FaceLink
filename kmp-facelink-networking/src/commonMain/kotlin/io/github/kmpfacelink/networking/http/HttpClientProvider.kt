package io.github.kmpfacelink.networking.http

import io.ktor.client.engine.HttpClientEngineFactory

public expect fun platformHttpEngine(): HttpClientEngineFactory<*>
