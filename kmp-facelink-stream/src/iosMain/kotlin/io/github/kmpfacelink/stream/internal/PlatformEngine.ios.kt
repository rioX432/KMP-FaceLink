package io.github.kmpfacelink.stream.internal

import io.ktor.client.engine.HttpClientEngineFactory
import io.ktor.client.engine.darwin.Darwin

internal actual fun platformEngine(): HttpClientEngineFactory<*> = Darwin
