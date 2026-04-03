package io.github.kmpfacelink.stream.livekit

/**
 * Represents the connection state of a LiveKit tracking bridge.
 */
public enum class LiveKitConnectionState {
    /** Not connected and no connection attempt in progress. */
    DISCONNECTED,

    /** Actively attempting to establish a connection. */
    CONNECTING,

    /** Connected and ready to publish tracking data. */
    CONNECTED,

    /** Connection was lost; attempting to reconnect. */
    RECONNECTING,
}
