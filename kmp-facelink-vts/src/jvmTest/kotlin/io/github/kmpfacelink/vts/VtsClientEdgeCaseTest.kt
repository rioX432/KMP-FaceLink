package io.github.kmpfacelink.vts

import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.websocket.WebSockets
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Integration tests for VtsClient edge cases not covered by VtsClientIntegrationTest:
 * - isConnected state transitions
 * - injectParameters with faceFound=false and mode="add"
 * - getPostProcessingEffects with effectIdFilter
 * - setPostProcessingValues with setAllOtherToDefault=true
 * - disconnect() clears session and isConnected reflects it
 * - release() after connect closes the connection
 */
class VtsClientEdgeCaseTest {

    private val basePort = 19401

    private fun createHttpClient(): HttpClient = HttpClient(OkHttp) {
        install(WebSockets)
    }

    // --- isConnected state transitions ---

    @Test
    fun isConnectedTrueAfterConnect() = runTest {
        val port = basePort
        val serverJob = SupervisorJob()
        val server = MockVtsServer(port = port)
        server.start(CoroutineScope(serverJob))
        delay(SETUP_DELAY)

        val httpClient = createHttpClient()
        val config = VtsConfig(
            host = "127.0.0.1",
            port = port,
            pluginName = "EdgeCase",
            pluginDeveloper = "TestDev",
        )
        val client = VtsClient(config, httpClient)

        try {
            assertFalse(client.isConnected, "Should not be connected before connect()")
            client.connect(this)
            assertTrue(client.isConnected, "Should be connected after connect()")
        } finally {
            client.disconnect()
            server.stop()
            httpClient.close()
            serverJob.cancel()
        }
    }

    @Test
    fun isConnectedFalseAfterDisconnect() = runTest {
        val port = basePort + 1
        val serverJob = SupervisorJob()
        val server = MockVtsServer(port = port)
        server.start(CoroutineScope(serverJob))
        delay(SETUP_DELAY)

        val httpClient = createHttpClient()
        val config = VtsConfig(
            host = "127.0.0.1",
            port = port,
            pluginName = "EdgeCase",
            pluginDeveloper = "TestDev",
        )
        val client = VtsClient(config, httpClient)

        try {
            client.connect(this)
            assertTrue(client.isConnected)
            client.disconnect()
            assertFalse(client.isConnected, "Should not be connected after disconnect()")
        } finally {
            server.stop()
            httpClient.close()
            serverJob.cancel()
        }
    }

    @Test
    fun isConnectedFalseAfterRelease() = runTest {
        val port = basePort + 2
        val serverJob = SupervisorJob()
        val server = MockVtsServer(port = port)
        server.start(CoroutineScope(serverJob))
        delay(SETUP_DELAY)

        val httpClient = createHttpClient()
        val config = VtsConfig(
            host = "127.0.0.1",
            port = port,
            pluginName = "EdgeCase",
            pluginDeveloper = "TestDev",
        )
        val client = VtsClient(config, httpClient)

        try {
            client.connect(this)
            assertTrue(client.isConnected)
            client.release()
            assertFalse(client.isConnected, "Should not be connected after release()")
        } finally {
            server.stop()
            httpClient.close()
            serverJob.cancel()
        }
    }

    // --- injectParameters with faceFound=false ---

    @Test
    fun injectParametersWithFaceFoundFalse() = runTest {
        val port = basePort + 3
        val serverJob = SupervisorJob()
        val server = MockVtsServer(port = port)
        server.start(CoroutineScope(serverJob))
        delay(SETUP_DELAY)

        val httpClient = createHttpClient()
        val config = VtsConfig(
            host = "127.0.0.1",
            port = port,
            pluginName = "EdgeCase",
            pluginDeveloper = "TestDev",
        )
        val client = VtsClient(config, httpClient)

        try {
            client.connect(this)
            client.authenticate()
            // faceFound=false signals that no face is currently detected
            client.injectParameters(
                parameters = listOf(ParameterValue(id = "FaceAngleX", value = 0f)),
                faceFound = false,
            )
            delay(SETTLE_DELAY)
            assertEquals(1, server.injectCount)
        } finally {
            client.disconnect()
            server.stop()
            httpClient.close()
            serverJob.cancel()
        }
    }

    // --- injectParameters with mode="add" ---

    @Test
    fun injectParametersWithModeAdd() = runTest {
        val port = basePort + 4
        val serverJob = SupervisorJob()
        val server = MockVtsServer(port = port)
        server.start(CoroutineScope(serverJob))
        delay(SETUP_DELAY)

        val httpClient = createHttpClient()
        val config = VtsConfig(
            host = "127.0.0.1",
            port = port,
            pluginName = "EdgeCase",
            pluginDeveloper = "TestDev",
        )
        val client = VtsClient(config, httpClient)

        try {
            client.connect(this)
            client.authenticate()
            client.injectParameters(
                parameters = listOf(ParameterValue(id = "MouthOpen", value = 0.3f)),
                mode = "add",
            )
            delay(SETTLE_DELAY)
            assertEquals(1, server.injectCount)
        } finally {
            client.disconnect()
            server.stop()
            httpClient.close()
            serverJob.cancel()
        }
    }

    // --- injectParameters with weight parameter ---

    @Test
    fun injectParametersWithCustomWeight() = runTest {
        val port = basePort + 5
        val serverJob = SupervisorJob()
        val server = MockVtsServer(port = port)
        server.start(CoroutineScope(serverJob))
        delay(SETUP_DELAY)

        val httpClient = createHttpClient()
        val config = VtsConfig(
            host = "127.0.0.1",
            port = port,
            pluginName = "EdgeCase",
            pluginDeveloper = "TestDev",
        )
        val client = VtsClient(config, httpClient)

        try {
            client.connect(this)
            client.authenticate()
            client.injectParameters(
                parameters = listOf(ParameterValue(id = "MouthOpen", value = 0.5f, weight = 0.5f)),
            )
            delay(SETTLE_DELAY)
            assertEquals(1, server.injectCount)
            // Verify the weight was forwarded correctly
            val injected = server.lastInjectedParams.find { it.id == "MouthOpen" }
            assertEquals(0.5f, injected?.weight)
        } finally {
            client.disconnect()
            server.stop()
            httpClient.close()
            serverJob.cancel()
        }
    }

    // --- injectParameters with empty list ---

    @Test
    fun injectEmptyParameterListSucceeds() = runTest {
        val port = basePort + 6
        val serverJob = SupervisorJob()
        val server = MockVtsServer(port = port)
        server.start(CoroutineScope(serverJob))
        delay(SETUP_DELAY)

        val httpClient = createHttpClient()
        val config = VtsConfig(
            host = "127.0.0.1",
            port = port,
            pluginName = "EdgeCase",
            pluginDeveloper = "TestDev",
        )
        val client = VtsClient(config, httpClient)

        try {
            client.connect(this)
            client.authenticate()
            // Empty list is valid per the VTS API
            client.injectParameters(parameters = emptyList())
            delay(SETTLE_DELAY)
            assertEquals(1, server.injectCount)
        } finally {
            client.disconnect()
            server.stop()
            httpClient.close()
            serverJob.cancel()
        }
    }

    // --- getPostProcessingEffects with effectIdFilter ---

    @Test
    fun getPostProcessingEffectsWithFilter() = runTest {
        val port = basePort + 7
        val serverJob = SupervisorJob()
        val server = MockVtsServer(port = port)
        server.start(CoroutineScope(serverJob))
        delay(SETUP_DELAY)

        val httpClient = createHttpClient()
        val config = VtsConfig(
            host = "127.0.0.1",
            port = port,
            pluginName = "EdgeCase",
            pluginDeveloper = "TestDev",
        )
        val client = VtsClient(config, httpClient)

        try {
            client.connect(this)
            client.authenticate()
            // Filtered request — server may return a subset
            val state = client.getPostProcessingEffects(
                effectIdFilter = listOf("ChromaticAberration"),
            )
            assertTrue(state.isSupported)
        } finally {
            client.disconnect()
            server.stop()
            httpClient.close()
            serverJob.cancel()
        }
    }

    // --- setPostProcessingValues with setAllOtherToDefault=true ---

    @Test
    fun setPostProcessingValuesWithResetToDefault() = runTest {
        val port = basePort + 8
        val serverJob = SupervisorJob()
        val server = MockVtsServer(port = port)
        server.start(CoroutineScope(serverJob))
        delay(SETUP_DELAY)

        val httpClient = createHttpClient()
        val config = VtsConfig(
            host = "127.0.0.1",
            port = port,
            pluginName = "EdgeCase",
            pluginDeveloper = "TestDev",
        )
        val client = VtsClient(config, httpClient)

        try {
            client.connect(this)
            client.authenticate()
            client.setPostProcessingValues(
                values = listOf(PostProcessingValue("Bloom_Strength", "0.5")),
                setAllOtherToDefault = true,
            )
            // Server accepted the request if no exception was thrown
            assertTrue(true)
        } finally {
            client.disconnect()
            server.stop()
            httpClient.close()
            serverJob.cancel()
        }
    }

    // --- setPostProcessingValues with postProcessingOn=false ---

    @Test
    fun setPostProcessingValuesWithEffectsOff() = runTest {
        val port = basePort + 9
        val serverJob = SupervisorJob()
        val server = MockVtsServer(port = port)
        server.start(CoroutineScope(serverJob))
        delay(SETUP_DELAY)

        val httpClient = createHttpClient()
        val config = VtsConfig(
            host = "127.0.0.1",
            port = port,
            pluginName = "EdgeCase",
            pluginDeveloper = "TestDev",
        )
        val client = VtsClient(config, httpClient)

        try {
            client.connect(this)
            client.authenticate()
            client.setPostProcessingValues(
                values = emptyList(),
                postProcessingOn = false,
            )
            assertTrue(true)
        } finally {
            client.disconnect()
            server.stop()
            httpClient.close()
            serverJob.cancel()
        }
    }

    // --- createParameter with boundary values ---

    @Test
    fun createParameterWithNegativeRange() = runTest {
        val port = basePort + 10
        val serverJob = SupervisorJob()
        val server = MockVtsServer(port = port)
        server.start(CoroutineScope(serverJob))
        delay(SETUP_DELAY)

        val httpClient = createHttpClient()
        val config = VtsConfig(
            host = "127.0.0.1",
            port = port,
            pluginName = "EdgeCase",
            pluginDeveloper = "TestDev",
        )
        val client = VtsClient(config, httpClient)

        try {
            client.connect(this)
            client.authenticate()
            client.createParameter(
                name = "NegRangeParam",
                explanation = "Parameter with negative range",
                min = -1f,
                max = 1f,
                defaultValue = 0f,
            )
            assertTrue(true)
        } finally {
            client.disconnect()
            server.stop()
            httpClient.close()
            serverJob.cancel()
        }
    }

    // --- disconnect() clears pending state ---

    @Test
    fun secondConnectDisconnectsFirst() = runTest {
        // Arrange: connect once, then connect again — the second connect should
        // cancel the first connection job before establishing the new one.
        val port = basePort + 11
        val serverJob = SupervisorJob()
        val server = MockVtsServer(port = port)
        server.start(CoroutineScope(serverJob))
        delay(SETUP_DELAY)

        val httpClient = createHttpClient()
        val config = VtsConfig(
            host = "127.0.0.1",
            port = port,
            pluginName = "EdgeCase",
            pluginDeveloper = "TestDev",
        )
        val client = VtsClient(config, httpClient)

        try {
            client.connect(this)
            assertTrue(client.isConnected)

            // Second connect() cancels the first and re-establishes
            client.connect(this)
            assertTrue(client.isConnected, "Should still be connected after reconnect via connect()")
        } finally {
            client.disconnect()
            server.stop()
            httpClient.close()
            serverJob.cancel()
        }
    }

    // --- Multiple parameter values in a single inject ---

    @Test
    fun injectMultipleParametersAtOnce() = runTest {
        val port = basePort + 12
        val serverJob = SupervisorJob()
        val server = MockVtsServer(port = port)
        server.start(CoroutineScope(serverJob))
        delay(SETUP_DELAY)

        val httpClient = createHttpClient()
        val config = VtsConfig(
            host = "127.0.0.1",
            port = port,
            pluginName = "EdgeCase",
            pluginDeveloper = "TestDev",
        )
        val client = VtsClient(config, httpClient)

        try {
            client.connect(this)
            client.authenticate()
            val params = listOf(
                ParameterValue(id = "FaceAngleX", value = 10f),
                ParameterValue(id = "FaceAngleY", value = -5f),
                ParameterValue(id = "FaceAngleZ", value = 3f),
                ParameterValue(id = "MouthOpen", value = 0.5f),
                ParameterValue(id = "EyeOpenLeft", value = 0.8f),
            )
            client.injectParameters(params)
            delay(SETTLE_DELAY)
            assertEquals(1, server.injectCount)
            assertEquals(MULTI_PARAM_COUNT, server.lastInjectedParams.size)
        } finally {
            client.disconnect()
            server.stop()
            httpClient.close()
            serverJob.cancel()
        }
    }
}

private const val SETUP_DELAY = 1000L
private const val SETTLE_DELAY = 200L
private const val MULTI_PARAM_COUNT = 5
