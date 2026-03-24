package io.github.kmpfacelink.stream

/**
 * Configuration for the WebSocket streaming server.
 *
 * @property port TCP port for the WebSocket server. Default is [DEFAULT_PORT] (21412).
 * @property host Bind address. Default is "0.0.0.0" (all interfaces).
 * @property targetFps Target frames per second for streaming. Capped to [MAX_FPS].
 * @property format Wire format for tracking data frames.
 * @property pingIntervalMs Interval between WebSocket ping frames in milliseconds.
 *           Used for connection liveness detection.
 * @property timeoutMs WebSocket idle timeout in milliseconds.
 */
public data class StreamConfig(
    val host: String = DEFAULT_HOST,
    val port: Int = DEFAULT_PORT,
    val targetFps: Int = DEFAULT_FPS,
    val format: StreamFormat = StreamFormat.JSON,
    val pingIntervalMs: Long = DEFAULT_PING_INTERVAL_MS,
    val timeoutMs: Long = DEFAULT_TIMEOUT_MS,
) {
    public companion object {
        /** Default WebSocket server port. */
        public const val DEFAULT_PORT: Int = 21412

        /** Default bind address (all interfaces). */
        public const val DEFAULT_HOST: String = "0.0.0.0"

        /** Default target FPS. */
        public const val DEFAULT_FPS: Int = 30

        /** Maximum allowed FPS. */
        public const val MAX_FPS: Int = 60

        /** Default ping interval (5 seconds). */
        public const val DEFAULT_PING_INTERVAL_MS: Long = 5000L

        /** Default idle timeout (15 seconds). */
        public const val DEFAULT_TIMEOUT_MS: Long = 15000L
    }

    init {
        require(targetFps in 1..MAX_FPS) { "targetFps must be between 1 and $MAX_FPS" }
        require(port in 1..MAX_PORT) { "port must be between 1 and $MAX_PORT" }
    }
}

/**
 * Wire format for streaming tracking data.
 */
public enum class StreamFormat {
    /** JSON text frames — human-readable, easy to debug. */
    JSON,

    /** Compact binary frames — lower bandwidth, faster serialization. */
    BINARY,
}

private const val MAX_PORT = 65535
