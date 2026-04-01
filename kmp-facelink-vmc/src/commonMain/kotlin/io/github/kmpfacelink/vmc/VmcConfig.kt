package io.github.kmpfacelink.vmc

/**
 * Configuration for VMC Protocol communication.
 *
 * By default, [host] is set to [DEFAULT_HOST] (`127.0.0.1`), which restricts
 * communication to the local machine. To send tracking data to a remote VMC
 * receiver (e.g., a VR application on another device), supply the receiver's
 * IP address or resolvable hostname.
 *
 * @property host Target host address for the VMC receiver (Marionette).
 *           Defaults to [DEFAULT_HOST] (localhost / `127.0.0.1`).
 * @property port Target UDP port. Default is [DEFAULT_MARIONETTE_PORT] (39539).
 * @property sendBundles When true, groups all messages per frame into an OSC bundle.
 *           Reduces packet count but increases individual packet size.
 */
public data class VmcConfig(
    val host: String = DEFAULT_HOST,
    val port: Int = DEFAULT_MARIONETTE_PORT,
    val sendBundles: Boolean = true,
) {
    public companion object {
        /** Default Marionette (receiver) port per VMC Protocol spec. */
        public const val DEFAULT_MARIONETTE_PORT: Int = 39539

        /** Default Performer (assistant server) port per VMC Protocol spec. */
        public const val DEFAULT_PERFORMER_PORT: Int = 39540

        /**
         * Default target host address (`127.0.0.1` — localhost).
         *
         * This value targets only the local loopback interface, meaning the sender
         * and receiver must run on the same machine. For remote receivers, pass the
         * target device's IP address or hostname to [VmcConfig.host] instead.
         */
        public const val DEFAULT_HOST: String = "127.0.0.1"
    }
}
