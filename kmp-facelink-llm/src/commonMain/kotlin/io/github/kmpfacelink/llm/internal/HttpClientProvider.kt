package io.github.kmpfacelink.llm.internal

import io.ktor.client.engine.HttpClientEngineFactory

internal expect fun platformHttpEngine(): HttpClientEngineFactory<*>
