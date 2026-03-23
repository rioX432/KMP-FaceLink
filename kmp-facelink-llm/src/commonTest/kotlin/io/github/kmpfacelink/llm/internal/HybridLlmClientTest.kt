package io.github.kmpfacelink.llm.internal

import io.github.kmpfacelink.llm.ConversationHistory
import io.github.kmpfacelink.llm.LlmClient
import io.github.kmpfacelink.llm.LlmState
import io.github.kmpfacelink.llm.RoutingStrategy
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
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
}
