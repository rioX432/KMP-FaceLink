package io.github.kmpfacelink.stream.livekit

import io.github.kmpfacelink.stream.StreamFrame
import io.github.kmpfacelink.util.FaceLinkLogger
import io.ktor.client.HttpClient
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.websocket.Frame
import io.ktor.websocket.WebSocketSession
import io.ktor.websocket.close
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Publishes tracking data to a LiveKit-compatible endpoint via WebSocket.
 *
 * Connects to the configured server URL and sends [LiveKitTrackingMessage]
 * frames as JSON text over the WebSocket connection. This is designed to
 * bridge KMP-FaceLink tracking data to LiveKit rooms where an AI agent
 * consumes the data for avatar embodiment.
 *
 * The publisher handles reconnection automatically up to
 * [LiveKitBridgeConfig.maxReconnectAttempts] times.
 *
 * @param config Bridge configuration (server URL, token, room, etc.)
 * @param httpClient Optional pre-configured Ktor [HttpClient]. If null,
 *        a default client with WebSocket support is created.
 */
public class LiveKitDataChannelPublisher(
    private val config: LiveKitBridgeConfig,
    httpClient: HttpClient? = null,
) : TrackingDataPublisher {

    private val json = Json { encodeDefaults = false }
    private val mutex = Mutex()
    private var session: WebSocketSession? = null
    private var connectionJob: Job? = null
    private var reconnectAttempts = 0

    private val client: HttpClient = httpClient ?: HttpClient {
        install(WebSockets)
    }

    private val _connectionState = MutableStateFlow(LiveKitConnectionState.DISCONNECTED)

    /** Observable connection state. */
    public val connectionState: StateFlow<LiveKitConnectionState> =
        _connectionState.asStateFlow()

    override val isConnected: Boolean
        get() = _connectionState.value == LiveKitConnectionState.CONNECTED

    override suspend fun connect(scope: CoroutineScope) {
        connectionJob?.cancel()
        _connectionState.value = LiveKitConnectionState.CONNECTING
        connectionJob = scope.launch { establishConnection() }
    }

    override suspend fun publish(frame: StreamFrame) {
        val currentSession = mutex.withLock { session } ?: return
        val message = frame.toLiveKitMessage(config.participantIdentity)
        val payload = json.encodeToString(message)
        try {
            currentSession.outgoing.send(Frame.Text(payload))
        } catch (e: CancellationException) {
            throw e
        } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
            handleSendFailure(e)
        }
    }

    override suspend fun disconnect() {
        connectionJob?.cancel()
        connectionJob = null
        mutex.withLock {
            session?.close()
            session = null
        }
        reconnectAttempts = 0
        _connectionState.value = LiveKitConnectionState.DISCONNECTED
    }

    private suspend fun establishConnection() {
        val wsUrl = buildWebSocketUrl()
        try {
            client.webSocket(urlString = wsUrl) {
                mutex.withLock { session = this }
                reconnectAttempts = 0
                _connectionState.value = LiveKitConnectionState.CONNECTED
                // Keep connection alive by consuming incoming frames
                for (incoming in this.incoming) {
                    // Server may send acks or control messages; consume silently
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
            FaceLinkLogger.w(TAG, "Connection failed (${e::class.simpleName}): ${e.message}")
            mutex.withLock { session = null }
            attemptReconnect()
        }
    }

    private suspend fun attemptReconnect() {
        if (reconnectAttempts >= config.maxReconnectAttempts) {
            _connectionState.value = LiveKitConnectionState.DISCONNECTED
            return
        }
        reconnectAttempts++
        _connectionState.value = LiveKitConnectionState.RECONNECTING
        delay(config.reconnectDelayMs)
        establishConnection()
    }

    private suspend fun handleSendFailure(error: Exception) {
        FaceLinkLogger.w(TAG, "Send failed (${error::class.simpleName}): ${error.message}")
        mutex.withLock { session = null }
        _connectionState.value = LiveKitConnectionState.DISCONNECTED
    }

    /**
     * Builds the WebSocket URL with room and token as query parameters.
     *
     * LiveKit servers typically accept connections at the server URL with
     * authentication via the token. The room and identity are encoded in
     * the JWT token itself, but we include them as query params for
     * compatibility with proxy/bridge endpoints.
     */
    private companion object {
        private const val TAG = "LiveKitDataChannelPublisher"
    }

    internal fun buildWebSocketUrl(): String {
        val base = config.serverUrl.trimEnd('/')
        return "$base/rtc?room=${config.roomName}" +
            "&identity=${config.participantIdentity}" +
            "&token=${config.token}"
    }
}
