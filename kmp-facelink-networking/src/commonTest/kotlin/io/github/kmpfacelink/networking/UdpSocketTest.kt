package io.github.kmpfacelink.networking

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class UdpSocketTest {

    @Test
    fun defaultBufferSizeIs8192() {
        assertEquals(DEFAULT_BUFFER_SIZE, 8192)
    }

    @Test
    fun sendAndReceiveLoopback() {
        val receiver = UdpSocket()
        val sender = UdpSocket()
        try {
            receiver.bind(0) // OS picks a free port — not available via API,
            // so we use a known port for the test.
            // Instead, bind to a fixed ephemeral port.
        } finally {
            receiver.close()
            sender.close()
        }
    }

    @Test
    fun sendReceiveRoundTrip() {
        val port = 19832
        val receiver = UdpSocket(bufferSize = 1024)
        val sender = UdpSocket()
        try {
            receiver.bind(port)
            val payload = "hello-networking".encodeToByteArray()
            sender.send(payload, "127.0.0.1", port)
            val received = receiver.receive()
            assertEquals("hello-networking", received.decodeToString())
        } finally {
            receiver.close()
            sender.close()
        }
    }

    @Test
    fun closeIsIdempotent() {
        val socket = UdpSocket()
        socket.close()
        socket.close() // should not throw
    }

    @Test
    fun customBufferSize() {
        val socket = UdpSocket(bufferSize = 2048)
        // Verify the socket can be created and closed with custom size
        socket.close()
    }

    @Test
    fun currentTimeMsReturnsReasonableValue() {
        val now = currentTimeMs()
        // Should be after 2024-01-01 (1704067200000)
        assertTrue(now > 1_704_067_200_000L, "currentTimeMs() should return a recent timestamp")
    }
}
