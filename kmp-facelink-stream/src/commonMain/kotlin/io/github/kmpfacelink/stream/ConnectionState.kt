package io.github.kmpfacelink.stream

/**
 * Common connection state for all WebSocket-based clients.
 *
 * Provides a unified state model across [BaseWebSocketClient] subclasses.
 * Clients that need additional states (e.g. reconnecting) can define their
 * own mapping on top of this enum.
 */
public enum class ConnectionState {
    /** Not connected and no connection attempt in progress. */
    DISCONNECTED,

    /** Actively attempting to establish a connection. */
    CONNECTING,

    /** Connected and ready to send/receive data. */
    CONNECTED,
}
