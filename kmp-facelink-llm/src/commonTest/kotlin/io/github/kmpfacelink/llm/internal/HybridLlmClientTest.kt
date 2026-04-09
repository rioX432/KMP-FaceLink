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
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue

class HybridLlmClientTest {

    @Test
    fun alwaysOnDeviceRoutesToOnDevice() = runTest {
        val onDevice = FakeLlmClient(response = "on-device")
        val cloud = FakeLlmClient(response = "cloud")
        val client = HybridLlmClient(
            onDeviceClient = onDevice,
            cloudClient = cloud,
            routingStrategy = RoutingStrategy.AlwaysOnDevice,
        )

        val history = ConversationHistory()
        history.addUser("Explain quantum physics in detail")
        val result = client.chatOnce(history)

        assertEquals("on-device", result)
        assertTrue(onDevice.chatCalled)
    }

    @Test
    fun alwaysCloudRoutesToCloud() = runTest {
        val onDevice = FakeLlmClient(response = "on-device")
        val cloud = FakeLlmClient(response = "cloud")
        val client = HybridLlmClient(
            onDeviceClient = onDevice,
            cloudClient = cloud,
            routingStrategy = RoutingStrategy.AlwaysCloud,
        )

        val history = ConversationHistory()
        history.addUser("Hi")
        val result = client.chatOnce(history)

        assertEquals("cloud", result)
        assertTrue(cloud.chatCalled)
    }

    @Test
    fun autoRoutsSimpleQueryToOnDevice() = runTest {
        val onDevice = FakeLlmClient(response = "on-device")
        val cloud = FakeLlmClient(response = "cloud")
        val client = HybridLlmClient(
            onDeviceClient = onDevice,
            cloudClient = cloud,
            routingStrategy = RoutingStrategy.Auto(),
        )

        val history = ConversationHistory()
        history.addUser("Hi there")
        val result = client.chatOnce(history)

        assertEquals("on-device", result)
    }

    @Test
    fun autoRoutsComplexQueryToCloud() = runTest {
        val onDevice = FakeLlmClient(response = "on-device")
        val cloud = FakeLlmClient(response = "cloud")
        val client = HybridLlmClient(
            onDeviceClient = onDevice,
            cloudClient = cloud,
            routingStrategy = RoutingStrategy.Auto(),
        )

        val history = ConversationHistory()
        history.addUser("Please analyze and compare these two architectural approaches in detail")
        val result = client.chatOnce(history)

        assertEquals("cloud", result)
    }

    @Test
    fun fallbackToCloudOnDeviceFailure() = runTest {
        val onDevice = FakeLlmClient(shouldFail = true)
        val cloud = FakeLlmClient(response = "cloud-fallback")
        val client = HybridLlmClient(
            onDeviceClient = onDevice,
            cloudClient = cloud,
            routingStrategy = RoutingStrategy.AlwaysOnDevice,
        )

        val history = ConversationHistory()
        history.addUser("Hello")
        val result = client.chatOnce(history)

        assertEquals("cloud-fallback", result)
    }

    @Test
    fun chatStreamingCollectsAllChunks() = runTest {
        val onDevice = FakeLlmClient(response = "hello")
        val cloud = FakeLlmClient(response = "cloud")
        val client = HybridLlmClient(
            onDeviceClient = onDevice,
            cloudClient = cloud,
            routingStrategy = RoutingStrategy.AlwaysOnDevice,
        )

        val history = ConversationHistory()
        history.addUser("Hi")
        val chunks = client.chat(history).toList()

        assertEquals(listOf("hello"), chunks)
    }

    @Test
    fun chatStreamingFallsBackToCloud() = runTest {
        val onDevice = FakeLlmClient(shouldFail = true)
        val cloud = FakeLlmClient(response = "cloud-response")
        val client = HybridLlmClient(
            onDeviceClient = onDevice,
            cloudClient = cloud,
            routingStrategy = RoutingStrategy.AlwaysOnDevice,
        )

        val history = ConversationHistory()
        history.addUser("Hello")
        val chunks = client.chat(history).toList()

        assertEquals(listOf("cloud-response"), chunks)
    }

    @Test
    fun releaseBothClients() {
        val onDevice = FakeLlmClient(response = "on-device")
        val cloud = FakeLlmClient(response = "cloud")
        val client = HybridLlmClient(
            onDeviceClient = onDevice,
            cloudClient = cloud,
            routingStrategy = RoutingStrategy.AlwaysOnDevice,
        )

        client.release()

        assertTrue(onDevice.released)
        assertTrue(cloud.released)
    }

    // --- State transition tests ---

    @Test
    fun stateIsStreamingDuringChatThenIdleAfterCompletion() = runTest {
        val onDevice = FakeLlmClient(response = "hello")
        val client = HybridLlmClient(
            onDeviceClient = onDevice,
            cloudClient = FakeLlmClient(response = "cloud"),
            routingStrategy = RoutingStrategy.AlwaysOnDevice,
        )

        val history = ConversationHistory()
        history.addUser("Hi")
        // Collect to completion so onCompletion fires
        client.chat(history).toList()

        assertIs<LlmState.Idle>(client.state.value)
    }

    @Test
    fun stateIsErrorAfterBothClientsFail() = runTest {
        val onDevice = FakeLlmClient(shouldFail = true)
        val cloud = FakeLlmClient(shouldFail = true)
        val client = HybridLlmClient(
            onDeviceClient = onDevice,
            cloudClient = cloud,
            routingStrategy = RoutingStrategy.AlwaysOnDevice,
        )

        val history = ConversationHistory()
        history.addUser("Hello")
        // Suppress the exception; we care about state
        runCatching { client.chat(history).toList() }

        assertIs<LlmState.Error>(client.state.value)
    }

    // --- Error propagation tests ---

    @Test
    fun chatOnceCloudErrorPropagatesWhenCloudIsPrimary() = runTest {
        val onDevice = FakeLlmClient(response = "on-device")
        val cloud = FakeLlmClient(shouldFail = true)
        val client = HybridLlmClient(
            onDeviceClient = onDevice,
            cloudClient = cloud,
            routingStrategy = RoutingStrategy.AlwaysCloud,
        )

        val history = ConversationHistory()
        history.addUser("Hello")

        assertFailsWith<IllegalStateException> {
            client.chatOnce(history)
        }
    }

    @Test
    fun chatCloudErrorPropagatesWhenCloudIsPrimary() = runTest {
        val onDevice = FakeLlmClient(response = "on-device")
        val cloud = FakeLlmClient(shouldFail = true)
        val client = HybridLlmClient(
            onDeviceClient = onDevice,
            cloudClient = cloud,
            routingStrategy = RoutingStrategy.AlwaysCloud,
        )

        val history = ConversationHistory()
        history.addUser("Hello")

        assertFailsWith<IllegalStateException> {
            client.chat(history).toList()
        }
    }

    @Test
    fun chatOnceErrorPropagatesWhenBothFail() = runTest {
        val onDevice = FakeLlmClient(shouldFail = true)
        val cloud = FakeLlmClient(shouldFail = true)
        val client = HybridLlmClient(
            onDeviceClient = onDevice,
            cloudClient = cloud,
            routingStrategy = RoutingStrategy.AlwaysOnDevice,
        )

        val history = ConversationHistory()
        history.addUser("Hello")

        assertFailsWith<IllegalStateException> {
            client.chatOnce(history)
        }
    }

    // --- RoutingStrategy.Auto threshold tests ---

    @Test
    fun autoWithLowThresholdRoutesSimpleQueryToCloud() = runTest {
        val onDevice = FakeLlmClient(response = "on-device")
        val cloud = FakeLlmClient(response = "cloud")
        // Threshold of 0.0 means everything routes to cloud
        val client = HybridLlmClient(
            onDeviceClient = onDevice,
            cloudClient = cloud,
            routingStrategy = RoutingStrategy.Auto(complexityThreshold = 0.0f),
        )

        val history = ConversationHistory()
        history.addUser("Hi")
        val result = client.chatOnce(history)

        assertEquals("cloud", result)
    }

    @Test
    fun autoWithHighThresholdRoutesComplexQueryToOnDevice() = runTest {
        val onDevice = FakeLlmClient(response = "on-device")
        val cloud = FakeLlmClient(response = "cloud")
        // Threshold of 1.0 means nothing ever routes to cloud
        val client = HybridLlmClient(
            onDeviceClient = onDevice,
            cloudClient = cloud,
            routingStrategy = RoutingStrategy.Auto(complexityThreshold = 1.0f),
        )

        val history = ConversationHistory()
        history.addUser("Please analyze and compare these architectural approaches in detail")
        val result = client.chatOnce(history)

        assertEquals("on-device", result)
    }

    // --- RoutingStrategy.Adaptive tests ---

    @Test
    fun adaptiveWithNoRouterFallsBackToComplexityBased() = runTest {
        val onDevice = FakeLlmClient(response = "on-device")
        val cloud = FakeLlmClient(response = "cloud")
        // No adaptiveRouter provided — falls back to selectByComplexity
        val client = HybridLlmClient(
            onDeviceClient = onDevice,
            cloudClient = cloud,
            routingStrategy = RoutingStrategy.Adaptive(),
            adaptiveRouter = null,
        )

        val history = ConversationHistory()
        history.addUser("Hi")
        val result = client.chatOnce(history)

        assertEquals("on-device", result)
    }

    @Test
    fun adaptiveWithNoRouterRoutesComplexQueryToCloud() = runTest {
        val onDevice = FakeLlmClient(response = "on-device")
        val cloud = FakeLlmClient(response = "cloud")
        val client = HybridLlmClient(
            onDeviceClient = onDevice,
            cloudClient = cloud,
            routingStrategy = RoutingStrategy.Adaptive(),
            adaptiveRouter = null,
        )

        val history = ConversationHistory()
        history.addUser("Please analyze and compare these two approaches in detail")
        val result = client.chatOnce(history)

        assertEquals("cloud", result)
    }

    @Test
    fun adaptiveRouterThermalSignalRoutesForcesToCloud() = runTest {
        val onDevice = FakeLlmClient(response = "on-device")
        val cloud = FakeLlmClient(response = "cloud")
        val thermalState = MutableStateFlow(ThermalState.SERIOUS)
        val strategy = RoutingStrategy.Adaptive()
        val router = AdaptiveRouter(
            deps = AdaptiveRouterDeps(
                onDeviceClient = onDevice,
                cloudClient = cloud,
                strategy = strategy,
                intentClassifier = IntentClassifier(),
                costTracker = CostTracker(
                    dailyBudget = strategy.dailyCostBudget,
                    costPerRequest = strategy.costPerCloudRequest,
                    timeProvider = { 0L },
                ),
                latencyTracker = LatencyTracker(),
            ),
            thermalStateProvider = thermalState,
        )
        val client = HybridLlmClient(
            onDeviceClient = onDevice,
            cloudClient = cloud,
            routingStrategy = strategy,
            adaptiveRouter = router,
        )

        val history = ConversationHistory()
        history.addUser("Hi")
        val result = client.chatOnce(history)

        assertEquals("cloud", result)
    }

    @Test
    fun adaptiveRouterCostExhaustedForcesOnDevice() = runTest {
        val onDevice = FakeLlmClient(response = "on-device")
        val cloud = FakeLlmClient(response = "cloud")
        val thermalState = MutableStateFlow(ThermalState.SERIOUS)
        val strategy = RoutingStrategy.Adaptive()
        val costTracker = CostTracker(
            dailyBudget = 0.001,
            costPerRequest = 0.003,
            timeProvider = { 0L },
        )
        // Budget already exceeded before we even start
        costTracker.recordRequest()
        val router = AdaptiveRouter(
            deps = AdaptiveRouterDeps(
                onDeviceClient = onDevice,
                cloudClient = cloud,
                strategy = strategy,
                intentClassifier = IntentClassifier(),
                costTracker = costTracker,
                latencyTracker = LatencyTracker(),
            ),
            thermalStateProvider = thermalState,
        )
        val client = HybridLlmClient(
            onDeviceClient = onDevice,
            cloudClient = cloud,
            routingStrategy = strategy,
            adaptiveRouter = router,
        )

        val history = ConversationHistory()
        history.addUser("Hi")
        val result = client.chatOnce(history)

        // Cost budget exhausted overrides thermal — routes to on-device
        assertEquals("on-device", result)
    }

    // --- chat() streaming multi-chunk test ---

    @Test
    fun chatStreamingEmitsMultipleChunks() = runTest {
        val onDevice = MultiChunkFakeLlmClient(chunks = listOf("Hello", " ", "world"))
        val client = HybridLlmClient(
            onDeviceClient = onDevice,
            cloudClient = FakeLlmClient(response = "cloud"),
            routingStrategy = RoutingStrategy.AlwaysOnDevice,
        )

        val history = ConversationHistory()
        history.addUser("Hi")
        val chunks = client.chat(history).toList()

        assertEquals(listOf("Hello", " ", "world"), chunks)
    }

    /**
     * Fake LLM client for testing routing behavior.
     */
    private class FakeLlmClient(
        private val response: String = "",
        private val shouldFail: Boolean = false,
    ) : LlmClient {
        var chatCalled = false
        var released = false

        private val _state = MutableStateFlow<LlmState>(LlmState.Idle)
        override val state: StateFlow<LlmState> = _state

        override fun chat(history: ConversationHistory): Flow<String> = flow {
            chatCalled = true
            if (shouldFail) error("On-device inference failed")
            emit(response)
        }

        override suspend fun chatOnce(history: ConversationHistory): String {
            chatCalled = true
            if (shouldFail) error("On-device inference failed")
            return response
        }

        override fun release() {
            released = true
        }
    }

    private class MultiChunkFakeLlmClient(
        private val chunks: List<String>,
    ) : LlmClient {
        private val _state = MutableStateFlow<LlmState>(LlmState.Idle)
        override val state: StateFlow<LlmState> = _state

        override fun chat(history: ConversationHistory): Flow<String> = flow {
            for (chunk in chunks) emit(chunk)
        }

        override suspend fun chatOnce(history: ConversationHistory): String = chunks.joinToString("")

        override fun release() = Unit
    }
}
