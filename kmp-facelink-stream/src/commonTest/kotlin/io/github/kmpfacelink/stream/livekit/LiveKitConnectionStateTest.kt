package io.github.kmpfacelink.stream.livekit

import kotlin.test.Test
import kotlin.test.assertEquals

class LiveKitConnectionStateTest {

    @Test
    fun hasExpectedValues() {
        val values = LiveKitConnectionState.entries
        assertEquals(4, values.size)
        assertEquals(LiveKitConnectionState.DISCONNECTED, values[0])
        assertEquals(LiveKitConnectionState.CONNECTING, values[1])
        assertEquals(LiveKitConnectionState.CONNECTED, values[2])
        assertEquals(LiveKitConnectionState.RECONNECTING, values[3])
    }

    @Test
    fun valueOfReturnsCorrectState() {
        assertEquals(
            LiveKitConnectionState.CONNECTED,
            LiveKitConnectionState.valueOf("CONNECTED"),
        )
        assertEquals(
            LiveKitConnectionState.DISCONNECTED,
            LiveKitConnectionState.valueOf("DISCONNECTED"),
        )
    }
}
