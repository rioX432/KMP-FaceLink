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
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Tests for VTS client reconnection, token persistence across sessions,
 * and error recovery scenarios.
 */
class VtsReconnectionTest {

    private val basePort = 19801

    private fun createHttpClient(): HttpClient = HttpClient(OkHttp) {
        install(WebSockets)
    }

    @Test
    fun tokenPersistenceAcrossThreeSessions() = runTest {
        val port = basePort
        val serverJob = SupervisorJob()
        val server = MockVtsServer(port = port)
        server.start(CoroutineScope(serverJob))
        delay(SETUP_DELAY)

        val httpClient = createHttpClient()

        try {
            // Session 1: Get initial token
            val config1 = VtsConfig(
                host = "127.0.0.1",
                port = port,
                pluginName = "PersistTest",
                pluginDeveloper = "TestDev",
            )
            val client1 = VtsClient(config1, httpClient)
            client1.connect(this)
            val token = client1.authenticate()
            assertNotNull(token)
            client1.disconnect()

            // Session 2: Reuse token
            val config2 = config1.copy(authToken = token)
            val client2 = VtsClient(config2, httpClient)
            client2.connect(this)
            val token2 = client2.authenticate()
            assertEquals(token, token2)
            client2.disconnect()

            // Session 3: Reuse token again
            val client3 = VtsClient(config2, httpClient)
            client3.connect(this)
            val token3 = client3.authenticate()
            assertEquals(token, token3)
            client3.disconnect()
        } finally {
            server.stop()
            httpClient.close()
            serverJob.cancel()
        }
    }

    @Test
    fun releaseWithoutConnectIsSafe() = runTest {
        val httpClient = createHttpClient()
        val config = VtsConfig(
            host = "127.0.0.1",
            port = basePort + 1,
            pluginName = "SafeTest",
            pluginDeveloper = "TestDev",
        )
        val client = VtsClient(config, httpClient)

        try {
            assertFalse(client.isConnected)
            client.release()
            assertFalse(client.isConnected)
        } finally {
            httpClient.close()
        }
    }

    @Test
    fun disconnectDuringInjection() = runTest {
        val port = basePort + 2
        val serverJob = SupervisorJob()
        val server = MockVtsServer(port = port)
        server.start(CoroutineScope(serverJob))
        delay(SETUP_DELAY)

        val httpClient = createHttpClient()
        val config = VtsConfig(
            host = "127.0.0.1",
            port = port,
            pluginName = "DisconnTest",
            pluginDeveloper = "TestDev",
        )
        val client = VtsClient(config, httpClient)

        try {
            client.connect(this)
            client.authenticate()

            client.injectParameters(
                listOf(ParameterValue(id = "FaceAngleX", value = 10f)),
            )
            client.disconnect()
            assertFalse(client.isConnected)
        } finally {
            server.stop()
            httpClient.close()
            serverJob.cancel()
        }
    }

    @Test
    fun reconnectOnSameServer() = runTest {
        val port = basePort + 3
        val serverJob = SupervisorJob()
        val server = MockVtsServer(port = port)
        server.start(CoroutineScope(serverJob))
        delay(SETUP_DELAY)

        val httpClient = createHttpClient()

        try {
            // First connection: get token
            val config1 = VtsConfig(
                host = "127.0.0.1",
                port = port,
                pluginName = "ReconnTest",
                pluginDeveloper = "TestDev",
            )
            val client1 = VtsClient(config1, httpClient)
            client1.connect(this)
            val token = client1.authenticate()
            client1.injectParameters(
                listOf(ParameterValue(id = "FaceAngleX", value = 5f)),
            )
            client1.disconnect()

            // Reconnect with saved token on the same server
            val config2 = config1.copy(authToken = token)
            val client2 = VtsClient(config2, httpClient)
            client2.connect(this)
            val reusedToken = client2.authenticate()
            assertEquals(token, reusedToken)

            client2.injectParameters(
                listOf(ParameterValue(id = "FaceAngleX", value = 10f)),
            )
            delay(SETTLE_DELAY)
            assertEquals(2, server.injectCount)

            client2.disconnect()
        } finally {
            server.stop()
            httpClient.close()
            serverJob.cancel()
        }
    }

    @Test
    fun rapidConnectDisconnectCycles() = runTest {
        val port = basePort + 4
        val serverJob = SupervisorJob()
        val server = MockVtsServer(port = port)
        server.start(CoroutineScope(serverJob))
        delay(SETUP_DELAY)

        val httpClient = createHttpClient()
        val config = VtsConfig(
            host = "127.0.0.1",
            port = port,
            pluginName = "RapidTest",
            pluginDeveloper = "TestDev",
        )

        try {
            for (i in 0 until RAPID_CYCLES) {
                val client = VtsClient(config, httpClient)
                client.connect(this)
                assertTrue(client.isConnected, "Cycle $i: should be connected")
                client.disconnect()
                assertFalse(client.isConnected, "Cycle $i: should be disconnected")
            }
        } finally {
            server.stop()
            httpClient.close()
            serverJob.cancel()
        }
    }
}

private const val SETUP_DELAY = 1000L
private const val SETTLE_DELAY = 500L
private const val RAPID_CYCLES = 10
