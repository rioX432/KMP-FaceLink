package io.github.kmpfacelink.stream

import io.github.kmpfacelink.api.Releasable
import kotlinx.coroutines.flow.StateFlow

/**
 * Sealed class representing the connection state of a [StreamClient].
 */
public sealed class StreamState {
    /** Not connected to any streaming target. */
    public data object Disconnected : StreamState()

    /** Establishing WebSocket connection. */
    public data object Connecting : StreamState()

    /** WebSocket connected, performing protocol-specific authentication. */
    public data object Authenticating : StreamState()

    /** Fully connected and ready to send parameters. */
    public data object Connected : StreamState()

    /** Attempting to reconnect after a connection loss. */
    public data class Reconnecting(val attempt: Int) : StreamState()

    /** An error occurred. */
    public data class Error(val message: String, val willRetry: Boolean) : StreamState()
}

/**
 * Interface for streaming face tracking parameters to external applications.
 *
 * Implementations handle protocol-specific connection, authentication, and data transmission.
 */
public interface StreamClient : Releasable {
    /** Current connection state as a [StateFlow]. */
    public val state: StateFlow<StreamState>

    /** Establishes a connection to the streaming target. */
    public suspend fun connect()

    /** Gracefully disconnects from the streaming target. */
    public suspend fun disconnect()

    /**
     * Sends parameter values to the streaming target.
     *
     * @param parameters Map of parameter IDs to their float values
     * @param faceFound Whether a face is currently being tracked
     */
    public suspend fun sendParameters(parameters: Map<String, Float>, faceFound: Boolean)

    /** Releases all resources held by this client. */
    public override fun release()
}
