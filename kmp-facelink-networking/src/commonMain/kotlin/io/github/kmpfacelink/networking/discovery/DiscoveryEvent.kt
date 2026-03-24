package io.github.kmpfacelink.networking.discovery

/**
 * Events emitted during mDNS service discovery.
 */
public sealed class DiscoveryEvent {

    /**
     * A new service was found and resolved on the network.
     *
     * @property service The discovered and resolved service info
     */
    public data class Found(val service: DiscoveredService) : DiscoveryEvent()

    /**
     * A previously discovered service is no longer available.
     *
     * @property name The name of the service that was lost
     * @property type The service type of the service that was lost
     */
    public data class Lost(val name: String, val type: String) : DiscoveryEvent()

    /**
     * Discovery failed with an error.
     *
     * @property message Description of the failure
     */
    public data class Error(val message: String) : DiscoveryEvent()
}
