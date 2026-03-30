package io.github.kmpfacelink.stream.livekit

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class LiveKitBridgeConfigTest {

    @Test
    fun defaultConfigHasExpectedValues() {
        val config = LiveKitBridgeConfig(
            serverUrl = "wss://example.com",
            token = "test-token",
            roomName = "test-room",
        )

        assertEquals(LiveKitBridgeConfig.DEFAULT_PUBLISH_RATE_HZ, config.publishRateHz)
        assertEquals(LiveKitBridgeConfig.DEFAULT_PARTICIPANT_IDENTITY, config.participantIdentity)
        assertEquals(LiveKitBridgeConfig.DEFAULT_DATA_CHANNEL_LABEL, config.dataChannelLabel)
        assertEquals(LiveKitBridgeConfig.DEFAULT_RECONNECT_DELAY_MS, config.reconnectDelayMs)
        assertEquals(LiveKitBridgeConfig.DEFAULT_MAX_RECONNECT_ATTEMPTS, config.maxReconnectAttempts)
    }

    @Test
    fun rejectsBlankServerUrl() {
        assertFailsWith<IllegalArgumentException> {
            LiveKitBridgeConfig(serverUrl = "", token = "t", roomName = "r")
        }
    }

    @Test
    fun rejectsBlankToken() {
        assertFailsWith<IllegalArgumentException> {
            LiveKitBridgeConfig(serverUrl = "wss://x", token = "", roomName = "r")
        }
    }

    @Test
    fun rejectsBlankRoomName() {
        assertFailsWith<IllegalArgumentException> {
            LiveKitBridgeConfig(serverUrl = "wss://x", token = "t", roomName = "")
        }
    }

    @Test
    fun rejectsPublishRateOutOfRange() {
        assertFailsWith<IllegalArgumentException> {
            LiveKitBridgeConfig(
                serverUrl = "wss://x",
                token = "t",
                roomName = "r",
                publishRateHz = 0,
            )
        }
        assertFailsWith<IllegalArgumentException> {
            LiveKitBridgeConfig(
                serverUrl = "wss://x",
                token = "t",
                roomName = "r",
                publishRateHz = 61,
            )
        }
    }

    @Test
    fun acceptsEdgePublishRates() {
        val configMin = LiveKitBridgeConfig(
            serverUrl = "wss://x",
            token = "t",
            roomName = "r",
            publishRateHz = 1,
        )
        assertEquals(1, configMin.publishRateHz)

        val configMax = LiveKitBridgeConfig(
            serverUrl = "wss://x",
            token = "t",
            roomName = "r",
            publishRateHz = LiveKitBridgeConfig.MAX_PUBLISH_RATE_HZ,
        )
        assertEquals(LiveKitBridgeConfig.MAX_PUBLISH_RATE_HZ, configMax.publishRateHz)
    }
}
