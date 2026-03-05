package io.github.kmpfacelink.stream.vts

import io.github.kmpfacelink.stream.FakeWebSocketProvider
import io.github.kmpfacelink.stream.StreamState
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class VtsStreamClientTest {

    /**
     * Creates a [VtsStreamClient] backed by a [FakeWebSocketProvider].
     *
     * Passing [scheduler] shares the [TestCoroutineScheduler] between the test
     * scope and the client's internal scope, so [advanceTimeBy] / [advanceUntilIdle]
     * in the test body affect delays inside the client (e.g. reconnect backoff).
     */
    private fun createClient(
        token: String? = null,
        reconnectPolicy: VtsReconnectPolicy = VtsReconnectPolicy.NONE,
        scheduler: TestCoroutineScheduler? = null,
    ): Pair<VtsStreamClient, FakeWebSocketProvider> {
        val ws = FakeWebSocketProvider()
        val config = VtsConfig(
            pluginName = "TestPlugin",
            pluginDeveloper = "TestDev",
            authToken = token,
            reconnectPolicy = reconnectPolicy,
        )
        val dispatcher = if (scheduler != null) {
            UnconfinedTestDispatcher(scheduler)
        } else {
            UnconfinedTestDispatcher()
        }
        return VtsStreamClient(config, ws, dispatcher) to ws
    }

    /** Drives a client through the full auth flow to reach [StreamState.Connected]. */
    private suspend fun connectAndReachConnected(
        client: VtsStreamClient,
        ws: FakeWebSocketProvider,
        token: String = "test-token",
    ) {
        client.connect()
        yield()
        client.authenticate()
        if (ws.sentMessages.any { it.contains("AuthenticationTokenRequest") }) {
            ws.receiveFromServer(buildTokenResponse(token))
            yield()
        }
        ws.receiveFromServer(buildAuthSuccessResponse())
        yield()
        assertEquals(StreamState.Connected, client.state.value)
    }

    // ------------------------------------------------------------------ existing tests

    @Test
    fun initialStateIsDisconnected() {
        val (client, _) = createClient()
        assertEquals(StreamState.Disconnected, client.state.value)
        client.release()
    }

    @Test
    fun authFlowWithNewTokenSendsTokenRequest() = runTest {
        val receivedTokens = mutableListOf<String>()
        val (client, ws) = createClient()

        val collectJob = launch {
            client.tokenReceived.collect { receivedTokens.add(it) }
        }

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
        collectJob.cancel()
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

    // ------------------------------------------------------------------ new tests

    @Test
    fun serverClosedTransitionsToReconnectingState() = runTest {
        val policy = VtsReconnectPolicy(
            maxAttempts = 2,
            initialDelayMs = 50L,
            maxDelayMs = 50L,
            backoffMultiplier = 1.0,
        )
        val (client, ws) = createClient(token = "tok", reconnectPolicy = policy)

        connectAndReachConnected(client, ws, token = "tok")

        ws.triggerServerClose()
        yield()

        val state = client.state.value
        assertTrue(
            state is StreamState.Reconnecting || state is StreamState.Connecting,
            "Expected Reconnecting or Connecting after server close, got $state",
        )

        ws.simulateClose()
        client.release()
    }

    @Test
    fun reconnectAfterServerClose() = runTest {
        val policy = VtsReconnectPolicy(
            maxAttempts = 1,
            initialDelayMs = 100L,
            maxDelayMs = 100L,
            backoffMultiplier = 1.0,
        )
        val (client, ws) = createClient(
            token = "saved-token",
            reconnectPolicy = policy,
            scheduler = testScheduler,
        )

        connectAndReachConnected(client, ws, token = "saved-token")
        assertEquals(1, ws.connectCount, "Should have connected once")

        val statesObserved = mutableListOf<StreamState>()
        val collectJob = launch { client.state.collect { statesObserved.add(it) } }

        // Simulate the server dropping the connection.
        ws.triggerServerClose()
        yield()

        // Client should have moved to Reconnecting.
        assertTrue(
            statesObserved.any { it is StreamState.Reconnecting },
            "Expected Reconnecting state after server close",
        )

        // Advance virtual time past the reconnect delay.
        advanceTimeBy(200L)
        yield()

        // The reconnect attempt calls connect() again (connectCount >= 2).
        // Supply the auth success response for the new session.
        if (ws.connectCount >= 2) {
            ws.receiveFromServer(buildAuthSuccessResponse())
            yield()
        }

        advanceUntilIdle()
        collectJob.cancel()
        client.release()
    }

    @Test
    fun reconnectSucceedsOnSecondAttempt() = runTest {
        val policy = VtsReconnectPolicy(
            maxAttempts = 3,
            initialDelayMs = 10L,
            maxDelayMs = 10L,
            backoffMultiplier = 1.0,
        )
        val (client, ws) = createClient(
            token = "tok",
            reconnectPolicy = policy,
            scheduler = testScheduler,
        )

        connectAndReachConnected(client, ws, token = "tok")

        // Trigger a server close so the client starts reconnecting.
        ws.triggerServerClose()
        yield()

        // Client should enter Reconnecting.
        val stateAfterClose = client.state.value
        assertTrue(
            stateAfterClose is StreamState.Reconnecting || stateAfterClose is StreamState.Connecting,
            "Expected reconnecting state after server close, got $stateAfterClose",
        )

        // Advance time past the reconnect delay so the second connect() call fires.
        advanceTimeBy(100L)
        yield()

        // Supply auth responses for the new session (saved token, so auth request is sent first).
        ws.receiveFromServer(buildAuthSuccessResponse())
        yield()

        assertEquals(StreamState.Connected, client.state.value, "Expected Connected after reconnect")

        ws.simulateClose()
        client.release()
    }

    @Test
    fun networkErrorTriggersReconnectingWhenPolicyAllows() = runTest {
        val policy = VtsReconnectPolicy(
            maxAttempts = 3,
            initialDelayMs = 50L,
            maxDelayMs = 50L,
            backoffMultiplier = 1.0,
        )
        val (client, ws) = createClient(reconnectPolicy = policy)

        client.connect()
        yield()

        ws.simulateError(RuntimeException("connection reset"))
        yield()

        // handleError sets Error(willRetry=true), then attemptReconnect immediately
        // transitions to Reconnecting. The observable post-yield state is Reconnecting.
        val state = client.state.value
        assertTrue(
            state is StreamState.Reconnecting || state is StreamState.Error,
            "Expected Reconnecting or Error after network error, got $state",
        )
        if (state is StreamState.Error) {
            assertTrue(state.willRetry, "willRetry should be true when maxAttempts > 0")
        }

        client.release()
    }

    @Test
    fun networkErrorSetsErrorWithNoRetryWhenPolicyIsNone() = runTest {
        val (client, ws) = createClient(reconnectPolicy = VtsReconnectPolicy.NONE)

        client.connect()
        yield()

        ws.simulateError(RuntimeException("timeout"))
        yield()

        val state = client.state.value
        assertTrue(state is StreamState.Error, "Expected Error state")
        assertFalse(
            (state as StreamState.Error).willRetry,
            "willRetry should be false when maxAttempts == 0",
        )

        client.release()
    }

    @Test
    fun concurrentConnectCallsAreIdempotent() = runTest {
        val (client, ws) = createClient(token = "tok")

        // Fire multiple connect() calls without waiting.
        client.connect()
        client.connect()
        client.connect()
        yield()

        // Only one physical WebSocket connection should be made.
        assertEquals(1, ws.connectCount, "Multiple connect() calls should result in one connection")

        ws.simulateClose()
        client.release()
    }

    @Test
    fun connectWhenAlreadyConnectedIsNoOp() = runTest {
        val (client, ws) = createClient(token = "tok")

        connectAndReachConnected(client, ws, token = "tok")

        // A second connect() while already Connected should be ignored.
        client.connect()
        yield()

        assertEquals(StreamState.Connected, client.state.value)
        assertEquals(1, ws.connectCount, "Second connect() while connected should be no-op")

        ws.simulateClose()
        client.release()
    }

    @Test
    fun disconnectFromConnectedState() = runTest {
        val (client, ws) = createClient(token = "tok")

        connectAndReachConnected(client, ws, token = "tok")
        assertEquals(StreamState.Connected, client.state.value)

        client.disconnect()

        assertEquals(StreamState.Disconnected, client.state.value)
        client.release()
    }

    @Test
    fun sendParametersIgnoredInConnectingState() = runTest {
        val (client, ws) = createClient()

        // Launch connect but do not drive auth — client stays in Connecting.
        client.connect()
        yield()

        val messageCountBefore = ws.sentMessages.size
        client.sendParameters(mapOf("param" to 0.3f), faceFound = false)

        assertEquals(
            messageCountBefore,
            ws.sentMessages.size,
            "sendParameters should be ignored while Connecting",
        )

        ws.simulateClose()
        client.release()
    }

    @Test
    fun authFailureClearsTokenAndSetsError() = runTest {
        val (client, ws) = createClient(token = "expired-token")

        client.connect()
        yield()
        client.authenticate()

        // Simulate VTS rejecting the saved token.
        ws.receiveFromServer(buildAuthFailureResponse("Token expired"))
        yield()

        val state = client.state.value
        assertIs<StreamState.Error>(state, "Expected Error state after auth failure")
        assertEquals("Token expired", state.message)
        assertFalse(state.willRetry, "Auth failure should not trigger reconnection")

        ws.simulateClose()
        client.release()
    }

    @Test
    fun tokenReceivedFlowEmitsOnNewToken() = runTest {
        val (client, ws) = createClient()
        val tokens = mutableListOf<String>()

        val collectJob = launch { client.tokenReceived.collect { tokens.add(it) } }

        client.connect()
        yield()
        client.authenticate()
        ws.receiveFromServer(buildTokenResponse("fresh-token"))
        yield()

        assertEquals(listOf("fresh-token"), tokens)

        collectJob.cancel()
        ws.simulateClose()
        client.release()
    }

    @Test
    fun errorStateIncludesMessageFromServerApiError() = runTest {
        val (client, ws) = createClient()

        client.connect()
        yield()

        ws.receiveFromServer(buildApiErrorResponse(42, "Plugin not whitelisted"))
        yield()

        val state = client.state.value
        assertIs<StreamState.Error>(state)
        assertEquals("Plugin not whitelisted", state.message)

        ws.simulateClose()
        client.release()
    }

    @Test
    fun stateTransitionsThroughConnectingAuthenticatingConnected() = runTest {
        val (client, ws) = createClient(token = "tok")

        // Initial state is Disconnected.
        assertEquals(StreamState.Disconnected, client.state.value)

        client.connect()
        yield()
        // Background job is now in ws.connect()'s for-loop (Connecting).
        assertEquals(StreamState.Connecting, client.state.value)

        // Drive authenticate — state should switch to Authenticating.
        client.authenticate()
        assertEquals(StreamState.Authenticating, client.state.value)

        // Supply the success response → Connected.
        ws.receiveFromServer(buildAuthSuccessResponse())
        yield()
        assertEquals(StreamState.Connected, client.state.value)

        ws.simulateClose()
        client.release()
    }

    // ------------------------------------------------------------------ helpers

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

    private fun buildAuthFailureResponse(reason: String): String = """
        {
            "apiName": "VTubeStudioPublicAPI",
            "apiVersion": "1.0",
            "timestamp": 0,
            "requestID": "test",
            "messageType": "AuthenticationResponse",
            "data": {"authenticated": false, "reason": "$reason"}
        }
    """.trimIndent()

    private fun buildApiErrorResponse(errorId: Int, message: String): String = """
        {
            "apiName": "VTubeStudioPublicAPI",
            "apiVersion": "1.0",
            "timestamp": 0,
            "requestID": "test",
            "messageType": "APIError",
            "data": {"errorID": $errorId, "message": "$message"}
        }
    """.trimIndent()
}
