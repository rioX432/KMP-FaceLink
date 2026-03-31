package io.github.kmpfacelink.stream.webrtc

import io.ktor.client.HttpClient
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.websocket.Frame
import io.ktor.websocket.WebSocketSession
import io.ktor.websocket.close
import io.ktor.websocket.readText
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json

/**
 * WebSocket-based signaling client for WebRTC connection establishment.
 *
 * Connects to a signaling server (typically the same host as the existing
 * [io.github.kmpfacelink.stream.StreamServer]) and exchanges SDP
 * offer/answer and ICE candidate messages required to set up a WebRTC
 * peer connection.
 *
 * Usage:
 * ```kotlin
 * val client = WebRtcSignalingClient("ws://192.168.1.10:21412/signaling")
 * client.connect(scope)
 * // Observe incoming signaling messages
 * client.messages.collect { msg ->
 *     when (msg) {
 *         is SignalingMessage.Answer -> peerConnection.setRemoteDescription(msg.sdp)
 *         is SignalingMessage.IceCandidate -> peerConnection.addIceCandidate(msg)
 *         // ...
 *     }
 * }
 * // Send our offer
 * client.send(SignalingMessage.Offer(sdp = localSdp))
 * ```
 *
 * @param url WebSocket URL of the signaling endpoint
 * @param httpClient Optional pre-configured Ktor [HttpClient]
 */
public class WebRtcSignalingClient(
    private val url: String,
    httpClient: HttpClient? = null,
) {
    private val json = Json {
        encodeDefaults = false
        ignoreUnknownKeys = true
    }

    private val mutex = Mutex()
    private var session: WebSocketSession? = null
    private var connectionJob: Job? = null

    private val client: HttpClient = httpClient ?: HttpClient {
        install(WebSockets)
    }

    private val _messages = MutableSharedFlow<SignalingMessage>(
        extraBufferCapacity = MESSAGE_BUFFER_CAPACITY,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )

    /** Flow of signaling messages received from the remote peer. */
    public val messages: SharedFlow<SignalingMessage> = _messages.asSharedFlow()

    /** Whether the signaling WebSocket is currently connected. */
    public val isConnected: Boolean
        get() = session != null

    /**
     * Opens the signaling WebSocket connection.
     *
     * @param scope Coroutine scope for the connection lifecycle
     */
    public suspend fun connect(scope: CoroutineScope) {
        connectionJob?.cancel()
        connectionJob = scope.launch { establishConnection() }
    }

    /**
     * Sends a signaling message to the remote peer.
     *
     * @param message The signaling message to send
     * @throws IllegalStateException if the client is not connected
     */
    public suspend fun send(message: SignalingMessage) {
        val currentSession = mutex.withLock { session }
        check(currentSession != null) { "Signaling client is not connected" }
        val payload = json.encodeToString(SignalingMessage.serializer(), message)
        currentSession.outgoing.send(Frame.Text(payload))
    }

    /**
     * Closes the signaling WebSocket connection.
     */
    public suspend fun disconnect() {
        connectionJob?.cancel()
        connectionJob = null
        mutex.withLock {
            session?.close()
            session = null
        }
    }

    private suspend fun establishConnection() {
        try {
            client.webSocket(urlString = url) {
                mutex.withLock { session = this }
                receiveLoop(this)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
            mutex.withLock { session = null }
            println("[WebRtcSignaling] Connection failed (${e::class.simpleName}): ${e.message}")
        }
    }

    private suspend fun receiveLoop(ws: WebSocketSession) {
        for (frame in ws.incoming) {
            if (frame is Frame.Text) {
                parseAndEmit(frame.readText())
            }
        }
        mutex.withLock { session = null }
    }

    private suspend fun parseAndEmit(text: String) {
        try {
            val message = json.decodeFromString(SignalingMessage.serializer(), text)
            _messages.emit(message)
        } catch (e: CancellationException) {
            throw e
        } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
            println("[WebRtcSignaling] Failed to parse message (${e::class.simpleName}): ${e.message}")
        }
    }

    private companion object {
        const val MESSAGE_BUFFER_CAPACITY = 32
    }
}
