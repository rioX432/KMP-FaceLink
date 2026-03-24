package io.github.kmpfacelink.networking.discovery

import kotlinx.coroutines.flow.StateFlow

/**
 * Platform-abstracted mDNS/DNS-SD service discovery.
 *
 * Discovers network services advertised via mDNS (Bonjour on iOS, NsdManager on Android).
 * Emits discovery events through [events] and maintains a snapshot of currently
 * discovered services in [services].
 *
 * Usage:
 * ```kotlin
 * val discovery = ServiceDiscovery(context) // Android needs Context
 * discovery.startDiscovery(ServiceType.IFACIALMOCAP)
 * discovery.services.collect { services ->
 *     // Update UI with discovered receivers
 * }
 * // When done:
 * discovery.stopDiscovery()
 * ```
 *
 * Thread-safe: all state mutations are synchronized internally.
 */
public expect class ServiceDiscovery {

    /**
     * Flow of discovery events (found, lost, error).
     *
     * Subscribe to this flow to receive real-time updates about
     * service availability changes on the network.
     */
    public val events: StateFlow<DiscoveryEvent?>

    /**
     * Current snapshot of all discovered and resolved services.
     *
     * Updated automatically as services appear and disappear.
     */
    public val services: StateFlow<List<DiscoveredService>>

    /**
     * Whether discovery is currently active.
     */
    public val isDiscovering: StateFlow<Boolean>

    /**
     * Starts discovering services of the given type on the local network.
     *
     * If discovery is already active, it is stopped first before restarting
     * with the new service type.
     *
     * @param serviceType DNS-SD service type to discover (e.g., [ServiceType.IFACIALMOCAP])
     */
    public fun startDiscovery(serviceType: String)

    /**
     * Starts discovering all known KMP-FaceLink-compatible service types.
     *
     * Equivalent to calling [startDiscovery] for each type in [ServiceType.ALL],
     * but implemented as sequential scans since most platforms only support
     * one active discovery per service type.
     */
    public fun startDiscoveryAll()

    /**
     * Stops all active discovery operations and clears discovered services.
     */
    public fun stopDiscovery()
}
