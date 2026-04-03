package io.github.kmpfacelink.networking.http

import io.ktor.client.engine.HttpClientEngineFactory
import io.ktor.client.engine.darwin.Darwin

public actual fun platformHttpEngine(): HttpClientEngineFactory<*> = Darwin
