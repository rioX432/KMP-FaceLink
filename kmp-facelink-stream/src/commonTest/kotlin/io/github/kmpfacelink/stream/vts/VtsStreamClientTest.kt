package io.github.kmpfacelink.stream.vts

import io.github.kmpfacelink.stream.FakeWebSocketProvider
import io.github.kmpfacelink.stream.StreamState
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class VtsStreamClientTest {

    private fun createClient(
        token: String? = null,
        onTokenReceived: (suspend (String) -> Unit)? = null,
    ): Pair<VtsStreamClient, FakeWebSocketProvider> {
        val ws = FakeWebSocketProvider()
        val config = VtsConfig(
            pluginName = "TestPlugin",
            pluginDeveloper = "TestDev",
            authToken = token,
            onTokenReceived = onTokenReceived,
            reconnectPolicy = VtsReconnectPolicy.NONE,
        )
        val dispatcher = UnconfinedTestDispatcher()
        return VtsStreamClient(config, ws, dispatcher) to ws
    }

    @Test
    fun initialStateIsDisconnected() {
        val (client, _) = createClient()
        assertEquals(StreamState.Disconnected, client.state.value)
        client.release()
    }

    @Test
    fun authFlowWithNewTokenSendsTokenRequest() = runTest {
        val receivedTokens = mutableListOf<String>()
        val (client, ws) = createClient(
            onTokenReceived = { receivedTokens.add(it) },
        )

        // connect() launches a background job that enters ws.connect (which suspends on channel)
        client.connect()
        yield()

        // Trigger authenticate (sends token request via the ws)
        client.authenticate()

        assertTrue(
            ws.sentMessages.any { it.contains("AuthenticationTokenRequest") },
            "Should send AuthenticationTokenRequest",
        )

        // Simulate VTS sending back a token
        ws.receiveFromServer(
            buildTokenResponse("new-token-xyz"),
        )
        yield()

        assertEquals(listOf("new-token-xyz"), receivedTokens)
        assertTrue(
            ws.sentMessages.any {
                it.contains("AuthenticationRequest") && it.contains("new-token-xyz")
            },
            "Should send AuthenticationRequest with new token",
        )

        // Simulate successful auth
        ws.receiveFromServer(buildAuthSuccessResponse())
        yield()

        assertEquals(StreamState.Connected, client.state.value)

        ws.simulateClose()
        client.release()
    }

    @Test
    fun authFlowWithSavedTokenSkipsTokenRequest() = runTest {
        val (client, ws) = createClient(token = "saved-token-123")

        client.connect()
        yield()

        client.authenticate()

        assertTrue(
            ws.sentMessages.none { it.contains("AuthenticationTokenRequest") },
            "Should not request new token when saved token exists",
        )
        assertTrue(
            ws.sentMessages.any { it.contains("AuthenticationRequest") },
            "Should send AuthenticationRequest",
        )
        assertTrue(
            ws.sentMessages.any { it.contains("saved-token-123") },
            "Should include saved token",
        )

        ws.simulateClose()
        client.release()
    }

    @Test
    fun sendParametersIgnoredWhenDisconnected() = runTest {
        val (client, ws) = createClient()

        client.sendParameters(mapOf("test" to 1.0f), faceFound = true)

        assertTrue(ws.sentMessages.isEmpty(), "Should not send when disconnected")
        client.release()
    }

    @Test
    fun sendParametersWhenConnected() = runTest {
        val (client, ws) = createClient()

        client.connect()
        yield()

        // Authenticate
        client.authenticate()
        ws.receiveFromServer(buildTokenResponse("token"))
        yield()
        ws.receiveFromServer(buildAuthSuccessResponse())
        yield()

        assertEquals(StreamState.Connected, client.state.value)

        client.sendParameters(mapOf("eyeBlinkLeft" to 0.5f), faceFound = true)

        assertTrue(
            ws.sentMessages.any { it.contains("InjectParameterDataRequest") },
            "Should send InjectParameterDataRequest",
        )

        ws.simulateClose()
        client.release()
    }

    @Test
    fun apiErrorSetsErrorState() = runTest {
        val (client, ws) = createClient()

        client.connect()
        yield()

        ws.receiveFromServer(
            """
            {
                "apiName": "VTubeStudioPublicAPI",
                "apiVersion": "1.0",
                "timestamp": 0,
                "requestID": "test",
                "messageType": "APIError",
                "data": {"errorID": 50, "message": "Access denied"}
            }
            """.trimIndent(),
        )
        yield()

        val state = client.state.value
        assertTrue(state is StreamState.Error, "Expected Error state, got $state")
        assertEquals("Access denied", (state as StreamState.Error).message)

        ws.simulateClose()
        client.release()
    }

    private fun buildTokenResponse(token: String): String = """
        {
            "apiName": "VTubeStudioPublicAPI",
            "apiVersion": "1.0",
            "timestamp": 0,
            "requestID": "test",
            "messageType": "AuthenticationTokenResponse",
            "data": {"authenticationToken": "$token"}
        }
    """.trimIndent()

    private fun buildAuthSuccessResponse(): String = """
        {
            "apiName": "VTubeStudioPublicAPI",
            "apiVersion": "1.0",
            "timestamp": 0,
            "requestID": "test",
            "messageType": "AuthenticationResponse",
            "data": {"authenticated": true, "reason": "OK"}
        }
    """.trimIndent()
}
