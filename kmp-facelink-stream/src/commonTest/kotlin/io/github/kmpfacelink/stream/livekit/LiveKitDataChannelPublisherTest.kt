package io.github.kmpfacelink.stream.livekit

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class LiveKitDataChannelPublisherTest {

    @Test
    fun buildWebSocketUrlIncludesAllParams() {
        val config = LiveKitBridgeConfig(
            serverUrl = "wss://livekit.example.com",
            token = "jwt-token-123",
            roomName = "avatar-room",
            participantIdentity = "tracker-1",
        )
        val publisher = LiveKitDataChannelPublisher(config)

        val url = publisher.buildWebSocketUrl()

        assertEquals(
            "wss://livekit.example.com/rtc?room=avatar-room&identity=tracker-1&token=jwt-token-123",
            url,
        )
    }

    @Test
    fun buildWebSocketUrlTrimsTrailingSlash() {
        val config = LiveKitBridgeConfig(
            serverUrl = "wss://livekit.example.com/",
            token = "tok",
            roomName = "room",
        )
        val publisher = LiveKitDataChannelPublisher(config)

        val url = publisher.buildWebSocketUrl()

        assert(url.startsWith("wss://livekit.example.com/rtc?"))
        assertFalse(url.contains("//rtc"))
    }

    @Test
    fun initialStateIsNotConnected() {
        val config = LiveKitBridgeConfig(
            serverUrl = "wss://x",
            token = "t",
            roomName = "r",
        )
        val publisher = LiveKitDataChannelPublisher(config)

        assertFalse(publisher.isConnected)
    }

    @Test
    fun initialConnectionStateIsDisconnected() {
        val config = LiveKitBridgeConfig(
            serverUrl = "wss://x",
            token = "t",
            roomName = "r",
        )
        val publisher = LiveKitDataChannelPublisher(config)

        assertEquals(
            LiveKitConnectionState.DISCONNECTED,
            publisher.liveKitConnectionState.value,
        )
    }
}
