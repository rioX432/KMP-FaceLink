package io.github.kmpfacelink.pipeline

import io.github.kmpfacelink.llm.LlmClient
import io.github.kmpfacelink.llm.LlmConfig
import io.github.kmpfacelink.llm.PersonaConfig
import io.github.kmpfacelink.llm.RoutingStrategy
import io.github.kmpfacelink.model.ThermalState
import io.github.kmpfacelink.voice.asr.AsrEngine
import io.github.kmpfacelink.voice.lipsync.LipSyncEngine
import io.github.kmpfacelink.voice.tts.TtsEngine
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

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
    private var thermalState: StateFlow<ThermalState>? = null
    private var customScope: CoroutineScope? = null
    private var customDispatcher: CoroutineDispatcher = Dispatchers.Default

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
     * Sets the coroutine scope for the pipeline.
     *
     * The caller owns [scope] and is responsible for cancelling it.
     * Takes precedence over [dispatcher] if both are set.
     */
    public fun scope(scope: CoroutineScope) {
        customScope = scope
    }

    /**
     * Sets the coroutine dispatcher for background work.
     *
     * An internal [CoroutineScope] is created from the dispatcher at build time.
     * Ignored if [scope] is also set.
     */
    public fun dispatcher(dispatcher: CoroutineDispatcher) {
        customDispatcher = dispatcher
    }

    /**
     * Sets the thermal state provider for adaptive LLM routing.
     *
     * When using [RoutingStrategy.Adaptive], this thermal state is passed to
     * the hybrid LLM router to avoid on-device inference when overheating.
     */
    public fun thermalStateProvider(provider: StateFlow<ThermalState>) {
        thermalState = provider
    }

    /**
     * Builds the [AvatarPipeline].
     *
     * @throws IllegalStateException if neither [llmClient] nor [llm] was provided
     */
    public fun build(): AvatarPipeline {
        val client = resolveClient()
        val config = buildConfig()
        val resolvedScope = customScope
            ?: CoroutineScope(SupervisorJob() + customDispatcher)
        return DefaultAvatarPipeline(
            config = config,
            llmClient = client,
            ttsEngine = customTtsEngine,
            lipSyncEngine = customLipSyncEngine,
            asrEngine = customAsrEngine,
            scope = resolvedScope,
        )
    }

    private fun resolveClient(): LlmClient {
        customLlmClient?.let { return it }
        val config = injectThermalState(
            llmConfig ?: error("LLM configuration required: call llm() or llmClient()"),
        )
        return LlmClient.create(config)
    }

    /**
     * If the LLM config is Hybrid with Adaptive routing and a thermal provider
     * was set on the builder, inject it into the config.
     */
    private fun injectThermalState(config: LlmConfig): LlmConfig {
        val thermal = thermalState ?: return config
        if (config is LlmConfig.Hybrid && config.routingStrategy is RoutingStrategy.Adaptive) {
            return config.copy(thermalStateProvider = thermal)
        }
        return config
    }

    private fun buildConfig(): AvatarPipelineConfig {
        // When a custom LlmClient is injected, llmConfig may be absent.
        // AvatarPipelineConfig requires it, but DefaultAvatarPipeline only uses
        // persona and expressionBlendRatio from the config — not llmConfig.
        val effectiveLlmConfig = llmConfig
            ?: LlmConfig.OpenAi(apiKey = "")
        val effectiveThermal = thermalState
            ?: MutableStateFlow(ThermalState.NOMINAL)
        return AvatarPipelineConfig(
            llmConfig = effectiveLlmConfig,
            persona = personaConfig ?: PersonaConfig(),
            expressionBlendRatio = blendRatio ?: DEFAULT_EXPRESSION_BLEND_RATIO,
            thermalStateProvider = effectiveThermal,
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
