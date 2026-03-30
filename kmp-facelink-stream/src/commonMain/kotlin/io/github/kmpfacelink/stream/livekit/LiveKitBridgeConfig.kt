package io.github.kmpfacelink.stream.livekit

/**
 * Configuration for connecting to a LiveKit room and publishing tracking data.
 *
 * @property serverUrl LiveKit server WebSocket URL (e.g. "wss://my-livekit.example.com").
 * @property token Access token for LiveKit room authentication (JWT).
 * @property roomName Name of the LiveKit room to join.
 * @property publishRateHz Rate at which tracking frames are published (frames per second).
 *           Capped to [MAX_PUBLISH_RATE_HZ].
 * @property participantIdentity Identity string for this tracking data source.
 * @property dataChannelLabel Label for the data channel used to send tracking data.
 * @property reconnectDelayMs Delay in milliseconds before attempting reconnection.
 * @property maxReconnectAttempts Maximum number of reconnection attempts before giving up.
 *           Use 0 for no retries.
 */
public data class LiveKitBridgeConfig(
    val serverUrl: String,
    val token: String,
    val roomName: String,
    val publishRateHz: Int = DEFAULT_PUBLISH_RATE_HZ,
    val participantIdentity: String = DEFAULT_PARTICIPANT_IDENTITY,
    val dataChannelLabel: String = DEFAULT_DATA_CHANNEL_LABEL,
    val reconnectDelayMs: Long = DEFAULT_RECONNECT_DELAY_MS,
    val maxReconnectAttempts: Int = DEFAULT_MAX_RECONNECT_ATTEMPTS,
) {
    public companion object {
        /** Default publish rate (30 fps). */
        public const val DEFAULT_PUBLISH_RATE_HZ: Int = 30

        /** Maximum allowed publish rate. */
        public const val MAX_PUBLISH_RATE_HZ: Int = 60

        /** Default participant identity. */
        public const val DEFAULT_PARTICIPANT_IDENTITY: String = "kmp-facelink"

        /** Default data channel label. */
        public const val DEFAULT_DATA_CHANNEL_LABEL: String = "tracking"

        /** Default reconnect delay (2 seconds). */
        public const val DEFAULT_RECONNECT_DELAY_MS: Long = 2000L

        /** Default maximum reconnect attempts. */
        public const val DEFAULT_MAX_RECONNECT_ATTEMPTS: Int = 5
    }

    init {
        require(serverUrl.isNotBlank()) { "serverUrl must not be blank" }
        require(token.isNotBlank()) { "token must not be blank" }
        require(roomName.isNotBlank()) { "roomName must not be blank" }
        require(publishRateHz in 1..MAX_PUBLISH_RATE_HZ) {
            "publishRateHz must be between 1 and $MAX_PUBLISH_RATE_HZ"
        }
        require(reconnectDelayMs > 0) { "reconnectDelayMs must be positive" }
        require(maxReconnectAttempts >= 0) { "maxReconnectAttempts must be non-negative" }
    }
}
