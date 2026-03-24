package io.github.kmpfacelink.ifm.internal

/**
 * Platform-abstracted UDP socket for sending and receiving datagrams.
 */
internal expect class UdpSocket() {

    /**
     * Sends a UDP datagram to the specified host and port.
     */
    fun send(data: ByteArray, host: String, port: Int)

    /**
     * Binds the socket to the specified port for receiving datagrams.
     */
    fun bind(port: Int)

    /**
     * Receives a UDP datagram. Blocks until data is available.
     * Returns the raw bytes of the received packet.
     */
    fun receive(): ByteArray

    /**
     * Closes the socket and releases all resources.
     */
    fun close()
}
