package io.github.kmpfacelink.vmc.internal

import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress

internal actual class UdpSocket actual constructor() {
    private var socket: DatagramSocket? = null

    private fun ensureSocket(): DatagramSocket {
        return socket ?: DatagramSocket().also { socket = it }
    }

    actual fun send(data: ByteArray, host: String, port: Int) {
        val address = InetAddress.getByName(host)
        val packet = DatagramPacket(data, data.size, address, port)
        ensureSocket().send(packet)
    }

    actual fun bind(port: Int) {
        close()
        socket = DatagramSocket(port)
    }

    actual fun receive(): ByteArray {
        val buf = ByteArray(RECEIVE_BUFFER_SIZE)
        val packet = DatagramPacket(buf, buf.size)
        ensureSocket().receive(packet)
        return buf.copyOf(packet.length)
    }

    actual fun close() {
        socket?.close()
        socket = null
    }

    private companion object {
        const val RECEIVE_BUFFER_SIZE = 4096
    }
}
