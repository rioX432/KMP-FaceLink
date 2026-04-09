package io.github.kmpfacelink.stream.webrtc

import io.github.kmpfacelink.stream.BaseWebSocketClient
import io.github.kmpfacelink.util.FaceLinkLogger
import io.ktor.client.HttpClient
import io.ktor.websocket.Frame
import io.ktor.websocket.readText
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
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
    url: String,
    httpClient: HttpClient? = null,
) : BaseWebSocketClient(url, httpClient, TAG) {

    private val json = Json {
        encodeDefaults = false
        ignoreUnknownKeys = true
    }

    private val _messages = MutableSharedFlow<SignalingMessage>(
        extraBufferCapacity = MESSAGE_BUFFER_CAPACITY,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )

    /** Flow of signaling messages received from the remote peer. */
    public val messages: SharedFlow<SignalingMessage> = _messages.asSharedFlow()

    /** Whether the signaling WebSocket is currently connected. */
    public val isConnected: Boolean
        get() = isSessionActive

    /**
     * Sends a signaling message to the remote peer.
     *
     * @param message The signaling message to send
     * @throws IllegalStateException if the client is not connected
     */
    public suspend fun send(message: SignalingMessage) {
        val currentSession = acquireSession()
        check(currentSession != null) { "Signaling client is not connected" }
        val payload = json.encodeToString(SignalingMessage.serializer(), message)
        currentSession.outgoing.send(Frame.Text(payload))
    }

    override suspend fun onFrame(frame: Frame) {
        if (frame is Frame.Text) {
            parseAndEmit(frame.readText())
        }
    }

    private suspend fun parseAndEmit(text: String) {
        try {
            val message = json.decodeFromString(SignalingMessage.serializer(), text)
            _messages.emit(message)
        } catch (e: CancellationException) {
            throw e
        } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
            FaceLinkLogger.w(TAG, "Failed to parse message (${e::class.simpleName}): ${e.message}")
        }
    }

    private companion object {
        private const val TAG = "WebRtcSignalingClient"
        const val MESSAGE_BUFFER_CAPACITY = 32
    }
}
