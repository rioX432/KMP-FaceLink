package io.github.kmpfacelink.stream.webrtc

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Configuration for a WebRTC DataChannel transport connection.
 *
 * @property signalingUrl WebSocket URL for SDP/ICE signaling exchange
 *           (e.g. "ws://192.168.1.10:21412/signaling").
 * @property iceServers List of ICE (STUN/TURN) servers for NAT traversal.
 *           For LAN-only use, this can be empty (host candidates suffice).
 * @property dataChannelConfig Configuration for the SCTP data channel.
 */
@Serializable
public data class WebRtcTransportConfig(
    @SerialName("signaling_url") val signalingUrl: String,
    @SerialName("ice_servers") val iceServers: List<IceServer> = emptyList(),
    @SerialName("data_channel") val dataChannelConfig: DataChannelConfig = DataChannelConfig(),
) {
    init {
        require(signalingUrl.isNotBlank()) { "signalingUrl must not be blank" }
    }
}

/**
 * ICE server configuration for STUN/TURN.
 *
 * @property urls STUN or TURN server URLs (e.g. "stun:stun.l.google.com:19302").
 * @property username Username for TURN authentication (null for STUN).
 * @property credential Credential for TURN authentication (null for STUN).
 */
@Serializable
public data class IceServer(
    @SerialName("urls") val urls: List<String>,
    @SerialName("username") val username: String? = null,
    @SerialName("credential") val credential: String? = null,
)

/**
 * SCTP data channel configuration for WebRTC.
 *
 * Controls reliability and ordering behavior of the data channel.
 * For real-time tracking data, unreliable + unordered mode is recommended
 * as each frame is self-contained (full state, not deltas).
 *
 * @property label Human-readable label for the data channel.
 * @property ordered Whether messages must be delivered in order.
 *           Set to false for lowest latency (unordered delivery).
 * @property maxRetransmits Maximum number of retransmission attempts.
 *           Set to 0 for unreliable delivery (no retransmits).
 *           Mutually exclusive with [maxPacketLifeTimeMs].
 * @property maxPacketLifeTimeMs Maximum time in milliseconds to attempt
 *           retransmission. Mutually exclusive with [maxRetransmits].
 *           Null means no time-based limit.
 * @property protocol Sub-protocol name (empty string for default).
 */
@Serializable
public data class DataChannelConfig(
    @SerialName("label") val label: String = DEFAULT_LABEL,
    @SerialName("ordered") val ordered: Boolean = false,
    @SerialName("max_retransmits") val maxRetransmits: Int? = 0,
    @SerialName("max_packet_life_time_ms") val maxPacketLifeTimeMs: Int? = null,
    @SerialName("protocol") val protocol: String = "",
) {
    init {
        require(maxRetransmits == null || maxPacketLifeTimeMs == null) {
            "maxRetransmits and maxPacketLifeTimeMs are mutually exclusive"
        }
        maxRetransmits?.let {
            require(it >= 0) { "maxRetransmits must be non-negative" }
        }
        maxPacketLifeTimeMs?.let {
            require(it > 0) { "maxPacketLifeTimeMs must be positive" }
        }
    }

    /** Whether the data channel is configured for unreliable delivery. */
    public val isUnreliable: Boolean
        get() = maxRetransmits == 0

    public companion object {
        /** Default data channel label. */
        public const val DEFAULT_LABEL: String = "tracking"
    }
}
