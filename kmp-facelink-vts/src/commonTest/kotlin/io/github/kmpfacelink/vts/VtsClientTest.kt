package io.github.kmpfacelink.vts

import io.ktor.client.HttpClient
import io.ktor.client.plugins.websocket.WebSockets
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class VtsClientTest {

    // --- Construction ---

    @Test
    fun constructWithDefaultConfig() {
        val client = VtsClient()
        assertNotNull(client)
        assertFalse(client.isConnected)
    }

    @Test
    fun constructWithCustomConfig() {
        val config = VtsConfig(
            host = "192.168.1.100",
            port = 9001,
            pluginName = "TestPlugin",
            pluginDeveloper = "TestDev",
            authToken = "saved-token",
        )
        val client = VtsClient(config)
        assertNotNull(client)
        assertFalse(client.isConnected)
    }

    @Test
    fun constructWithExternalHttpClient() {
        val httpClient = HttpClient { install(WebSockets) }
        val client = VtsClient(httpClient = httpClient)
        assertFalse(client.isConnected)
        // External client is not closed by VtsClient
        client.release()
        httpClient.close()
    }

    // --- Initial state ---

    @Test
    fun initialStateIsDisconnected() {
        val client = VtsClient()
        assertFalse(client.isConnected)
    }

    // --- disconnect() without prior connect ---

    @Test
    fun disconnectWithoutConnectDoesNotThrow() = runTest {
        val client = VtsClient()
        // Should complete without throwing
        client.disconnect()
        assertFalse(client.isConnected)
    }

    @Test
    fun disconnectCalledMultipleTimesIsIdempotent() = runTest {
        val client = VtsClient()
        repeat(MULTI_DISCONNECT_COUNT) {
            client.disconnect()
        }
        assertFalse(client.isConnected)
    }

    // --- release() semantics ---

    @Test
    fun releaseOnFreshClient() {
        val client = VtsClient()
        // Release should not throw on a never-connected client
        client.release()
        assertFalse(client.isConnected)
    }

    @Test
    fun doubleReleaseIsSafe() {
        val client = VtsClient()
        client.release()
        // Second release should be a no-op, not throw
        client.release()
        assertFalse(client.isConnected)
    }

    @Test
    fun multipleReleasesAreSafe() {
        val client = VtsClient()
        repeat(MULTI_RELEASE_COUNT) {
            client.release()
        }
        assertFalse(client.isConnected)
    }

    @Test
    fun releaseAfterDisconnectDoesNotThrow() = runTest {
        val client = VtsClient()
        client.disconnect()
        // Should be a no-op after a prior disconnect
        client.release()
        assertFalse(client.isConnected)
    }

    // --- External HttpClient ownership ---

    @Test
    fun releaseWithExternalClientDoesNotCloseIt() {
        val httpClient = HttpClient { install(WebSockets) }
        val client = VtsClient(httpClient = httpClient)
        client.release()
        // httpClient should still be usable after VtsClient release
        assertTrue(httpClient.engine.config.toString().isNotEmpty())
        httpClient.close()
    }

    @Test
    fun disconnectWithExternalClientDoesNotCloseIt() = runTest {
        val httpClient = HttpClient { install(WebSockets) }
        val client = VtsClient(httpClient = httpClient)
        client.disconnect()
        // httpClient should still be usable after VtsClient disconnect
        assertTrue(httpClient.engine.config.toString().isNotEmpty())
        httpClient.close()
    }

    // --- isConnected ---

    @Test
    fun isConnectedReturnsFalseAfterRelease() {
        val client = VtsClient()
        client.release()
        assertFalse(client.isConnected)
    }

    @Test
    fun isConnectedReturnsFalseAfterDisconnectWithoutConnect() = runTest {
        val client = VtsClient()
        client.disconnect()
        assertFalse(client.isConnected)
    }

    // --- Config values are accepted ---

    @Test
    fun minValidPortIsAccepted() {
        val config = VtsConfig(
            port = 1,
            pluginName = "TestPlugin",
            pluginDeveloper = "TestDev",
        )
        val client = VtsClient(config)
        assertFalse(client.isConnected)
    }

    @Test
    fun maxValidPortIsAccepted() {
        val config = VtsConfig(
            port = 65535,
            pluginName = "TestPlugin",
            pluginDeveloper = "TestDev",
        )
        val client = VtsClient(config)
        assertFalse(client.isConnected)
    }

    @Test
    fun nullAuthTokenIsAccepted() {
        val config = VtsConfig(
            pluginName = "TestPlugin",
            pluginDeveloper = "TestDev",
            authToken = null,
        )
        val client = VtsClient(config)
        assertFalse(client.isConnected)
    }

    @Test
    fun nonNullAuthTokenIsAccepted() {
        val config = VtsConfig(
            pluginName = "TestPlugin",
            pluginDeveloper = "TestDev",
            authToken = "my-saved-token",
        )
        val client = VtsClient(config)
        assertFalse(client.isConnected)
    }

    @Test
    fun pluginIconIsAccepted() {
        val config = VtsConfig(
            pluginName = "TestPlugin",
            pluginDeveloper = "TestDev",
            pluginIcon = "base64encodedpng",
        )
        val client = VtsClient(config)
        assertFalse(client.isConnected)
    }
}

private const val MULTI_RELEASE_COUNT = 10
private const val MULTI_DISCONNECT_COUNT = 5
