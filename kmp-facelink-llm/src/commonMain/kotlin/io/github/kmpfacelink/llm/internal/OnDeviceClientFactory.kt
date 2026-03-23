package io.github.kmpfacelink.llm.internal

import io.github.kmpfacelink.llm.LlmClient
import io.github.kmpfacelink.llm.LlmConfig

/**
 * Platform-specific factory for creating on-device [LlmClient] instances.
 *
 * Android uses llama.cpp via JNI; iOS will use MLX (future).
 * Platforms that do not support on-device inference throw [UnsupportedOperationException].
 */
internal expect fun createOnDeviceLlmClient(config: LlmConfig.OnDevice): LlmClient
