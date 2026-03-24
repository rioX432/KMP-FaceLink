package io.github.kmpfacelink.vmc

/**
 * Configuration for VMC Protocol communication.
 *
 * @property host Target host address for the VMC receiver (Marionette)
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

        /** Default target host (localhost). */
        public const val DEFAULT_HOST: String = "127.0.0.1"
    }
}
