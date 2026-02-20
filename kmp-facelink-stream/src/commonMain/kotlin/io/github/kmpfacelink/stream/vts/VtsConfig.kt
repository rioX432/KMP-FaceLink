package io.github.kmpfacelink.stream.vts

import io.github.kmpfacelink.stream.StreamConfig

/**
 * Reconnection policy for VTubeStudio connections.
 *
 * @property maxAttempts Maximum number of reconnection attempts (0 = no retry)
 * @property initialDelayMs Initial delay before first reconnection attempt
 * @property maxDelayMs Maximum delay between reconnection attempts
 * @property backoffMultiplier Multiplier applied to delay after each failed attempt
 */
public class VtsReconnectPolicy(
    public val maxAttempts: Int = DEFAULT_MAX_ATTEMPTS,
    public val initialDelayMs: Long = DEFAULT_INITIAL_DELAY_MS,
    public val maxDelayMs: Long = DEFAULT_MAX_DELAY_MS,
    public val backoffMultiplier: Double = DEFAULT_BACKOFF_MULTIPLIER,
) {
    public companion object {
        private const val DEFAULT_MAX_ATTEMPTS = 5
        private const val DEFAULT_INITIAL_DELAY_MS = 1000L
        private const val DEFAULT_MAX_DELAY_MS = 30000L
        private const val DEFAULT_BACKOFF_MULTIPLIER = 2.0

        /** No automatic reconnection. */
        public val NONE: VtsReconnectPolicy = VtsReconnectPolicy(maxAttempts = 0)
    }
}

/**
 * Configuration for connecting to VTubeStudio via its WebSocket API.
 *
 * @property pluginName Name displayed in the VTS approval dialog
 * @property pluginDeveloper Developer name displayed in the VTS approval dialog
 * @property pluginIcon Optional base64-encoded PNG icon (256x256)
 * @property authToken Saved authentication token for re-authentication
 * @property onTokenReceived Callback invoked when a new auth token is received, for persistence
 * @property reconnectPolicy Policy for automatic reconnection on connection loss
 */
@Suppress("LongParameterList")
public class VtsConfig(
    host: String = "localhost",
    port: Int = StreamConfig.DEFAULT_PORT,
    maxFps: Int = StreamConfig.DEFAULT_MAX_FPS,
    public val pluginName: String,
    public val pluginDeveloper: String,
    public val pluginIcon: String? = null,
    public val authToken: String? = null,
    public val onTokenReceived: (suspend (String) -> Unit)? = null,
    public val reconnectPolicy: VtsReconnectPolicy = VtsReconnectPolicy(),
) : StreamConfig(host, port, maxFps)
