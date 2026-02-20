package io.github.kmpfacelink.stream.internal

/**
 * Internal abstraction over WebSocket connections for testability.
 */
internal interface WebSocketProvider {
    /**
     * Opens a WebSocket connection to the given URL.
     *
     * @param url The WebSocket URL to connect to (e.g., "ws://localhost:8001")
     * @param onMessage Callback invoked when a text message is received
     * @param onClose Callback invoked when the connection closes
     * @param onError Callback invoked when an error occurs
     */
    suspend fun connect(
        url: String,
        onMessage: suspend (String) -> Unit,
        onClose: suspend (reason: String?) -> Unit,
        onError: suspend (Throwable) -> Unit,
    )

    /** Sends a text message over the connection. */
    suspend fun send(text: String)

    /** Closes the connection gracefully. */
    suspend fun close()

    /** Whether the connection is currently open. */
    val isConnected: Boolean
}
