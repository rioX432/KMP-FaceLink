package io.github.kmpfacelink.vts

import io.github.kmpfacelink.api.Releasable
import io.ktor.client.HttpClient
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.websocket.Frame
import io.ktor.websocket.WebSocketSession
import io.ktor.websocket.readText
import io.ktor.websocket.send
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.concurrent.atomics.AtomicInt
import kotlin.concurrent.atomics.ExperimentalAtomicApi

/**
 * WebSocket client for the VTube Studio Plugin API.
 *
 * Manages the WebSocket connection, authentication flow, parameter injection,
 * and post-processing control. The client must be [connect]ed and [authenticate]d
 * before sending parameter data or controlling post-processing effects.
 *
 * ## Authentication Flow
 *
 * 1. Call [connect] to establish the WebSocket connection.
 * 2. Call [authenticate] (or [authenticateWithRetry] for robustness).
 *    - If [VtsConfig.authToken] is set, it authenticates with the saved token.
 *    - Otherwise, VTS shows a permission popup to the user.
 * 3. Save the returned token for future sessions.
 * 4. Call [injectParameters], [setPostProcessingValues], etc.
 *
 * ## Example
 *
 * ```kotlin
 * val client = VtsClient(VtsConfig(pluginName = "MyPlugin", pluginDeveloper = "Me"))
 * client.connect(scope)
 * val token = client.authenticateWithRetry()
 * // Save token for next session, then inject parameters:
 * client.injectParameters(parameterValues, faceFound = true)
 * // Control post-processing:
 * client.setPostProcessingValues(listOf(PostProcessingValue("Bloom_Strength", "0.5")))
 * client.disconnect()
 * ```
 *
 * @param config VTS connection configuration
 * @param httpClient Optional Ktor HttpClient (must have WebSockets plugin installed)
 * @see VtsSender for a high-level sender that handles the full lifecycle
 */
@Suppress("TooManyFunctions")
@OptIn(ExperimentalAtomicApi::class)
public class VtsClient(
    private val config: VtsConfig = VtsConfig(),
    httpClient: HttpClient? = null,
) : Releasable {
    private val client: HttpClient = httpClient ?: HttpClient { install(WebSockets) }
    private val ownsClient: Boolean = httpClient == null
    private var released = false

    private val mutex = Mutex()
    private var session: WebSocketSession? = null
    private var connectionJob: Job? = null
    private val sendChannel = Channel<String>(Channel.BUFFERED)
    private val pendingRequests = mutableMapOf<String, CompletableDeferred<VtsEnvelope>>()
    private val requestCounter = AtomicInt(0)

    /** Whether the client is currently connected to VTS. */
    public val isConnected: Boolean get() = session != null

    /**
     * Connects to the VTube Studio WebSocket server.
     *
     * @param scope Coroutine scope for the connection lifecycle
     * @throws Exception if connection fails
     */
    public suspend fun connect(scope: CoroutineScope) {
        disconnect()
        val ready = CompletableDeferred<Unit>()
        connectionJob = scope.launch {
            client.webSocket(host = config.host, port = config.port) {
                val ws = this
                mutex.withLock { session = ws }
                ready.complete(Unit)
                launch { sendLoop(ws) }
                receiveLoop(ws)
            }
        }
        ready.await()
    }

    /**
     * Disconnects from VTube Studio.
     */
    public suspend fun disconnect() {
        connectionJob?.cancel()
        connectionJob = null
        mutex.withLock {
            session = null
            pendingRequests.values.forEach {
                it.cancel(CancellationException("Disconnected"))
            }
            pendingRequests.clear()
        }
        if (ownsClient) {
            client.close()
        }
    }

    /**
     * Authenticates with VTube Studio.
     *
     * If [VtsConfig.authToken] is set, attempts to authenticate with the existing token.
     * Otherwise, requests a new token (which triggers an approval popup in VTS).
     *
     * @return The authentication token (save this for future sessions)
     * @throws VtsAuthException if authentication is denied
     * @throws VtsApiException if VTS returns an API error
     */
    public suspend fun authenticate(): String =
        requestAndValidateAuth(config.authToken)

    /**
     * Injects parameter values into VTube Studio.
     *
     * Must be called at least once per second per parameter to prevent
     * VTS from reverting to its own tracking values.
     *
     * @param parameters List of parameter values to inject
     * @param faceFound Whether face tracking is active
     * @param mode Injection mode: "set" (exclusive) or "add" (shared)
     * @throws VtsApiException if VTS returns an API error
     */
    public suspend fun injectParameters(
        parameters: List<ParameterValue>,
        faceFound: Boolean = true,
        mode: String = "set",
    ) {
        val request = VtsMessageBuilder.buildInjectRequest(
            parameters = parameters,
            faceFound = faceFound,
            mode = mode,
            requestId = nextRequestId(),
        )
        sendAndReceive(request)
    }

    /**
     * Creates a custom parameter in VTube Studio.
     *
     * Custom parameters appear in the VTS parameter list with a blue "P" indicator.
     * Each plugin can create up to 100 custom parameters (300 total across all plugins).
     *
     * @param name Parameter name (4-32 alphanumeric characters, no spaces)
     * @param explanation Optional description (max 256 characters)
     * @param min Minimum value (default 0)
     * @param max Maximum value (default 1)
     * @param defaultValue Default value (default 0)
     * @throws VtsApiException if VTS returns an API error
     */
    public suspend fun createParameter(
        name: String,
        explanation: String = "",
        min: Float = 0f,
        max: Float = 1f,
        defaultValue: Float = 0f,
    ) {
        val data = ParameterCreationData(
            parameterName = name,
            explanation = explanation,
            min = min,
            max = max,
            defaultValue = defaultValue,
        )
        val request = VtsMessageBuilder.buildParameterCreationRequest(
            data = data,
            requestId = nextRequestId(),
        )
        sendAndReceive(request)
    }

    /**
     * Authenticates with retry logic for transient failures.
     *
     * Attempts authentication up to [maxRetries] times with exponential backoff.
     * Useful when VTS may not be ready immediately after launch.
     * [VtsAuthException] (user denied) is never retried.
     *
     * @param maxRetries Maximum number of retry attempts (default 3)
     * @param initialDelayMs Initial delay between retries in milliseconds (default 1000)
     * @return The authentication token
     * @throws VtsAuthException if authentication is denied by the user
     * @throws VtsApiException if all retries are exhausted
     */
    public suspend fun authenticateWithRetry(
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

    /**
     * Retrieves the current post-processing state and available effects.
     *
     * Returns information about whether post-processing is active,
     * the current preset, and all available effects with their configurations.
     *
     * @param effectIdFilter Optional list of effect IDs to filter (empty = all effects)
     * @return Current post-processing state
     * @throws VtsApiException if VTS returns an API error
     */
    public suspend fun getPostProcessingEffects(
        effectIdFilter: List<String> = emptyList(),
    ): PostProcessingState {
        val request = VtsMessageBuilder.buildPostProcessingListRequest(
            fillPresets = false,
            fillEffects = true,
            effectIdFilter = effectIdFilter,
            requestId = nextRequestId(),
        )
        val response = sendAndReceive(request)
        val data = requireNotNull(response.data) { "PostProcessingList response missing data" }
        val parsed = VtsResponseParser.parsePostProcessingListResponse(data)
        return mapPostProcessingState(parsed)
    }

    /**
     * Sets post-processing effect values in VTube Studio.
     *
     * Allows controlling visual effects like bloom, color grading, vignette,
     * chromatic aberration, and more. Effect config IDs are case-insensitive
     * and underscores/hyphens are ignored.
     *
     * Example:
     * ```kotlin
     * client.setPostProcessingValues(
     *     values = listOf(
     *         PostProcessingValue("Backlight_Strength", "0.8"),
     *         PostProcessingValue("ColorGrading_Saturation", "1.2"),
     *     ),
     *     fadeTime = 0.5,
     * )
     * ```
     *
     * @param values List of effect values to set
     * @param postProcessingOn Whether to enable post-processing (default true)
     * @param fadeTime Transition time in seconds (default 0.0 for instant)
     * @param setAllOtherToDefault Reset unspecified values to defaults (default false)
     * @throws VtsApiException if VTS returns an API error
     */
    public suspend fun setPostProcessingValues(
        values: List<PostProcessingValue>,
        postProcessingOn: Boolean = true,
        fadeTime: Double = 0.0,
        setAllOtherToDefault: Boolean = false,
    ) {
        val internalValues = values.map { PostProcessingValueData(it.configId, it.configValue) }
        val request = VtsMessageBuilder.buildPostProcessingUpdateRequest(
            values = internalValues,
            postProcessingOn = postProcessingOn,
            fadeTime = fadeTime,
            setAllOtherToDefault = setAllOtherToDefault,
            requestId = nextRequestId(),
        )
        sendAndReceive(request)
    }

    /**
     * Releases all resources held by this client.
     *
     * Cancels any active connection and closes the HTTP client if it was
     * created internally. Safe to call multiple times.
     */
    public override fun release() {
        if (released) return
        released = true
        connectionJob?.cancel()
        connectionJob = null
        session = null
        pendingRequests.values.forEach {
            it.cancel(CancellationException("Released"))
        }
        pendingRequests.clear()
        if (ownsClient) {
            client.close()
        }
    }

    private suspend fun requestAndValidateAuth(existingToken: String?): String {
        val token = if (existingToken != null) {
            existingToken
        } else {
            val request = VtsMessageBuilder.buildAuthTokenRequest(
                pluginName = config.pluginName,
                pluginDeveloper = config.pluginDeveloper,
                pluginIcon = config.pluginIcon,
                requestId = nextRequestId(),
            )
            val response = sendAndReceive(request)
            val data = requireNotNull(response.data) { "Token response missing data" }
            VtsResponseParser.parseAuthTokenResponse(data).authenticationToken
        }
        val authRequest = VtsMessageBuilder.buildAuthRequest(
            pluginName = config.pluginName,
            pluginDeveloper = config.pluginDeveloper,
            authToken = token,
            requestId = nextRequestId(),
        )
        val authResponse = sendAndReceive(authRequest)
        val authData = requireNotNull(authResponse.data) { "Auth response missing data" }
        val result = VtsResponseParser.parseAuthResponse(authData)
        if (!result.authenticated) {
            throw VtsAuthException(result.reason)
        }
        return token
    }

    private suspend fun sendAndReceive(request: String): VtsEnvelope {
        val envelope = VtsResponseParser.parseEnvelope(request)
        val deferred = CompletableDeferred<VtsEnvelope>()
        mutex.withLock { pendingRequests[envelope.requestID] = deferred }
        sendChannel.send(request)
        val response = deferred.await()
        if (VtsResponseParser.isError(response)) {
            val error = VtsResponseParser.parseError(response)
            throw VtsApiException(error.errorId, error.message)
        }
        return response
    }

    private suspend fun sendLoop(ws: WebSocketSession) {
        for (message in sendChannel) {
            ws.send(message)
        }
    }

    private suspend fun receiveLoop(ws: WebSocketSession) {
        try {
            for (frame in ws.incoming) {
                if (frame is Frame.Text) {
                    handleResponse(frame.readText())
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            // Connection closed
        } finally {
            mutex.withLock { session = null }
        }
    }

    private suspend fun handleResponse(text: String) {
        val envelope = VtsResponseParser.parseEnvelope(text)
        val requestId = envelope.requestID
        if (requestId.isNotEmpty()) {
            val deferred = mutex.withLock { pendingRequests.remove(requestId) }
            deferred?.complete(envelope)
        }
    }

    private fun nextRequestId(): String {
        val id = requestCounter.addAndFetch(1)
        return "kmpfl-$id"
    }
}

private const val DEFAULT_MAX_RETRIES = 3
private const val DEFAULT_RETRY_DELAY_MS = 1000L
private const val BACKOFF_MULTIPLIER = 2L
