package io.github.kmpfacelink.stream.webrtc

import io.github.kmpfacelink.stream.ConnectionState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/**
 * Transport abstraction for sending and receiving binary tracking data.
 *
 * Implementations provide a bidirectional byte-oriented channel.
 * The [connect] / [disconnect] lifecycle is managed by the caller,
 * and data flows through [send] / [incoming].
 *
 * Two built-in implementations exist:
 * - [WebSocketTransport] — wraps Ktor WebSocket client (TCP, reliable)
 * - Future: WebRTC DataChannel transport (UDP, configurable reliability)
 */
public interface DataChannelTransport {

    /** Whether the transport is currently connected and able to send data. */
    public val isConnected: Boolean

    /** Observable connection state changes. */
    public val connectionState: StateFlow<ConnectionState>

    /**
     * Opens the transport connection.
     *
     * After this call returns successfully, [isConnected] is true and
     * [incoming] begins emitting received messages.
     *
     * @param scope Coroutine scope for the connection lifecycle.
     *              The caller owns this scope and is responsible for cancellation.
     */
    public suspend fun connect(scope: CoroutineScope)

    /**
     * Sends a binary message to the remote peer.
     *
     * @param data The raw bytes to send
     * @throws IllegalStateException if the transport is not connected
     */
    public suspend fun send(data: ByteArray)

    /**
     * Flow of binary messages received from the remote peer.
     *
     * The flow emits messages as long as the transport is connected.
     * It completes when the connection is closed.
     */
    public val incoming: Flow<ByteArray>

    /**
     * Closes the transport connection and releases resources.
     *
     * After this call, [isConnected] is false and [incoming] completes.
     */
    public suspend fun disconnect()
}
