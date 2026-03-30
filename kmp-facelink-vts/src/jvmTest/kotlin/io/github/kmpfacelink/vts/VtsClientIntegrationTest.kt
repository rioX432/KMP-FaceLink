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
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class VtsClientIntegrationTest {

    private val testPort = 18901

    private fun createHttpClient(): HttpClient = HttpClient(OkHttp) {
        install(WebSockets)
    }

    @Test
    fun authenticateAndInjectParameters() = runTest {
        val serverJob = SupervisorJob()
        val serverScope = CoroutineScope(serverJob)
        val server = MockVtsServer(port = testPort)
        server.start(serverScope)
        delay(500L)

        val httpClient = createHttpClient()
        val config = VtsConfig(
            host = "127.0.0.1",
            port = testPort,
            pluginName = "TestPlugin",
            pluginDeveloper = "TestDev",
        )
        val client = VtsClient(config, httpClient)

        try {
            client.connect(this)
            val token = client.authenticate()
            assertNotNull(token)
            assertEquals("mock-token-12345", token)

            val params = listOf(
                ParameterValue(id = "FaceAngleX", value = 10f),
                ParameterValue(id = "MouthOpen", value = 0.5f),
            )
            client.injectParameters(params)
            delay(100L)
            assertEquals(1, server.injectCount)
            assertEquals(2, server.lastInjectedParams.size)
        } finally {
            client.disconnect()
            server.stop()
            httpClient.close()
            serverJob.cancel()
        }
    }

    @Test
    fun authenticateWithExistingToken() = runTest {
        val serverJob = SupervisorJob()
        val serverScope = CoroutineScope(serverJob)
        val server = MockVtsServer(port = testPort + 1)
        server.start(serverScope)
        delay(500L)

        val httpClient = createHttpClient()
        val config = VtsConfig(
            host = "127.0.0.1",
            port = testPort + 1,
            pluginName = "TestPlugin",
            pluginDeveloper = "TestDev",
            authToken = "existing-token-xyz",
        )
        val client = VtsClient(config, httpClient)

        try {
            client.connect(this)
            val token = client.authenticate()
            assertEquals("existing-token-xyz", token)
        } finally {
            client.disconnect()
            server.stop()
            httpClient.close()
            serverJob.cancel()
        }
    }

    @Test
    fun authDeniedThrowsApiException() = runTest {
        val serverJob = SupervisorJob()
        val serverScope = CoroutineScope(serverJob)
        val server = MockVtsServer(port = testPort + 2, acceptAuth = false)
        server.start(serverScope)
        delay(500L)

        val httpClient = createHttpClient()
        val config = VtsConfig(
            host = "127.0.0.1",
            port = testPort + 2,
            pluginName = "TestPlugin",
            pluginDeveloper = "TestDev",
        )
        val client = VtsClient(config, httpClient)

        try {
            client.connect(this)
            assertFailsWith<VtsApiException> {
                client.authenticate()
            }
        } finally {
            client.disconnect()
            server.stop()
            httpClient.close()
            serverJob.cancel()
        }
    }

    @Test
    fun createCustomParameter() = runTest {
        val serverJob = SupervisorJob()
        val serverScope = CoroutineScope(serverJob)
        val server = MockVtsServer(port = testPort + 3)
        server.start(serverScope)
        delay(500L)

        val httpClient = createHttpClient()
        val config = VtsConfig(
            host = "127.0.0.1",
            port = testPort + 3,
            pluginName = "TestPlugin",
            pluginDeveloper = "TestDev",
        )
        val client = VtsClient(config, httpClient)

        try {
            client.connect(this)
            client.authenticate()
            client.createParameter(
                name = "MyCustomParam",
                explanation = "Test parameter",
                min = -10f,
                max = 10f,
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

    @Test
    fun getPostProcessingEffects() = runTest {
        val serverJob = SupervisorJob()
        val serverScope = CoroutineScope(serverJob)
        val server = MockVtsServer(port = testPort + 5)
        server.start(serverScope)
        delay(500L)

        val httpClient = createHttpClient()
        val config = VtsConfig(
            host = "127.0.0.1",
            port = testPort + 5,
            pluginName = "TestPlugin",
            pluginDeveloper = "TestDev",
        )
        val client = VtsClient(config, httpClient)

        try {
            client.connect(this)
            client.authenticate()
            val state = client.getPostProcessingEffects()
            assertTrue(state.isSupported)
            assertTrue(state.isActive)
            assertEquals("Default", state.activePreset)
        } finally {
            client.disconnect()
            server.stop()
            httpClient.close()
            serverJob.cancel()
        }
    }

    @Test
    fun setPostProcessingValues() = runTest {
        val serverJob = SupervisorJob()
        val serverScope = CoroutineScope(serverJob)
        val server = MockVtsServer(port = testPort + 6)
        server.start(serverScope)
        delay(500L)

        val httpClient = createHttpClient()
        val config = VtsConfig(
            host = "127.0.0.1",
            port = testPort + 6,
            pluginName = "TestPlugin",
            pluginDeveloper = "TestDev",
        )
        val client = VtsClient(config, httpClient)

        try {
            client.connect(this)
            client.authenticate()
            client.setPostProcessingValues(
                values = listOf(
                    PostProcessingValue("Backlight_Strength", "0.8"),
                    PostProcessingValue("ColorGrading_Saturation", "1.2"),
                ),
                fadeTime = 0.5,
            )
            // If no exception, the request was accepted
            assertTrue(true)
        } finally {
            client.disconnect()
            server.stop()
            httpClient.close()
            serverJob.cancel()
        }
    }

    @Test
    fun authenticateWithRetrySucceedsOnFirstAttempt() = runTest {
        val serverJob = SupervisorJob()
        val serverScope = CoroutineScope(serverJob)
        val server = MockVtsServer(port = testPort + 7)
        server.start(serverScope)
        delay(500L)

        val httpClient = createHttpClient()
        val config = VtsConfig(
            host = "127.0.0.1",
            port = testPort + 7,
            pluginName = "TestPlugin",
            pluginDeveloper = "TestDev",
        )
        val client = VtsClient(config, httpClient)

        try {
            client.connect(this)
            val token = client.authenticateWithRetry(maxRetries = 2, initialDelayMs = 100L)
            assertNotNull(token)
            assertEquals("mock-token-12345", token)
        } finally {
            client.disconnect()
            server.stop()
            httpClient.close()
            serverJob.cancel()
        }
    }

    @Test
    fun disconnectWithoutConnectClosesOwnedHttpClient() = runTest {
        // VtsClient created without an explicit HttpClient — it owns the internal one.
        // Calling disconnect() without a prior connect() must not throw and must
        // leave isConnected == false (exercising the ownsClient == true code path).
        val config = VtsConfig(
            host = "127.0.0.1",
            port = testPort + 4,
            pluginName = "TestPlugin",
            pluginDeveloper = "TestDev",
        )
        val client = VtsClient(config)

        assertFalse(client.isConnected)
        client.disconnect()
        assertFalse(client.isConnected)
    }
}
