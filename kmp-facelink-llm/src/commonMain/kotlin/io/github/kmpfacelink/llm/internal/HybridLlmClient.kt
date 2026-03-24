package io.github.kmpfacelink.llm.internal

import io.github.kmpfacelink.llm.ConversationHistory
import io.github.kmpfacelink.llm.LlmClient
import io.github.kmpfacelink.llm.LlmState
import io.github.kmpfacelink.llm.RoutingStrategy
import io.github.kmpfacelink.model.ThermalState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.withTimeout

/**
 * Hybrid LLM client that routes queries between on-device and cloud backends.
 *
 * Routes each request based on the [routingStrategy] and [intentClassifier].
 * When using [RoutingStrategy.Adaptive], also considers thermal state, latency,
 * and cost budgeting. When on-device inference fails, automatically falls back
 * to the cloud client.
 */
internal class HybridLlmClient(
    private val onDeviceClient: LlmClient,
    private val cloudClient: LlmClient,
    private val routingStrategy: RoutingStrategy,
    private val intentClassifier: IntentClassifier = IntentClassifier(),
    private val adaptiveRouter: AdaptiveRouter? = null,
) : LlmClient {

    private val _state = MutableStateFlow<LlmState>(LlmState.Idle)
    override val state: StateFlow<LlmState> = _state.asStateFlow()

    private val isAdaptiveWithTimeout: Boolean
        get() = routingStrategy is RoutingStrategy.Adaptive && adaptiveRouter != null

    private val latencyTimeout: Long
        get() = (routingStrategy as? RoutingStrategy.Adaptive)?.latencyTimeoutMs ?: Long.MAX_VALUE

    override fun chat(history: ConversationHistory): Flow<String> = flow {
        _state.value = LlmState.Streaming
        val decision = selectClient(history)
        try {
            collectFromPrimary(decision, history)
        } catch (e: CancellationException) {
            throw e
        } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
            fallbackChat(decision, history, e)
        }
    }.onCompletion { cause ->
        _state.value = if (cause == null) LlmState.Idle else errorState(cause)
    }

    override suspend fun chatOnce(history: ConversationHistory): String {
        val decision = selectClient(history)
        return try {
            chatOnceFromPrimary(decision, history)
        } catch (e: CancellationException) {
            throw e
        } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
            if (decision.client === onDeviceClient) {
                recordCost()
                cloudClient.chatOnce(history)
            } else {
                throw e
            }
        }
    }

    override fun release() {
        onDeviceClient.release()
        cloudClient.release()
    }

    private fun selectClient(history: ConversationHistory): RoutingDecision =
        when (routingStrategy) {
            is RoutingStrategy.AlwaysOnDevice ->
                RoutingDecision(onDeviceClient, RoutingReason.ON_DEVICE_PREFERRED)
            is RoutingStrategy.AlwaysCloud ->
                RoutingDecision(cloudClient, RoutingReason.COMPLEXITY)
            is RoutingStrategy.Auto -> selectByComplexity(history)
            is RoutingStrategy.Adaptive ->
                adaptiveRouter?.route(history) ?: selectByComplexity(history)
        }

    private fun selectByComplexity(history: ConversationHistory): RoutingDecision {
        val threshold = when (routingStrategy) {
            is RoutingStrategy.Auto -> routingStrategy.complexityThreshold
            is RoutingStrategy.Adaptive -> routingStrategy.complexityThreshold
            else -> return RoutingDecision(onDeviceClient, RoutingReason.ON_DEVICE_PREFERRED)
        }
        val complexity = intentClassifier.classify(history)
        return if (complexity.score < threshold) {
            RoutingDecision(onDeviceClient, RoutingReason.ON_DEVICE_PREFERRED)
        } else {
            RoutingDecision(cloudClient, RoutingReason.COMPLEXITY)
        }
    }

    private suspend fun FlowCollector<String>.collectFromPrimary(
        decision: RoutingDecision,
        history: ConversationHistory,
    ) {
        if (decision.client === onDeviceClient && isAdaptiveWithTimeout) {
            collectWithTimeout(history)
        } else {
            if (decision.client === cloudClient) recordCost()
            decision.client.chat(history).collect { emit(it) }
        }
    }

    private suspend fun FlowCollector<String>.collectWithTimeout(
        history: ConversationHistory,
    ) {
        val startMs = currentTimeMillis()
        try {
            withTimeout(latencyTimeout) {
                onDeviceClient.chat(history).collect { emit(it) }
            }
            recordLatency(currentTimeMillis() - startMs)
        } catch (@Suppress("SwallowedException") e: TimeoutCancellationException) {
            recordLatency(currentTimeMillis() - startMs)
            recordCost()
            cloudClient.chat(history).collect { emit(it) }
        }
    }

    private suspend fun chatOnceFromPrimary(
        decision: RoutingDecision,
        history: ConversationHistory,
    ): String {
        if (decision.client === onDeviceClient && isAdaptiveWithTimeout) {
            return chatOnceWithTimeout(history)
        }
        if (decision.client === cloudClient) recordCost()
        return decision.client.chatOnce(history)
    }

    private suspend fun chatOnceWithTimeout(history: ConversationHistory): String {
        val startMs = currentTimeMillis()
        return try {
            val result = withTimeout(latencyTimeout) { onDeviceClient.chatOnce(history) }
            recordLatency(currentTimeMillis() - startMs)
            result
        } catch (@Suppress("SwallowedException") e: TimeoutCancellationException) {
            recordLatency(currentTimeMillis() - startMs)
            recordCost()
            cloudClient.chatOnce(history)
        }
    }

    private suspend fun FlowCollector<String>.fallbackChat(
        decision: RoutingDecision,
        history: ConversationHistory,
        error: Exception,
    ) {
        if (decision.client === onDeviceClient) {
            recordCost()
            cloudClient.chat(history).collect { emit(it) }
        } else {
            throw error
        }
    }

    private fun recordLatency(ms: Long) {
        if (ms > 0L) adaptiveRouter?.latencyTracker?.record(ms)
    }

    private fun recordCost() {
        adaptiveRouter?.costTracker?.recordRequest()
    }

    private fun errorState(cause: Throwable): LlmState =
        LlmState.Error(cause.message ?: "Hybrid request failed")
}

/**
 * Creates a [HybridLlmClient] with adaptive routing support.
 */
internal fun createAdaptiveHybridClient(
    onDeviceClient: LlmClient,
    cloudClient: LlmClient,
    strategy: RoutingStrategy.Adaptive,
    thermalStateProvider: StateFlow<ThermalState>,
): HybridLlmClient {
    val intentClassifier = IntentClassifier()
    val deps = AdaptiveRouterDeps(
        onDeviceClient = onDeviceClient,
        cloudClient = cloudClient,
        strategy = strategy,
        intentClassifier = intentClassifier,
        costTracker = CostTracker(
            dailyBudget = strategy.dailyCostBudget,
            costPerRequest = strategy.costPerCloudRequest,
        ),
        latencyTracker = LatencyTracker(),
    )
    return HybridLlmClient(
        onDeviceClient = onDeviceClient,
        cloudClient = cloudClient,
        routingStrategy = strategy,
        intentClassifier = intentClassifier,
        adaptiveRouter = AdaptiveRouter(deps = deps, thermalStateProvider = thermalStateProvider),
    )
}
