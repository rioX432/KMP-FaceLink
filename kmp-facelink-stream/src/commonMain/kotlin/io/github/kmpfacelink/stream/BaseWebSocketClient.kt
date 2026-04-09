package io.github.kmpfacelink.stream

import io.github.kmpfacelink.api.Releasable
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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.concurrent.atomics.AtomicBoolean
import kotlin.concurrent.atomics.ExperimentalAtomicApi

/**
 * Shared WebSocket connection lifecycle management.
 *
 * Encapsulates the common connect/disconnect/establishConnection pattern
 * used across [io.github.kmpfacelink.stream.webrtc.WebSocketTransport],
 * [io.github.kmpfacelink.stream.webrtc.WebRtcSignalingClient], and
 * [io.github.kmpfacelink.stream.livekit.LiveKitDataChannelPublisher].
 *
 * Subclasses override hooks ([onConnected], [onDisconnected],
 * [onConnectionFailed], [onFrame]) to plug in their specific behavior.
 *
 * All subclasses automatically implement [Releasable] with thread-safe
 * atomic CAS on [released]. Override [release] to add custom cleanup
 * but always call `super.release()`.
 *
 * @param url WebSocket URL to connect to
 * @param httpClient Optional pre-configured Ktor [HttpClient].
 *        If null, a default client with WebSocket support is created.
 * @param tag Logging tag for this client
 */
@OptIn(ExperimentalAtomicApi::class)
public abstract class BaseWebSocketClient(
    private val url: String,
    httpClient: HttpClient? = null,
    private val tag: String,
) : Releasable {
    protected val mutex: Mutex = Mutex()
    private var session: WebSocketSession? = null
    private var connectionJob: Job? = null

    /** Atomic flag ensuring [release] runs at most once. */
    protected val released: AtomicBoolean = AtomicBoolean(false)

    /** Mutable connection state. Subclasses may update this directly. */
    protected val mutableConnectionState: MutableStateFlow<ConnectionState> =
        MutableStateFlow(ConnectionState.DISCONNECTED)

    /** Observable connection state for this client. */
    public val connectionState: StateFlow<ConnectionState> =
        mutableConnectionState.asStateFlow()

    protected val client: HttpClient = httpClient ?: HttpClient {
        install(WebSockets)
    }

    /** Whether an externally-provided client was used (true = caller owns it). */
    protected val ownsClient: Boolean = httpClient == null

    /** Whether a WebSocket session is currently active. */
    public val isSessionActive: Boolean
        get() = session != null

    /**
     * Returns the URL used for the WebSocket connection.
     *
     * The default implementation returns the [url] passed to the constructor.
     * Override this to build dynamic URLs (e.g. with query parameters).
     */
    protected open fun resolveUrl(): String = url

    /**
     * Opens the WebSocket connection.
     *
     * Cancels any existing connection first, then launches a new one in [scope].
     */
    public suspend fun connect(scope: CoroutineScope) {
        connectionJob?.cancel()
        mutableConnectionState.value = ConnectionState.CONNECTING
        onConnecting()
        connectionJob = scope.launch { establishConnection() }
    }

    /**
     * Closes the WebSocket connection and releases the session.
     */
    public open suspend fun disconnect() {
        connectionJob?.cancel()
        connectionJob = null
        mutex.withLock {
            session?.close()
            session = null
        }
        mutableConnectionState.value = ConnectionState.DISCONNECTED
        onDisconnected()
    }

    /**
     * Releases all resources held by this client.
     *
     * Uses atomic CAS to ensure cleanup runs exactly once, even when
     * called concurrently from multiple threads. Safe to call multiple
     * times. Subclasses should override and call `super.release()`.
     */
    public override fun release() {
        if (!released.compareAndSet(expectedValue = false, newValue = true)) return
        connectionJob?.cancel()
        connectionJob = null
        mutableConnectionState.value = ConnectionState.DISCONNECTED
        if (ownsClient) {
            client.close()
        }
    }

    /**
     * Called when [connect] is invoked, before the connection attempt starts.
     * Default implementation does nothing.
     */
    protected open suspend fun onConnecting() { /* no-op */ }

    /**
     * Called when the WebSocket session is established.
     *
     * @param ws The active WebSocket session
     */
    protected open suspend fun onConnected(ws: WebSocketSession) { /* no-op */ }

    /**
     * Called when the connection is closed or [disconnect] is invoked.
     */
    protected open suspend fun onDisconnected() { /* no-op */ }

    /**
     * Called when [establishConnection] catches a non-cancellation exception.
     *
     * @param error The exception that caused the connection failure
     */
    protected open suspend fun onConnectionFailed(error: Exception) { /* no-op */ }

    /**
     * Called for each incoming WebSocket frame.
     *
     * @param frame The received frame
     */
    protected open suspend fun onFrame(frame: Frame) { /* no-op */ }

    /**
     * Acquires the current session under the mutex lock.
     *
     * @return The active session, or null if not connected
     */
    protected suspend fun acquireSession(): WebSocketSession? =
        mutex.withLock { session }

    /**
     * Clears the current session under the mutex lock.
     */
    protected suspend fun clearSession() {
        mutex.withLock { session = null }
    }

    /**
     * Sends a frame on the current session under the mutex lock.
     *
     * @param frame The frame to send
     * @throws IllegalStateException if the session is not connected
     */
    protected suspend fun sendFrame(frame: Frame) {
        mutex.withLock {
            val currentSession = checkNotNull(session) { "Not connected" }
            currentSession.outgoing.send(frame)
        }
    }

    protected suspend fun establishConnection() {
        try {
            client.webSocket(urlString = resolveUrl()) {
                mutex.withLock { session = this }
                mutableConnectionState.value = ConnectionState.CONNECTED
                onConnected(this)
                receiveLoop(this)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
            mutex.withLock { session = null }
            mutableConnectionState.value = ConnectionState.DISCONNECTED
            FaceLinkLogger.w(tag, "Connection failed (${e::class.simpleName}): ${e.message}")
            onConnectionFailed(e)
        }
    }

    private suspend fun receiveLoop(ws: WebSocketSession) {
        for (frame in ws.incoming) {
            onFrame(frame)
        }
        // Connection closed by remote
        mutex.withLock { session = null }
        mutableConnectionState.value = ConnectionState.DISCONNECTED
        onDisconnected()
    }
}
