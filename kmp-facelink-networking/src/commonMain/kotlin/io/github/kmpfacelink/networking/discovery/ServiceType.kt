package io.github.kmpfacelink.networking.discovery

/**
 * Well-known mDNS/DNS-SD service type constants for face tracking protocols.
 *
 * These follow the DNS-SD naming convention: `_protocol._transport.`
 */
public object ServiceType {

    /**
     * KMP-FaceLink receiver service type.
     *
     * Custom service type for KMP-FaceLink SDK receivers.
     * Uses UDP transport since tracking data is sent via UDP datagrams.
     */
    public const val KMP_FACELINK: String = "_kmpfacelink._udp."

    /**
     * iFacialMocap receiver service type.
     *
     * Used by iFacialMocap, VTube Studio, and compatible apps
     * to advertise their presence for face tracking data reception.
     */
    public const val IFACIALMOCAP: String = "_ifacialmocap._udp."

    /**
     * VTube Studio API service type.
     *
     * Used by VTube Studio to advertise its Plugin API endpoint
     * for WebSocket-based communication.
     */
    public const val VTUBE_STUDIO: String = "_vstudio._tcp."

    /**
     * VMC Protocol receiver service type.
     *
     * Used by VMC Protocol-compatible applications (e.g., VSeeFace, VMagicMirror)
     * to advertise their marionette (receiver) endpoint.
     */
    public const val VMC_PROTOCOL: String = "_vmc._udp."

    /**
     * All service types that [ServiceDiscovery] can discover.
     *
     * Useful for scanning the network for any compatible receiver.
     */
    public val ALL: List<String> = listOf(
        KMP_FACELINK,
        IFACIALMOCAP,
        VTUBE_STUDIO,
        VMC_PROTOCOL,
    )
}
