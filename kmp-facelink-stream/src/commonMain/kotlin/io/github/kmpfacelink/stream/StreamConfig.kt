package io.github.kmpfacelink.stream

/**
 * Base configuration for streaming clients.
 *
 * @property host Hostname of the streaming target
 * @property port Port number of the streaming target
 * @property maxFps Maximum frames per second for parameter updates
 */
public open class StreamConfig(
    public val host: String = "localhost",
    public val port: Int = DEFAULT_PORT,
    public val maxFps: Int = DEFAULT_MAX_FPS,
) {
    public companion object {
        /** Default WebSocket port for VTubeStudio. */
        public const val DEFAULT_PORT: Int = 8001

        /** Default maximum FPS for parameter streaming. */
        public const val DEFAULT_MAX_FPS: Int = 30
    }
}
