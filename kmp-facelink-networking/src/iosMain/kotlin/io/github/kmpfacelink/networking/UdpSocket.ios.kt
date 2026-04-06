@file:OptIn(ExperimentalForeignApi::class)

package io.github.kmpfacelink.networking

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.alloc
import kotlinx.cinterop.convert
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.sizeOf
import kotlinx.cinterop.usePinned
import platform.Foundation.NSLock
import platform.posix.AF_INET
import platform.posix.INADDR_ANY
import platform.posix.SOCK_DGRAM
import platform.posix.close
import platform.posix.errno
import platform.posix.recvfrom
import platform.posix.sendto
import platform.posix.sockaddr_in
import platform.posix.socket

/**
 * iOS [UdpSocket] implementation backed by POSIX sockets.
 *
 * All mutable state is guarded by [NSLock] to prevent a race
 * condition where concurrent callers could create duplicate
 * file descriptors inside [ensureSocket].
 */
public actual class UdpSocket actual constructor(
    private val bufferSize: Int,
) {
    private var fd: Int = -1
    private val lock = NSLock()

    /** Must be called with [lock] held. Creates the socket if not yet open. */
    private fun ensureSocketLocked(): Int {
        if (fd < 0) {
            fd = socket(AF_INET, SOCK_DGRAM, 0)
            check(fd >= 0) { "Failed to create UDP socket: errno=$errno" }
        }
        return fd
    }

    private fun ensureSocket(): Int {
        lock.lock()
        try {
            return ensureSocketLocked()
        } finally {
            lock.unlock()
        }
    }

    public actual fun send(data: ByteArray, host: String, port: Int) {
        lock.lock()
        try {
            val sock = ensureSocketLocked()
            memScoped {
                val addr = alloc<sockaddr_in>()
                addr.sin_family = AF_INET.convert()
                addr.sin_port = hostToNetworkShort(port)
                addr.sin_addr.s_addr = parseIpv4Address(host)

                data.usePinned { pinned ->
                    val result = sendto(
                        sock,
                        pinned.addressOf(0),
                        data.size.convert(),
                        0,
                        addr.ptr.reinterpret(),
                        sizeOf<sockaddr_in>().convert(),
                    )
                    check(result >= 0) { "UDP send failed: errno=$errno" }
                }
            }
        } finally {
            lock.unlock()
        }
    }

    public actual fun bind(port: Int) {
        lock.lock()
        try {
            closeInternal()
            fd = socket(AF_INET, SOCK_DGRAM, 0)
            check(fd >= 0) { "Failed to create UDP socket: errno=$errno" }

            memScoped {
                val addr = alloc<sockaddr_in>()
                addr.sin_family = AF_INET.convert()
                addr.sin_port = hostToNetworkShort(port)
                addr.sin_addr.s_addr = INADDR_ANY

                val result = platform.posix.bind(
                    fd,
                    addr.ptr.reinterpret(),
                    sizeOf<sockaddr_in>().convert(),
                )
                check(result == 0) { "UDP bind to port $port failed: errno=$errno" }
            }
        } finally {
            lock.unlock()
        }
    }

    public actual fun receive(): ByteArray {
        val sock = ensureSocket()
        val buf = ByteArray(bufferSize)
        val bytesRead = buf.usePinned { pinned ->
            recvfrom(sock, pinned.addressOf(0), buf.size.convert(), 0, null, null)
        }
        check(bytesRead >= 0) { "UDP receive failed: errno=$errno" }
        return buf.copyOf(bytesRead.toInt())
    }

    public actual fun close() {
        lock.lock()
        try {
            closeInternal()
        } finally {
            lock.unlock()
        }
    }

    private fun closeInternal() {
        if (fd >= 0) {
            close(fd)
            fd = -1
        }
    }
}

/**
 * Converts a host-order port number to network byte order (big-endian).
 * Equivalent to htons() which is a macro not directly available in K/N.
 */
@Suppress("MagicNumber")
internal fun hostToNetworkShort(value: Int): UShort {
    val v = value.toUShort()
    return (((v.toInt() and 0xFF) shl 8) or ((v.toInt() shr 8) and 0xFF)).toUShort()
}

/**
 * Parses a dotted-decimal IPv4 address string into a network-order 32-bit integer.
 * Equivalent to inet_addr() which may not be available in K/N on Apple targets.
 */
@Suppress("MagicNumber")
internal fun parseIpv4Address(host: String): UInt {
    val parts = host.split(".")
    require(parts.size == 4) { "Invalid IPv4 address: $host" }
    var result = 0u
    for (i in parts.indices) {
        val octet = parts[i].toUInt()
        require(octet <= 255u) { "Invalid IPv4 octet: ${parts[i]}" }
        // Network byte order: first octet is lowest byte
        result = result or (octet shl (i * 8))
    }
    return result
}
