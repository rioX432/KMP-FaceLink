package io.github.kmpfacelink.vts

import kotlinx.coroutines.delay

/**
 * Handles VTube Studio authentication flows including token request,
 * token validation, and retry-with-exponential-backoff.
 *
 * @param config VTS connection configuration supplying plugin identity and optional saved token
 * @param transport Transport layer for sending requests and receiving responses
 */
internal class VtsAuthenticator(
    private val config: VtsConfig,
    private val transport: VtsTransport,
) {
    /**
     * Authenticates with VTube Studio using the token in [config] if available,
     * or requests a new token (which triggers the VTS approval popup).
     *
     * @return The authentication token
     * @throws VtsAuthException if the user denies the authentication request
     * @throws VtsApiException if VTS returns an API error
     */
    suspend fun authenticate(): String = requestAndValidateAuth(config.authToken)

    /**
     * Authenticates with retry logic and exponential backoff for transient failures.
     *
     * [VtsAuthException] (user denied) is never retried.
     *
     * @param maxRetries Maximum number of retry attempts
     * @param initialDelayMs Initial delay between retries in milliseconds
     * @return The authentication token
     * @throws VtsAuthException if authentication is denied by the user
     * @throws VtsApiException if all retries are exhausted
     */
    suspend fun authenticateWithRetry(
        maxRetries: Int = DEFAULT_MAX_RETRIES,
        initialDelayMs: Long = DEFAULT_RETRY_DELAY_MS,
    ): String {
        var lastException: Exception? = null
        var delayMs = initialDelayMs
        repeat(maxRetries + 1) { attempt ->
            try {
                return authenticate()
            } catch (e: VtsAuthException) {
                throw e // User denied — do not retry
            } catch (e: VtsApiException) {
                lastException = e
                if (attempt < maxRetries) {
                    delay(delayMs)
                    delayMs *= BACKOFF_MULTIPLIER
                }
            }
        }
        throw lastException ?: VtsApiException(0, "Authentication failed after retries")
    }

    private suspend fun requestAndValidateAuth(existingToken: String?): String {
        val token = resolveToken(existingToken)
        return validateToken(token)
    }

    private suspend fun resolveToken(existingToken: String?): String {
        if (existingToken != null) return existingToken
        val request = VtsMessageBuilder.buildAuthTokenRequest(
            pluginName = config.pluginName,
            pluginDeveloper = config.pluginDeveloper,
            pluginIcon = config.pluginIcon,
            requestId = transport.nextRequestId(),
        )
        val response = transport.sendAndReceive(request)
        val data = requireNotNull(response.data) { "Token response missing data" }
        return VtsResponseParser.parseAuthTokenResponse(data).authenticationToken
    }

    private suspend fun validateToken(token: String): String {
        val authRequest = VtsMessageBuilder.buildAuthRequest(
            pluginName = config.pluginName,
            pluginDeveloper = config.pluginDeveloper,
            authToken = token,
            requestId = transport.nextRequestId(),
        )
        val authResponse = transport.sendAndReceive(authRequest)
        val authData = requireNotNull(authResponse.data) { "Auth response missing data" }
        val result = VtsResponseParser.parseAuthResponse(authData)
        if (!result.authenticated) {
            throw VtsAuthException(result.reason)
        }
        return token
    }
}

private const val DEFAULT_MAX_RETRIES = 3
private const val DEFAULT_RETRY_DELAY_MS = 1000L
private const val BACKOFF_MULTIPLIER = 2L
