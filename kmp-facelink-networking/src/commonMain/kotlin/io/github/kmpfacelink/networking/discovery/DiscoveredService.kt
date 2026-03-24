package io.github.kmpfacelink.networking.discovery

/**
 * Represents a network service discovered via mDNS/DNS-SD.
 *
 * @property name Human-readable service instance name (e.g., "My VTube Studio")
 * @property type Service type string (e.g., "_ifacialmocap._udp.")
 * @property host Resolved host address (IP). Null if not yet resolved.
 * @property port Resolved port number. Zero if not yet resolved.
 */
public data class DiscoveredService(
    val name: String,
    val type: String,
    val host: String?,
    val port: Int,
)
