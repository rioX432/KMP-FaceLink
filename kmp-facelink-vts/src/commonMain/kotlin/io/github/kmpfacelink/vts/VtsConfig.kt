package io.github.kmpfacelink.vts

/**
 * Configuration for VTube Studio Plugin API connection.
 *
 * @property host WebSocket server host. Default is localhost.
 * @property port WebSocket server port. Default is [DEFAULT_PORT] (8001).
 * @property pluginName Display name of the plugin (3-32 characters).
 * @property pluginDeveloper Developer name (3-32 characters).
 * @property pluginIcon Optional base64-encoded PNG/JPG icon (128x128 pixels).
 * @property authToken Previously obtained authentication token, or null for first-time auth.
 */
public data class VtsConfig(
    val host: String = DEFAULT_HOST,
    val port: Int = DEFAULT_PORT,
    val pluginName: String = DEFAULT_PLUGIN_NAME,
    val pluginDeveloper: String = DEFAULT_PLUGIN_DEVELOPER,
    val pluginIcon: String? = null,
    val authToken: String? = null,
) {
    public companion object {
        /** Default VTube Studio API WebSocket port. */
        public const val DEFAULT_PORT: Int = 8001

        /** Default host (localhost). */
        public const val DEFAULT_HOST: String = "127.0.0.1"

        /** Default plugin name. */
        public const val DEFAULT_PLUGIN_NAME: String = "KMP-FaceLink"

        /** Default developer name. */
        public const val DEFAULT_PLUGIN_DEVELOPER: String = "KMP-FaceLink"

        /** Minimum plugin/developer name length per VTS API spec. */
        internal const val MIN_NAME_LENGTH: Int = 3

        /** Maximum plugin/developer name length per VTS API spec. */
        internal const val MAX_NAME_LENGTH: Int = 32
    }

    init {
        require(port in 1..MAX_PORT) { "port must be between 1 and $MAX_PORT" }
        require(pluginName.length in MIN_NAME_LENGTH..MAX_NAME_LENGTH) {
            "pluginName must be $MIN_NAME_LENGTH-$MAX_NAME_LENGTH characters"
        }
        require(pluginDeveloper.length in MIN_NAME_LENGTH..MAX_NAME_LENGTH) {
            "pluginDeveloper must be $MIN_NAME_LENGTH-$MAX_NAME_LENGTH characters"
        }
    }
}

private const val MAX_PORT = 65535
