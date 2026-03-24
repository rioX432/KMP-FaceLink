package io.github.kmpfacelink.llm.internal

import io.github.kmpfacelink.llm.LlmClient
import io.github.kmpfacelink.llm.LlmConfig

/**
 * Platform-specific factory for creating on-device [LlmClient] instances.
 *
 * Android uses llama.cpp via JNI. iOS prefers Apple Foundation Models (iOS 26+)
 * and falls back to error if Foundation Models is unavailable.
 */
internal expect fun createOnDeviceLlmClient(config: LlmConfig.OnDevice): LlmClient
