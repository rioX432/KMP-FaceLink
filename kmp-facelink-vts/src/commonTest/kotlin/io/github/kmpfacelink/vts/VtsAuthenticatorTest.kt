package io.github.kmpfacelink.vts

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs

class VtsAuthenticatorTest {

    // --- authenticate() happy path ---

    @Test
    fun authenticateWithSavedTokenSkipsTokenRequest() = runTest {
        // Arrange: config already has a token — transport should only receive the auth request
        val transport = RecordingTransport(
            authTokenResponse = tokenResponse("should-not-be-used"),
            authResponse = authResponse(authenticated = true),
        )
        val config = testConfig(authToken = "saved-token")
        val authenticator = VtsAuthenticator(config, transport)

        // Act
        val result = authenticator.authenticate()

        // Assert: returned token is the saved one, and token request was never sent
        assertEquals("saved-token", result)
        assertEquals(0, transport.tokenRequestCount)
        assertEquals(1, transport.authRequestCount)
    }

    @Test
    fun authenticateWithoutSavedTokenRequestsNewToken() = runTest {
        // Arrange: no existing token — transport issues both token request and auth request
        val transport = RecordingTransport(
            authTokenResponse = tokenResponse("fresh-token"),
            authResponse = authResponse(authenticated = true),
        )
        val config = testConfig(authToken = null)
        val authenticator = VtsAuthenticator(config, transport)

        // Act
        val result = authenticator.authenticate()

        // Assert: token came from the token-request response
        assertEquals("fresh-token", result)
        assertEquals(1, transport.tokenRequestCount)
        assertEquals(1, transport.authRequestCount)
    }

    @Test
    fun authenticateThrowsVtsAuthExceptionWhenDenied() = runTest {
        // Arrange: VTS returns authenticated=false
        val transport = RecordingTransport(
            authTokenResponse = tokenResponse("some-token"),
            authResponse = authResponse(authenticated = false, reason = "User denied"),
        )
        val config = testConfig(authToken = "some-token")
        val authenticator = VtsAuthenticator(config, transport)

        // Act & Assert
        val ex = assertFailsWith<VtsAuthException> {
            authenticator.authenticate()
        }
        assertEquals("User denied", ex.reason)
    }

    // --- authenticateWithRetry() success cases ---

    @Test
    fun authenticateWithRetrySucceedsOnFirstAttempt() = runTest {
        // Arrange
        val transport = RecordingTransport(
            authTokenResponse = tokenResponse("retry-token"),
            authResponse = authResponse(authenticated = true),
        )
        val config = testConfig(authToken = "retry-token")
        val authenticator = VtsAuthenticator(config, transport)

        // Act
        val result = authenticator.authenticateWithRetry(maxRetries = 2, initialDelayMs = 0)

        // Assert
        assertEquals("retry-token", result)
        assertEquals(1, transport.authRequestCount)
    }

    @Test
    fun authenticateWithRetrySucceedsOnSecondAttempt() = runTest {
        // Arrange: first attempt throws VtsApiException, second succeeds
        val transport = FailThenSucceedTransport(
            failCount = 1,
            successToken = "eventual-token",
        )
        val config = testConfig(authToken = "eventual-token")
        val authenticator = VtsAuthenticator(config, transport)

        // Act
        val result = authenticator.authenticateWithRetry(maxRetries = 2, initialDelayMs = 0)

        // Assert
        assertEquals("eventual-token", result)
        assertEquals(2, transport.callCount) // 1 failure + 1 success
    }

    @Test
    fun authenticateWithRetrySucceedsOnLastAllowedAttempt() = runTest {
        // Arrange: fails maxRetries times then succeeds on the final attempt
        val transport = FailThenSucceedTransport(
            failCount = 3,
            successToken = "last-chance-token",
        )
        val config = testConfig(authToken = "last-chance-token")
        val authenticator = VtsAuthenticator(config, transport)

        // Act
        val result = authenticator.authenticateWithRetry(maxRetries = 3, initialDelayMs = 0)

        // Assert
        assertEquals("last-chance-token", result)
        assertEquals(4, transport.callCount) // 3 failures + 1 success
    }

    // --- authenticateWithRetry() failure cases ---

    @Test
    fun authenticateWithRetryExhaustsMaxRetriesAndThrows() = runTest {
        // Arrange: always fails with VtsApiException
        val transport = AlwaysFailTransport(errorId = 99, message = "service unavailable")
        val config = testConfig(authToken = "token")
        val authenticator = VtsAuthenticator(config, transport)

        // Act & Assert
        assertFailsWith<VtsApiException> {
            authenticator.authenticateWithRetry(maxRetries = 2, initialDelayMs = 0)
        }
        // 1 initial attempt + 2 retries = 3 total
        assertEquals(3, transport.callCount)
    }

    @Test
    fun authenticateWithRetryThrowsLastExceptionOnExhaustion() = runTest {
        // Arrange: each failure has a distinct message so we can identify the last one
        val transport = SequentialErrorTransport(
            errors = listOf(
                VtsApiException(1, "first error"),
                VtsApiException(2, "second error"),
                VtsApiException(3, "third error"),
            ),
        )
        val config = testConfig(authToken = "token")
        val authenticator = VtsAuthenticator(config, transport)

        // Act & Assert: the thrown exception should be the last one (third error)
        val ex = assertFailsWith<VtsApiException> {
            authenticator.authenticateWithRetry(maxRetries = 2, initialDelayMs = 0)
        }
        assertEquals(3, ex.errorId)
        assertEquals("third error", ex.vtsMessage)
    }

    @Test
    fun authenticateWithRetryZeroRetriesMakesExactlyOneAttempt() = runTest {
        // Arrange: always fails
        val transport = AlwaysFailTransport(errorId = 1, message = "no retries")
        val config = testConfig(authToken = "token")
        val authenticator = VtsAuthenticator(config, transport)

        // Act & Assert
        assertFailsWith<VtsApiException> {
            authenticator.authenticateWithRetry(maxRetries = 0, initialDelayMs = 0)
        }
        assertEquals(1, transport.callCount)
    }

    // --- authenticateWithRetry() VtsAuthException is never retried ---

    @Test
    fun authenticateWithRetryDoesNotRetryOnVtsAuthException() = runTest {
        // Arrange: transport returns authenticated=false (user denied)
        val transport = RecordingTransport(
            authTokenResponse = tokenResponse("token"),
            authResponse = authResponse(authenticated = false, reason = "Denied"),
        )
        val config = testConfig(authToken = "token")
        val authenticator = VtsAuthenticator(config, transport)

        // Act & Assert: VtsAuthException should propagate immediately without retry
        val ex = assertFailsWith<VtsAuthException> {
            authenticator.authenticateWithRetry(maxRetries = 5, initialDelayMs = 0)
        }
        assertEquals("Denied", ex.reason)
        // Only 1 auth request — no retry was attempted
        assertEquals(1, transport.authRequestCount)
    }

    @Test
    fun authenticateWithRetryVtsAuthExceptionIsDistinctFromVtsApiException() = runTest {
        // Ensure the thrown type is specifically VtsAuthException, not VtsApiException
        val transport = RecordingTransport(
            authTokenResponse = tokenResponse("token"),
            authResponse = authResponse(authenticated = false, reason = "Access denied"),
        )
        val config = testConfig(authToken = "token")
        val authenticator = VtsAuthenticator(config, transport)

        val thrown = runCatching {
            authenticator.authenticateWithRetry(maxRetries = 3, initialDelayMs = 0)
        }.exceptionOrNull()

        assertIs<VtsAuthException>(thrown)
    }

    // --- cancellation ---

    @Test
    fun authenticateWithRetryCancellationPropagates() = runTest {
        // Arrange: transport throws CancellationException to simulate coroutine cancellation
        val transport = CancellingTransport()
        val config = testConfig(authToken = null)
        val authenticator = VtsAuthenticator(config, transport)

        // Act & Assert: CancellationException must propagate, not be swallowed as a retry
        assertFailsWith<CancellationException> {
            authenticator.authenticateWithRetry(maxRetries = 3, initialDelayMs = 0)
        }
        // Only 1 attempt was made — cancellation is not retried
        assertEquals(1, transport.callCount)
    }
}

// ---- Helpers ----

private fun testConfig(authToken: String? = null): VtsConfig =
    VtsConfig(pluginName = "TestPlugin", pluginDeveloper = "TestDev", authToken = authToken)

private fun tokenResponse(token: String): VtsEnvelope = VtsEnvelope(
    messageType = "AuthenticationTokenResponse",
    data = JsonObject(mapOf("authenticationToken" to JsonPrimitive(token))),
)

private fun authResponse(authenticated: Boolean, reason: String = ""): VtsEnvelope = VtsEnvelope(
    messageType = "AuthenticationResponse",
    data = JsonObject(
        mapOf(
            "authenticated" to JsonPrimitive(authenticated),
            "reason" to JsonPrimitive(reason),
        ),
    ),
)

/**
 * Transport that records how many times each request type was sent and returns
 * preset responses for token and auth requests.
 */
private class RecordingTransport(
    private val authTokenResponse: VtsEnvelope,
    private val authResponse: VtsEnvelope,
) : VtsTransport {

    var tokenRequestCount = 0
        private set
    var authRequestCount = 0
        private set

    private var idCounter = 0

    override suspend fun sendAndReceive(request: String): VtsEnvelope {
        return when {
            request.contains("AuthenticationTokenRequest") -> {
                tokenRequestCount++
                authTokenResponse
            }
            request.contains("AuthenticationRequest") -> {
                authRequestCount++
                authResponse
            }
            else -> error("Unexpected request: $request")
        }
    }

    override fun nextRequestId(): String = (++idCounter).toString()
}

/**
 * Transport that throws [VtsApiException] for the first [failCount] auth calls,
 * then returns a success response.
 */
private class FailThenSucceedTransport(
    private val failCount: Int,
    private val successToken: String,
) : VtsTransport {

    var callCount = 0
        private set

    private var idCounter = 0

    override suspend fun sendAndReceive(request: String): VtsEnvelope {
        if (!request.contains("AuthenticationRequest")) {
            // Ignore token requests — return empty token (already injected via config)
            return tokenResponse(successToken)
        }
        callCount++
        if (callCount <= failCount) {
            throw VtsApiException(500, "transient failure $callCount")
        }
        return authResponse(authenticated = true)
    }

    override fun nextRequestId(): String = (++idCounter).toString()
}

/**
 * Transport that always throws [VtsApiException] on every auth request.
 */
private class AlwaysFailTransport(
    private val errorId: Int,
    private val message: String,
) : VtsTransport {

    var callCount = 0
        private set

    private var idCounter = 0

    override suspend fun sendAndReceive(request: String): VtsEnvelope {
        if (request.contains("AuthenticationRequest")) {
            callCount++
            throw VtsApiException(errorId, message)
        }
        // token request — not expected if config has a token, but handle gracefully
        return tokenResponse("dummy")
    }

    override fun nextRequestId(): String = (++idCounter).toString()
}

/**
 * Transport that returns a pre-defined sequence of [VtsApiException]s, one per auth call.
 */
private class SequentialErrorTransport(
    private val errors: List<VtsApiException>,
) : VtsTransport {

    private var callIndex = 0
    private var idCounter = 0

    override suspend fun sendAndReceive(request: String): VtsEnvelope {
        if (!request.contains("AuthenticationRequest")) return tokenResponse("dummy")
        val ex = errors[callIndex++]
        throw ex
    }

    override fun nextRequestId(): String = (++idCounter).toString()
}

/**
 * Transport that throws [CancellationException] on the first call, simulating
 * an external coroutine cancellation during the auth flow.
 */
private class CancellingTransport : VtsTransport {

    var callCount = 0
        private set

    private var idCounter = 0

    override suspend fun sendAndReceive(request: String): VtsEnvelope {
        callCount++
        throw CancellationException("coroutine cancelled during auth")
    }

    override fun nextRequestId(): String = (++idCounter).toString()
}
