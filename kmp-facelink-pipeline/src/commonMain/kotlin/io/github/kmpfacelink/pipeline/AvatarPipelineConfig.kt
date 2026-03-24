package io.github.kmpfacelink.pipeline

import io.github.kmpfacelink.llm.LlmConfig
import io.github.kmpfacelink.llm.PersonaConfig
import io.github.kmpfacelink.model.ThermalState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

private const val DEFAULT_EXPRESSION_BLEND_RATIO = 0.7f

/**
 * Configuration for the avatar pipeline.
 *
 * Composes configurations from dependent modules. ASR, TTS, and lip sync
 * are configured through the voice pipeline; the pipeline degrades gracefully
 * when voice capabilities are not available.
 *
 * @property llmConfig LLM provider configuration for text generation
 * @property persona Character persona configuration (system prompt and personality)
 * @property expressionBlendRatio Blend ratio between lip sync and expression blend shapes (0.0–1.0).
 *   Higher values give more weight to emotion-driven expressions over lip sync.
 * @property thermalStateProvider Source of current device thermal state, passed through to
 *   the LLM hybrid router when using [io.github.kmpfacelink.llm.RoutingStrategy.Adaptive].
 */
public data class AvatarPipelineConfig(
    public val llmConfig: LlmConfig,
    public val persona: PersonaConfig = PersonaConfig(),
    public val expressionBlendRatio: Float = DEFAULT_EXPRESSION_BLEND_RATIO,
    public val thermalStateProvider: StateFlow<ThermalState> =
        MutableStateFlow(ThermalState.NOMINAL),
)
