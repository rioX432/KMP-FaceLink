package io.github.kmpfacelink.vts

import io.ktor.client.HttpClient
import io.ktor.client.plugins.websocket.WebSockets
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class VtsClientTest {

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

    @Test
    fun initialStateIsDisconnected() {
        val client = VtsClient()
        assertFalse(client.isConnected)
    }

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
    fun releaseWithExternalClientDoesNotCloseIt() {
        val httpClient = HttpClient { install(WebSockets) }
        val client = VtsClient(httpClient = httpClient)
        client.release()
        // httpClient should still be usable after VtsClient release
        assertTrue(httpClient.engine.config.toString().isNotEmpty())
        httpClient.close()
    }

    @Test
    fun isConnectedReturnsFalseAfterRelease() {
        val client = VtsClient()
        client.release()
        assertFalse(client.isConnected)
    }
}

private const val MULTI_RELEASE_COUNT = 10
