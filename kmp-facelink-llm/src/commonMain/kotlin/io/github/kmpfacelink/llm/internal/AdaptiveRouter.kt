package io.github.kmpfacelink.llm.internal

import io.github.kmpfacelink.llm.ConversationHistory
import io.github.kmpfacelink.llm.LlmClient
import io.github.kmpfacelink.llm.RoutingStrategy
import io.github.kmpfacelink.model.ThermalState
import kotlinx.coroutines.flow.StateFlow

/**
 * Reason why a particular backend was selected by adaptive routing.
 */
internal enum class RoutingReason {
    /** Query complexity exceeded the threshold. */
    COMPLEXITY,

    /** Device thermal state is at or above the configured threshold. */
    THERMAL,

    /** Average on-device latency exceeds the configured timeout. */
    LATENCY,

    /** Daily cloud cost budget has been exhausted. */
    COST_BUDGET_EXHAUSTED,

    /** On-device was preferred by all signals. */
    ON_DEVICE_PREFERRED,
}

/**
 * Result of an adaptive routing decision.
 *
 * @property client The selected LLM client
 * @property reason Why this client was selected
 */
internal data class RoutingDecision(
    val client: LlmClient,
    val reason: RoutingReason,
)

/**
 * Dependencies for [AdaptiveRouter].
 */
internal data class AdaptiveRouterDeps(
    val onDeviceClient: LlmClient,
    val cloudClient: LlmClient,
    val strategy: RoutingStrategy.Adaptive,
    val intentClassifier: IntentClassifier,
    val costTracker: CostTracker,
    val latencyTracker: LatencyTracker,
)

/**
 * Multi-signal router that combines thermal, latency, cost, and complexity
 * signals to select between on-device and cloud backends.
 *
 * Signal priority (highest to lowest):
 * 1. Cost budget exhausted -> force on-device
 * 2. Thermal threshold exceeded -> prefer cloud
 * 3. Latency threshold exceeded -> prefer cloud
 * 4. Complexity threshold exceeded -> prefer cloud
 * 5. Default -> on-device
 */
internal class AdaptiveRouter(
    private val deps: AdaptiveRouterDeps,
    private val thermalStateProvider: StateFlow<ThermalState>,
) {

    internal val costTracker: CostTracker get() = deps.costTracker
    internal val latencyTracker: LatencyTracker get() = deps.latencyTracker

    /**
     * Selects the appropriate client based on all routing signals.
     */
    internal fun route(history: ConversationHistory): RoutingDecision {
        if (!deps.costTracker.canAffordRequest()) {
            return RoutingDecision(deps.onDeviceClient, RoutingReason.COST_BUDGET_EXHAUSTED)
        }
        if (isThermalThresholdExceeded()) {
            return RoutingDecision(deps.cloudClient, RoutingReason.THERMAL)
        }
        if (deps.latencyTracker.exceedsTimeout(deps.strategy.latencyTimeoutMs)) {
            return RoutingDecision(deps.cloudClient, RoutingReason.LATENCY)
        }
        if (isComplexQuery(history)) {
            return RoutingDecision(deps.cloudClient, RoutingReason.COMPLEXITY)
        }
        return RoutingDecision(deps.onDeviceClient, RoutingReason.ON_DEVICE_PREFERRED)
    }

    private fun isThermalThresholdExceeded(): Boolean {
        val currentThermal = thermalStateProvider.value
        return currentThermal.ordinal >= deps.strategy.thermalThreshold.ordinal
    }

    private fun isComplexQuery(history: ConversationHistory): Boolean {
        val complexity = deps.intentClassifier.classify(history)
        return complexity.score >= deps.strategy.complexityThreshold
    }
}
