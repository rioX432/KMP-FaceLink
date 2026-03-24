package io.github.kmpfacelink.vts

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
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * WebSocket client for the VTube Studio Plugin API.
 *
 * Manages the WebSocket connection, authentication flow, and parameter injection.
 * The client must be [connect]ed and [authenticate]d before sending parameter data.
 *
 * Usage:
 * ```kotlin
 * val client = VtsClient(VtsConfig(pluginName = "MyPlugin", pluginDeveloper = "Me"))
 * client.connect(scope)
 * val token = client.authenticate()  // Shows approval popup in VTS
 * // Save token for next session, then inject parameters:
 * client.injectParameters(parameterValues, faceFound = true)
 * client.disconnect()
 * ```
 *
 * @param config VTS connection configuration
 * @param httpClient Optional Ktor HttpClient (must have WebSockets plugin installed)
 */
public class VtsClient(
    private val config: VtsConfig = VtsConfig(),
    httpClient: HttpClient? = null,
) {
    private val client: HttpClient = httpClient ?: HttpClient { install(WebSockets) }
    private val ownsClient: Boolean = httpClient == null

    private val mutex = Mutex()
    private var session: WebSocketSession? = null
    private var connectionJob: Job? = null
    private val sendChannel = Channel<String>(Channel.BUFFERED)
    private val pendingRequests = mutableMapOf<String, CompletableDeferred<VtsEnvelope>>()
    private var requestCounter = 0

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
            // Don't close user-provided client
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
    public suspend fun authenticate(): String {
        val token = config.authToken ?: requestNewToken()
        authenticateWithToken(token)
        return token
    }

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

    private suspend fun requestNewToken(): String {
        val request = VtsMessageBuilder.buildAuthTokenRequest(
            pluginName = config.pluginName,
            pluginDeveloper = config.pluginDeveloper,
            pluginIcon = config.pluginIcon,
            requestId = nextRequestId(),
        )
        val response = sendAndReceive(request)
        val data = requireNotNull(response.data) { "Token response missing data" }
        return VtsResponseParser.parseAuthTokenResponse(data).authenticationToken
    }

    private suspend fun authenticateWithToken(token: String) {
        val request = VtsMessageBuilder.buildAuthRequest(
            pluginName = config.pluginName,
            pluginDeveloper = config.pluginDeveloper,
            authToken = token,
            requestId = nextRequestId(),
        )
        val response = sendAndReceive(request)
        val data = requireNotNull(response.data) { "Auth response missing data" }
        val result = VtsResponseParser.parseAuthResponse(data)
        if (!result.authenticated) {
            throw VtsAuthException(result.reason)
        }
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
        requestCounter++
        return "kmpfl-$requestCounter"
    }
}
