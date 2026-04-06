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
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Stress and reconnection tests for VTube Studio client.
 *
 * Tests rapid parameter injection, token persistence across reconnections,
 * and error recovery scenarios.
 */
class VtsStressTest {

    private val basePort = 19201

    private fun createHttpClient(): HttpClient = HttpClient(OkHttp) {
        install(WebSockets)
    }

    @Test
    fun rapidParameterInjection() = runTest {
        val serverJob = SupervisorJob()
        val serverScope = CoroutineScope(serverJob)
        val server = MockVtsServer(port = basePort)
        server.start(serverScope)
        delay(SETUP_DELAY)

        val httpClient = createHttpClient()
        val config = VtsConfig(
            host = "127.0.0.1",
            port = basePort,
            pluginName = "StressTest",
            pluginDeveloper = "TestDev",
        )
        val client = VtsClient(config, httpClient)

        try {
            client.connect(this)
            client.authenticate()

            val burstCount = 100
            for (i in 0 until burstCount) {
                val params = listOf(
                    ParameterValue(id = "FaceAngleX", value = i.toFloat()),
                    ParameterValue(id = "MouthOpen", value = (i % 101).toFloat() / 100f),
                )
                client.injectParameters(params)
            }
            delay(SETTLE_DELAY)

            assertEquals(burstCount, server.injectCount)
        } finally {
            client.disconnect()
            server.stop()
            httpClient.close()
            serverJob.cancel()
        }
    }

    @Test
    fun tokenPersistenceWithSavedToken() = runTest {
        val port = basePort + 1
        val serverJob = SupervisorJob()
        val serverScope = CoroutineScope(serverJob)
        val server = MockVtsServer(port = port)
        server.start(serverScope)
        delay(SETUP_DELAY)

        val httpClient = createHttpClient()

        // Simulate first session: get token
        val config1 = VtsConfig(
            host = "127.0.0.1",
            port = port,
            pluginName = "TokenTest",
            pluginDeveloper = "TestDev",
        )
        val client1 = VtsClient(config1, httpClient)
        client1.connect(this)
        val token = client1.authenticate()
        assertNotNull(token)
        assertEquals("mock-token-12345", token)
        client1.disconnect()

        // Simulate second session on same server: reuse token
        val config2 = VtsConfig(
            host = "127.0.0.1",
            port = port,
            pluginName = "TokenTest",
            pluginDeveloper = "TestDev",
            authToken = token,
        )
        val client2 = VtsClient(config2, httpClient)
        try {
            client2.connect(this)
            val reusedToken = client2.authenticate()
            assertEquals(token, reusedToken)
        } finally {
            client2.disconnect()
            server.stop()
            httpClient.close()
            serverJob.cancel()
        }
    }

    @Test
    fun authenticateWithRetryExhaustsRetriesOnPersistentFailure() = runTest {
        val port = basePort + 2
        val serverJob = SupervisorJob()
        val serverScope = CoroutineScope(serverJob)
        val server = MockVtsServer(port = port, acceptAuth = false)
        server.start(serverScope)
        delay(SETUP_DELAY)

        val httpClient = createHttpClient()
        val config = VtsConfig(
            host = "127.0.0.1",
            port = port,
            pluginName = "RetryTest",
            pluginDeveloper = "TestDev",
        )
        val client = VtsClient(config, httpClient)

        try {
            client.connect(this)
            // Auth denied throws VtsApiException (error response, not VtsAuthException)
            // because MockVtsServer returns an API error for token request denial
            assertFailsWith<VtsApiException> {
                client.authenticateWithRetry(maxRetries = 1, initialDelayMs = 50L)
            }
        } finally {
            client.disconnect()
            server.stop()
            httpClient.close()
            serverJob.cancel()
        }
    }

    @Test
    fun multipleConnectDisconnectCycles() = runTest {
        val port = basePort + 3
        val serverJob = SupervisorJob()
        val serverScope = CoroutineScope(serverJob)
        val server = MockVtsServer(port = port)
        server.start(serverScope)
        delay(SETUP_DELAY)

        val httpClient = createHttpClient()
        val config = VtsConfig(
            host = "127.0.0.1",
            port = port,
            pluginName = "CycleTest",
            pluginDeveloper = "TestDev",
        )

        try {
            val cycles = 5
            for (cycle in 0 until cycles) {
                val client = VtsClient(config, httpClient)
                client.connect(this)
                client.authenticate()
                client.injectParameters(
                    listOf(ParameterValue(id = "FaceAngleX", value = cycle.toFloat())),
                )
                client.disconnect()
            }
            delay(SETTLE_DELAY)
            assertEquals(cycles, server.injectCount)
        } finally {
            server.stop()
            httpClient.close()
            serverJob.cancel()
        }
    }

    @Test
    fun releaseIsIdempotent() = runTest {
        val port = basePort + 4
        val serverJob = SupervisorJob()
        val serverScope = CoroutineScope(serverJob)
        val server = MockVtsServer(port = port)
        server.start(serverScope)
        delay(SETUP_DELAY)

        val httpClient = createHttpClient()
        val config = VtsConfig(
            host = "127.0.0.1",
            port = port,
            pluginName = "ReleaseTest",
            pluginDeveloper = "TestDev",
        )
        val client = VtsClient(config, httpClient)

        try {
            client.connect(this)
            client.authenticate()
            // Calling release multiple times should not throw
            client.release()
            client.release()
            client.release()
            assertTrue(true)
        } finally {
            server.stop()
            httpClient.close()
            serverJob.cancel()
        }
    }
}

private const val SETUP_DELAY = 1000L
private const val SETTLE_DELAY = 500L
