package io.github.kmpfacelink.llm.internal

import io.github.kmpfacelink.llm.LlmClient
import io.github.kmpfacelink.llm.LlmConfig

internal actual fun createOnDeviceLlmClient(config: LlmConfig.OnDevice): LlmClient {
    throw UnsupportedOperationException(
        "On-device LLM is not yet supported on iOS. MLX integration is planned.",
    )
}
