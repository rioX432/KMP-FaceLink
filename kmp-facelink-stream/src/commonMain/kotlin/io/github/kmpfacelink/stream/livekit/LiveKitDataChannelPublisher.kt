package io.github.kmpfacelink.stream.livekit

import io.github.kmpfacelink.stream.BaseWebSocketClient
import io.github.kmpfacelink.stream.ConnectionState
import io.github.kmpfacelink.stream.StreamFrame
import io.github.kmpfacelink.util.FaceLinkLogger
import io.ktor.client.HttpClient
import io.ktor.websocket.Frame
import io.ktor.websocket.WebSocketSession
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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
) : BaseWebSocketClient(config.serverUrl, httpClient, TAG), TrackingDataPublisher {

    private val json = Json { encodeDefaults = false }
    private var reconnectAttempts = 0

    private val _liveKitConnectionState =
        MutableStateFlow(LiveKitConnectionState.DISCONNECTED)

    /**
     * Observable LiveKit-specific connection state.
     *
     * Includes [LiveKitConnectionState.RECONNECTING] in addition to the
     * common [ConnectionState] values exposed by [connectionState].
     */
    public val liveKitConnectionState: StateFlow<LiveKitConnectionState> =
        _liveKitConnectionState.asStateFlow()

    override val isConnected: Boolean
        get() = _liveKitConnectionState.value == LiveKitConnectionState.CONNECTED

    override suspend fun onConnecting() {
        _liveKitConnectionState.value = LiveKitConnectionState.CONNECTING
    }

    override suspend fun publish(frame: StreamFrame) {
        val currentSession = acquireSession() ?: return
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
        super.disconnect()
        reconnectAttempts = 0
        _liveKitConnectionState.value = LiveKitConnectionState.DISCONNECTED
    }

    override suspend fun onConnected(ws: WebSocketSession) {
        reconnectAttempts = 0
        _liveKitConnectionState.value = LiveKitConnectionState.CONNECTED
    }

    override suspend fun onDisconnected() {
        _liveKitConnectionState.value = LiveKitConnectionState.DISCONNECTED
    }

    override suspend fun onConnectionFailed(error: Exception) {
        attemptReconnect()
    }

    override fun resolveUrl(): String = buildWebSocketUrl()

    private suspend fun attemptReconnect() {
        if (reconnectAttempts >= config.maxReconnectAttempts) {
            _liveKitConnectionState.value = LiveKitConnectionState.DISCONNECTED
            return
        }
        reconnectAttempts++
        _liveKitConnectionState.value = LiveKitConnectionState.RECONNECTING
        delay(config.reconnectDelayMs)
        establishConnection()
    }

    private suspend fun handleSendFailure(error: Exception) {
        FaceLinkLogger.w(TAG, "Send failed (${error::class.simpleName}): ${error.message}")
        clearSession()
        _liveKitConnectionState.value = LiveKitConnectionState.DISCONNECTED
        mutableConnectionState.value = ConnectionState.DISCONNECTED
    }

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
