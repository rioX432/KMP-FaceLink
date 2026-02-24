package io.github.kmpfacelink.llm.internal

import io.ktor.client.engine.HttpClientEngineFactory
import io.ktor.client.engine.darwin.Darwin

internal actual fun platformHttpEngine(): HttpClientEngineFactory<*> = Darwin
