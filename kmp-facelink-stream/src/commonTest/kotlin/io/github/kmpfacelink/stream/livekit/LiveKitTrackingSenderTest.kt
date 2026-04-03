package io.github.kmpfacelink.stream.livekit

import io.github.kmpfacelink.stream.StreamFrame
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class LiveKitTrackingSenderTest {

    @Test
    fun initialConnectionStateIsDisconnected() {
        val config = LiveKitBridgeConfig(
            serverUrl = "wss://example.com",
            token = "test-token",
            roomName = "room",
        )
        val sender = LiveKitTrackingSender(config)

        assertEquals(
            LiveKitConnectionState.DISCONNECTED,
            sender.connectionState.value,
        )
    }

    @Test
    fun publisherInitiallyNotConnected() {
        val config = LiveKitBridgeConfig(
            serverUrl = "wss://example.com",
            token = "test-token",
            roomName = "room",
        )
        val publisher = LiveKitDataChannelPublisher(config)

        assertFalse(publisher.isConnected)
        assertEquals(
            LiveKitConnectionState.DISCONNECTED,
            publisher.connectionState.value,
        )
    }
}

/**
 * Fake publisher that records published frames for testing.
 */
internal class FakeTrackingDataPublisher : TrackingDataPublisher {
    private val mutex = Mutex()
    private val _publishedFrames = mutableListOf<StreamFrame>()
    val publishedFrames: List<StreamFrame> get() = _publishedFrames

    private var _connected = false
    override val isConnected: Boolean get() = _connected

    override suspend fun connect(scope: CoroutineScope) {
        _connected = true
    }

    override suspend fun publish(frame: StreamFrame) {
        mutex.withLock { _publishedFrames.add(frame) }
    }

    override suspend fun disconnect() {
        _connected = false
    }
}
