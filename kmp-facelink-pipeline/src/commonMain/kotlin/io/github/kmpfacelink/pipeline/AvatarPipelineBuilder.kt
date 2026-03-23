package io.github.kmpfacelink.pipeline

import io.github.kmpfacelink.llm.LlmClient
import io.github.kmpfacelink.llm.LlmConfig
import io.github.kmpfacelink.llm.PersonaConfig
import io.github.kmpfacelink.voice.asr.AsrEngine
import io.github.kmpfacelink.voice.lipsync.LipSyncEngine
import io.github.kmpfacelink.voice.tts.TtsEngine

private const val DEFAULT_EXPRESSION_BLEND_RATIO = 0.7f

/**
 * DSL builder for constructing [AvatarPipeline] instances.
 *
 * At minimum, either an [LlmConfig] (via [llm]) or a pre-built [LlmClient]
 * (via [llmClient]) must be provided. All other settings are optional.
 *
 * Example usage:
 * ```kotlin
 * val pipeline = buildAvatarPipeline {
 *     llm(LlmConfig.OpenAi(apiKey = "sk-..."))
 *     persona(PersonaConfig(name = "Ai", systemPrompt = "You are a friendly assistant"))
 *     expressionBlendRatio(0.7f)
 * }
 * ```
 */
public class AvatarPipelineBuilder {

    private var llmConfig: LlmConfig? = null
    private var personaConfig: PersonaConfig? = null
    private var blendRatio: Float? = null
    private var customLlmClient: LlmClient? = null
    private var customTtsEngine: TtsEngine? = null
    private var customLipSyncEngine: LipSyncEngine? = null
    private var customAsrEngine: AsrEngine? = null

    /** Sets the LLM provider configuration. */
    public fun llm(config: LlmConfig) {
        llmConfig = config
    }

    /** Sets the character persona configuration. */
    public fun persona(config: PersonaConfig) {
        personaConfig = config
    }

    /** Sets the expression blend ratio (0.0-1.0). */
    public fun expressionBlendRatio(ratio: Float) {
        blendRatio = ratio
    }

    /** Injects a pre-built [LlmClient] instead of creating one from config. */
    public fun llmClient(client: LlmClient) {
        customLlmClient = client
    }

    /** Injects a custom [TtsEngine]. */
    public fun ttsEngine(engine: TtsEngine) {
        customTtsEngine = engine
    }

    /** Injects a custom [LipSyncEngine]. */
    public fun lipSyncEngine(engine: LipSyncEngine) {
        customLipSyncEngine = engine
    }

    /** Injects a custom [AsrEngine]. */
    public fun asrEngine(engine: AsrEngine) {
        customAsrEngine = engine
    }

    /**
     * Builds the [AvatarPipeline].
     *
     * @throws IllegalStateException if neither [llmClient] nor [llm] was provided
     */
    public fun build(): AvatarPipeline {
        val client = resolveClient()
        val config = buildConfig()
        return DefaultAvatarPipeline(
            config = config,
            llmClient = client,
            ttsEngine = customTtsEngine,
            lipSyncEngine = customLipSyncEngine,
            asrEngine = customAsrEngine,
        )
    }

    private fun resolveClient(): LlmClient {
        customLlmClient?.let { return it }
        val config = llmConfig
            ?: error("LLM configuration required: call llm() or llmClient()")
        return LlmClient.create(config)
    }

    private fun buildConfig(): AvatarPipelineConfig {
        // When a custom LlmClient is injected, llmConfig may be absent.
        // AvatarPipelineConfig requires it, but DefaultAvatarPipeline only uses
        // persona and expressionBlendRatio from the config — not llmConfig.
        val effectiveLlmConfig = llmConfig
            ?: LlmConfig.OpenAi(apiKey = "")
        return AvatarPipelineConfig(
            llmConfig = effectiveLlmConfig,
            persona = personaConfig ?: PersonaConfig(),
            expressionBlendRatio = blendRatio ?: DEFAULT_EXPRESSION_BLEND_RATIO,
        )
    }
}

/**
 * Builds an [AvatarPipeline] using a DSL builder.
 *
 * @param block Configuration block applied to [AvatarPipelineBuilder]
 * @return A configured [AvatarPipeline] instance
 * @throws IllegalStateException if LLM configuration is missing
 */
public fun buildAvatarPipeline(block: AvatarPipelineBuilder.() -> Unit): AvatarPipeline {
    return AvatarPipelineBuilder().apply(block).build()
}
