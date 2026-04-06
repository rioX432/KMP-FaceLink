package io.github.kmpfacelink.networking

import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress

/**
 * Android [UdpSocket] implementation backed by [DatagramSocket].
 *
 * All mutable state is guarded by [Synchronized] to prevent a race
 * condition where concurrent callers could create duplicate sockets
 * inside [ensureSocket].
 */
public actual class UdpSocket actual constructor(
    private val bufferSize: Int,
) {
    private var socket: DatagramSocket? = null

    @Synchronized
    private fun ensureSocket(): DatagramSocket {
        return socket ?: DatagramSocket().also { socket = it }
    }

    @Synchronized
    public actual fun send(data: ByteArray, host: String, port: Int) {
        val address = InetAddress.getByName(host)
        val packet = DatagramPacket(data, data.size, address, port)
        ensureSocket().send(packet)
    }

    @Synchronized
    public actual fun bind(port: Int) {
        close()
        socket = DatagramSocket(port)
    }

    @Synchronized
    public actual fun receive(): ByteArray {
        val buf = ByteArray(bufferSize)
        val packet = DatagramPacket(buf, buf.size)
        ensureSocket().receive(packet)
        return buf.copyOf(packet.length)
    }

    @Synchronized
    public actual fun close() {
        socket?.close()
        socket = null
    }
}
