package io.github.kmpfacelink.networking

/**
 * Default receive buffer size in bytes.
 * Large enough for VMC bundles and iFacialMocap messages.
 */
public const val DEFAULT_BUFFER_SIZE: Int = 8192

/**
 * Platform-abstracted UDP socket for sending and receiving datagrams.
 *
 * Thread-safe: concurrent calls to [send], [bind], [receive], and [close]
 * are synchronized to prevent race conditions during lazy socket creation.
 *
 * Used by [io.github.kmpfacelink.vmc.VmcSender], [io.github.kmpfacelink.vmc.VmcReceiver],
 * and [io.github.kmpfacelink.ifm.IfmSender] for protocol transport.
 *
 * @param bufferSize receive buffer size in bytes (default [DEFAULT_BUFFER_SIZE])
 */
public expect class UdpSocket(bufferSize: Int = DEFAULT_BUFFER_SIZE) {

    /**
     * Sends a UDP datagram to the specified host and port.
     */
    public fun send(data: ByteArray, host: String, port: Int)

    /**
     * Binds the socket to the specified port for receiving datagrams.
     */
    public fun bind(port: Int)

    /**
     * Receives a UDP datagram. Blocks until data is available.
     * Returns the raw bytes of the received packet.
     */
    public fun receive(): ByteArray

    /**
     * Closes the socket and releases all resources.
     */
    public fun close()
}
