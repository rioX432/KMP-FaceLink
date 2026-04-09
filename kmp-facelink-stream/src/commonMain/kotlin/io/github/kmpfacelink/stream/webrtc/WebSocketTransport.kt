package io.github.kmpfacelink.stream.webrtc

import io.github.kmpfacelink.stream.BaseWebSocketClient
import io.github.kmpfacelink.stream.ConnectionState
import io.ktor.client.HttpClient
import io.ktor.websocket.Frame
import io.ktor.websocket.readBytes
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * [DataChannelTransport] implementation backed by a Ktor WebSocket client.
 *
 * Wraps an existing WebSocket connection to provide the same transport
 * interface that a future WebRTC DataChannel implementation will use.
 * This allows higher-level code ([io.github.kmpfacelink.stream.StreamSender])
 * to be transport-agnostic.
 *
 * @param url WebSocket URL to connect to (e.g. "ws://192.168.1.10:21412/tracking")
 * @param httpClient Optional pre-configured Ktor [HttpClient].
 *        If null, a default client with WebSocket support is created.
 */
public class WebSocketTransport(
    url: String,
    httpClient: HttpClient? = null,
) : BaseWebSocketClient(url, httpClient, TAG), DataChannelTransport {

    private val _incoming = MutableSharedFlow<ByteArray>(
        extraBufferCapacity = INCOMING_BUFFER_CAPACITY,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )

    override val isConnected: Boolean
        get() = isSessionActive

    override val incoming: Flow<ByteArray> = _incoming.asSharedFlow()

    override suspend fun send(data: ByteArray) {
        try {
            sendFrame(Frame.Binary(true, data))
        } catch (e: CancellationException) {
            throw e
        } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
            clearSession()
            mutableConnectionState.value = ConnectionState.DISCONNECTED
            throw e
        }
    }

    override suspend fun onFrame(frame: Frame) {
        when (frame) {
            is Frame.Binary -> _incoming.emit(frame.readBytes())
            is Frame.Text -> _incoming.emit(frame.readBytes())
            else -> { /* Ignore ping/pong/close frames */ }
        }
    }

    private companion object {
        private const val TAG = "WebSocketTransport"
        const val INCOMING_BUFFER_CAPACITY = 64
    }
}
