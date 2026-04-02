package io.github.kmpfacelink.stream

import io.github.kmpfacelink.util.FaceLinkLogger
import io.ktor.server.application.install
import io.ktor.server.cio.CIO
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.embeddedServer
import io.ktor.server.routing.routing
import io.ktor.server.websocket.DefaultWebSocketServerSession
import io.ktor.server.websocket.WebSockets
import io.ktor.server.websocket.pingPeriod
import io.ktor.server.websocket.timeout
import io.ktor.server.websocket.webSocket
import io.ktor.websocket.Frame
import io.ktor.websocket.close
import io.ktor.websocket.send
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
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.concurrent.atomics.AtomicInt
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.time.Duration.Companion.milliseconds

/**
 * WebSocket streaming server for real-time tracking data.
 *
 * Starts a Ktor CIO HTTP server with a WebSocket endpoint at `/tracking`.
 * Connected clients receive tracking frames at the configured FPS.
 *
 * Usage:
 * ```kotlin
 * val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
 * val server = StreamServer(StreamConfig(port = 21412))
 * server.start(scope)
 * // Send frames from a tracking flow
 * trackingFlow.collect { data ->
 *     server.sendFrame(buildStreamFrame(data, null, data.timestampMs))
 * }
 * server.stop()
 * ```
 *
 * Tracking data is produced by [io.github.kmpfacelink.api.FaceTracker] in the core module.
 *
 * @param config Server configuration
 */
@OptIn(ExperimentalAtomicApi::class)
public class StreamServer(
    private val config: StreamConfig = StreamConfig(),
) {
    private val json = Json { encodeDefaults = false }
    private val mutex = Mutex()
    private val sessions = mutableListOf<ClientSession>()
    private val _clientCount = AtomicInt(0)
    private var serverJob: Job? = null
    private var engine: EmbeddedServer<*, *>? = null

    private val _connectionCount = MutableSharedFlow<Int>(
        replay = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )

    /** Observable connection count changes. */
    public val connectionCount: SharedFlow<Int> = _connectionCount.asSharedFlow()

    /** Current number of connected clients. */
    public val clientCount: Int get() = _clientCount.load()

    /** Whether the server is currently running. */
    public val isRunning: Boolean get() = serverJob?.isActive == true

    /**
     * Starts the WebSocket server.
     *
     * The caller owns [scope] and is responsible for cancelling it
     * when the server is no longer needed.
     *
     * @param scope Coroutine scope for the server lifecycle
     */
    public fun start(scope: CoroutineScope) {
        if (isRunning) return
        serverJob = scope.launch { runServer() }
    }

    /**
     * Stops the WebSocket server and disconnects all clients.
     */
    public fun stop() {
        engine?.stop()
        engine = null
        serverJob?.cancel()
        serverJob = null
        sessions.clear()
        _clientCount.store(0)
    }

    /**
     * Sends a tracking frame to all connected clients.
     *
     * @param frame The tracking data frame to broadcast
     */
    public suspend fun sendFrame(frame: StreamFrame) {
        val snapshot = mutex.withLock { sessions.toList() }
        for (session in snapshot) {
            sendToSession(session, frame)
        }
    }

    private suspend fun runServer() {
        val server = embeddedServer(CIO, port = config.port, host = config.host) {
            install(WebSockets) {
                pingPeriod = config.pingIntervalMs.milliseconds
                timeout = config.timeoutMs.milliseconds
            }
            routing {
                webSocket("/tracking") {
                    handleClientSession(this)
                }
            }
        }
        engine = server
        server.start(wait = true)
    }

    private suspend fun handleClientSession(wsSession: DefaultWebSocketServerSession) {
        val session = ClientSession(wsSession)
        addSession(session)
        try {
            for (frame in wsSession.incoming) {
                // Consume incoming frames to keep session alive
            }
        } finally {
            removeSession(session)
        }
    }

    private suspend fun addSession(session: ClientSession) {
        val count = mutex.withLock {
            sessions.add(session)
            _clientCount.store(sessions.size)
            sessions.size
        }
        // Use tryEmit to avoid suspension/cancellation that could leave
        // the session list and emitted count in an inconsistent state.
        // Always succeeds because the flow has replay=1 and DROP_OLDEST.
        _connectionCount.tryEmit(count)
    }

    private suspend fun removeSession(session: ClientSession) {
        val count = mutex.withLock {
            sessions.remove(session)
            _clientCount.store(sessions.size)
            sessions.size
        }
        _connectionCount.tryEmit(count)
    }

    private suspend fun sendToSession(session: ClientSession, frame: StreamFrame) {
        try {
            when (config.format) {
                StreamFormat.JSON -> {
                    val text = json.encodeToString(frame)
                    session.wsSession.send(text)
                }
                StreamFormat.BINARY -> {
                    val bytes = BinaryFrameCodec.encode(frame)
                    session.wsSession.outgoing.send(Frame.Binary(true, bytes))
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            FaceLinkLogger.w(TAG, "Client send failed (${e::class.simpleName}): ${e.message}")
            try {
                session.wsSession.close()
            } catch (closeEx: Exception) {
                FaceLinkLogger.d(TAG, "Session already closed (${closeEx::class.simpleName}): ${closeEx.message}")
            }
            removeSession(session)
        }
    }

    private companion object {
        private const val TAG = "StreamServer"
    }
}

internal data class ClientSession(
    val wsSession: DefaultWebSocketServerSession,
)
