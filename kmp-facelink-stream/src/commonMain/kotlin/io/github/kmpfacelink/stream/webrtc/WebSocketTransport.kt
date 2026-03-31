package io.github.kmpfacelink.stream.webrtc

import io.ktor.client.HttpClient
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.websocket.Frame
import io.ktor.websocket.WebSocketSession
import io.ktor.websocket.close
import io.ktor.websocket.readBytes
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

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
    private val url: String,
    httpClient: HttpClient? = null,
) : DataChannelTransport {

    private val mutex = Mutex()
    private var session: WebSocketSession? = null
    private var connectionJob: Job? = null

    private val client: HttpClient = httpClient ?: HttpClient {
        install(WebSockets)
    }

    private val _connectionState = MutableSharedFlow<Boolean>(
        replay = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )

    private val _incoming = MutableSharedFlow<ByteArray>(
        extraBufferCapacity = INCOMING_BUFFER_CAPACITY,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )

    override val isConnected: Boolean
        get() = session != null

    override val connectionState: SharedFlow<Boolean> = _connectionState.asSharedFlow()

    override val incoming: Flow<ByteArray> = _incoming.asSharedFlow()

    override suspend fun connect(scope: CoroutineScope) {
        connectionJob?.cancel()
        connectionJob = scope.launch { establishConnection() }
    }

    override suspend fun send(data: ByteArray) {
        val currentSession = mutex.withLock { session }
        check(currentSession != null) { "Transport is not connected" }
        try {
            currentSession.outgoing.send(Frame.Binary(true, data))
        } catch (e: CancellationException) {
            throw e
        } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
            mutex.withLock { session = null }
            _connectionState.emit(false)
            throw e
        }
    }

    override suspend fun disconnect() {
        connectionJob?.cancel()
        connectionJob = null
        mutex.withLock {
            session?.close()
            session = null
        }
        _connectionState.emit(false)
    }

    private suspend fun establishConnection() {
        try {
            client.webSocket(urlString = url) {
                mutex.withLock { session = this }
                _connectionState.emit(true)
                receiveLoop(this)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
            mutex.withLock { session = null }
            _connectionState.emit(false)
            println("[WebSocketTransport] Connection failed (${e::class.simpleName}): ${e.message}")
        }
    }

    private suspend fun receiveLoop(ws: WebSocketSession) {
        for (frame in ws.incoming) {
            when (frame) {
                is Frame.Binary -> _incoming.emit(frame.readBytes())
                is Frame.Text -> _incoming.emit(frame.readBytes())
                else -> { /* Ignore ping/pong/close frames */ }
            }
        }
        // Connection closed
        mutex.withLock { session = null }
        _connectionState.emit(false)
    }

    private companion object {
        const val INCOMING_BUFFER_CAPACITY = 64
    }
}
