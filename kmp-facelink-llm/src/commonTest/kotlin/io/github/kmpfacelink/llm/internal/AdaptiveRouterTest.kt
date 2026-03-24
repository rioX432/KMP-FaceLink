package io.github.kmpfacelink.llm.internal

import io.github.kmpfacelink.llm.ConversationHistory
import io.github.kmpfacelink.llm.LlmClient
import io.github.kmpfacelink.llm.LlmState
import io.github.kmpfacelink.llm.RoutingStrategy
import io.github.kmpfacelink.model.ThermalState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flow
import kotlin.test.Test
import kotlin.test.assertEquals

class AdaptiveRouterTest {

    private val onDevice = StubLlmClient("on-device")
    private val cloud = StubLlmClient("cloud")
    private val thermalState = MutableStateFlow(ThermalState.NOMINAL)

    private fun createRouter(
        strategy: RoutingStrategy.Adaptive = RoutingStrategy.Adaptive(),
        costTracker: CostTracker = CostTracker(
            dailyBudget = strategy.dailyCostBudget,
            costPerRequest = strategy.costPerCloudRequest,
            timeProvider = { 0L },
        ),
        latencyTracker: LatencyTracker = LatencyTracker(),
    ): AdaptiveRouter = AdaptiveRouter(
        deps = AdaptiveRouterDeps(
            onDeviceClient = onDevice,
            cloudClient = cloud,
            strategy = strategy,
            intentClassifier = IntentClassifier(),
            costTracker = costTracker,
            latencyTracker = latencyTracker,
        ),
        thermalStateProvider = thermalState,
    )

    @Test
    fun routesToOnDeviceForSimpleQuery() {
        val router = createRouter()
        val history = ConversationHistory()
        history.addUser("Hi")

        val decision = router.route(history)

        assertEquals(onDevice, decision.client)
        assertEquals(RoutingReason.ON_DEVICE_PREFERRED, decision.reason)
    }

    @Test
    fun routesToCloudForComplexQuery() {
        val router = createRouter()
        val history = ConversationHistory()
        history.addUser("Please analyze and compare these approaches in detail")

        val decision = router.route(history)

        assertEquals(cloud, decision.client)
        assertEquals(RoutingReason.COMPLEXITY, decision.reason)
    }

    @Test
    fun routesToCloudWhenThermalThresholdExceeded() {
        thermalState.value = ThermalState.SERIOUS
        val router = createRouter()
        val history = ConversationHistory()
        history.addUser("Hi")

        val decision = router.route(history)

        assertEquals(cloud, decision.client)
        assertEquals(RoutingReason.THERMAL, decision.reason)
    }

    @Test
    fun routesToCloudWhenThermalCritical() {
        thermalState.value = ThermalState.CRITICAL
        val router = createRouter()
        val history = ConversationHistory()
        history.addUser("Hello")

        val decision = router.route(history)

        assertEquals(cloud, decision.client)
        assertEquals(RoutingReason.THERMAL, decision.reason)
    }

    @Test
    fun routesToOnDeviceWhenThermalFair() {
        thermalState.value = ThermalState.FAIR
        val router = createRouter()
        val history = ConversationHistory()
        history.addUser("Hi")

        val decision = router.route(history)

        assertEquals(onDevice, decision.client)
        assertEquals(RoutingReason.ON_DEVICE_PREFERRED, decision.reason)
    }

    @Test
    fun routesToCloudWhenLatencyExceedsTimeout() {
        val latencyTracker = LatencyTracker()
        latencyTracker.record(6000L)
        latencyTracker.record(7000L)

        val router = createRouter(latencyTracker = latencyTracker)
        val history = ConversationHistory()
        history.addUser("Hi")

        val decision = router.route(history)

        assertEquals(cloud, decision.client)
        assertEquals(RoutingReason.LATENCY, decision.reason)
    }

    @Test
    fun routesToOnDeviceWhenCostBudgetExhausted() {
        val costTracker = CostTracker(
            dailyBudget = 0.01,
            costPerRequest = 0.01,
            timeProvider = { 0L },
        )
        costTracker.recordRequest()

        thermalState.value = ThermalState.SERIOUS
        val router = createRouter(costTracker = costTracker)
        val history = ConversationHistory()
        history.addUser("Hi")

        val decision = router.route(history)

        assertEquals(onDevice, decision.client)
        assertEquals(RoutingReason.COST_BUDGET_EXHAUSTED, decision.reason)
    }

    @Test
    fun costBudgetTakesPriorityOverThermal() {
        val costTracker = CostTracker(
            dailyBudget = 0.003,
            costPerRequest = 0.003,
            timeProvider = { 0L },
        )
        costTracker.recordRequest()
        thermalState.value = ThermalState.CRITICAL

        val router = createRouter(costTracker = costTracker)
        val history = ConversationHistory()
        history.addUser("explain something complex")

        val decision = router.route(history)

        assertEquals(onDevice, decision.client)
        assertEquals(RoutingReason.COST_BUDGET_EXHAUSTED, decision.reason)
    }

    @Test
    fun thermalTakesPriorityOverLatency() {
        thermalState.value = ThermalState.SERIOUS
        val latencyTracker = LatencyTracker()
        latencyTracker.record(6000L)

        val router = createRouter(latencyTracker = latencyTracker)
        val history = ConversationHistory()
        history.addUser("Hi")

        val decision = router.route(history)

        assertEquals(cloud, decision.client)
        assertEquals(RoutingReason.THERMAL, decision.reason)
    }

    @Test
    fun customThermalThreshold() {
        thermalState.value = ThermalState.FAIR
        val strategy = RoutingStrategy.Adaptive(thermalThreshold = ThermalState.FAIR)
        val router = createRouter(strategy = strategy)
        val history = ConversationHistory()
        history.addUser("Hello")

        val decision = router.route(history)

        assertEquals(cloud, decision.client)
        assertEquals(RoutingReason.THERMAL, decision.reason)
    }

    private class StubLlmClient(private val name: String) : LlmClient {
        private val _state = MutableStateFlow<LlmState>(LlmState.Idle)
        override val state: StateFlow<LlmState> = _state

        override fun chat(history: ConversationHistory): Flow<String> = flow {
            emit(name)
        }

        override suspend fun chatOnce(history: ConversationHistory): String = name

        override fun release() {
            // No-op for test stub
        }

        override fun toString(): String = "StubLlmClient($name)"
    }
}
