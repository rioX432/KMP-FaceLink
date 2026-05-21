package io.github.kmpfacelink.interact.model

/**
 * Connection lifecycle states for a [StreamEventSource][io.github.kmpfacelink.interact.StreamEventSource].
 */
public enum class ConnectionState {
    /** Not connected and no connection attempt in progress. */
    DISCONNECTED,

    /** Actively attempting to establish a connection. */
    CONNECTING,

    /** Connected and receiving events. */
    CONNECTED,

    /** Connection lost; automatically attempting to reconnect. */
    RECONNECTING,
}
