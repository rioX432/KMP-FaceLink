package io.github.kmpfacelink.stream.webrtc

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class WebRtcTransportConfigTest {

    private val json = Json { encodeDefaults = true }

    @Test
    fun defaultConfigIsUnreliableUnordered() {
        val config = DataChannelConfig()

        assertFalse(config.ordered)
        assertEquals(0, config.maxRetransmits)
        assertTrue(config.isUnreliable)
    }

    @Test
    fun reliableOrderedConfig() {
        val config = DataChannelConfig(
            ordered = true,
            maxRetransmits = null,
            maxPacketLifeTimeMs = null,
        )

        assertTrue(config.ordered)
        assertFalse(config.isUnreliable)
    }

    @Test
    fun rejectsBlankSignalingUrl() {
        assertFailsWith<IllegalArgumentException> {
            WebRtcTransportConfig(signalingUrl = "")
        }
        assertFailsWith<IllegalArgumentException> {
            WebRtcTransportConfig(signalingUrl = "   ")
        }
    }

    @Test
    fun rejectsMutuallyExclusiveRetransmitOptions() {
        assertFailsWith<IllegalArgumentException> {
            DataChannelConfig(maxRetransmits = 3, maxPacketLifeTimeMs = 50)
        }
    }

    @Test
    fun rejectsNegativeMaxRetransmits() {
        assertFailsWith<IllegalArgumentException> {
            DataChannelConfig(maxRetransmits = -1)
        }
    }

    @Test
    fun rejectsNonPositiveMaxPacketLifeTime() {
        assertFailsWith<IllegalArgumentException> {
            DataChannelConfig(maxRetransmits = null, maxPacketLifeTimeMs = 0)
        }
    }

    @Test
    fun configWithIceServersRoundTrip() {
        val config = WebRtcTransportConfig(
            signalingUrl = "ws://localhost:21412/signaling",
            iceServers = listOf(
                IceServer(urls = listOf("stun:stun.l.google.com:19302")),
                IceServer(
                    urls = listOf("turn:turn.example.com:3478"),
                    username = "user",
                    credential = "pass",
                ),
            ),
        )

        val encoded = json.encodeToString(WebRtcTransportConfig.serializer(), config)
        val decoded = json.decodeFromString(WebRtcTransportConfig.serializer(), encoded)

        assertEquals(config.signalingUrl, decoded.signalingUrl)
        assertEquals(2, decoded.iceServers.size)
        assertEquals("stun:stun.l.google.com:19302", decoded.iceServers[0].urls[0])
        assertEquals("user", decoded.iceServers[1].username)
        assertEquals("pass", decoded.iceServers[1].credential)
    }

    @Test
    fun dataChannelConfigRoundTrip() {
        val config = DataChannelConfig(
            label = "custom-tracking",
            ordered = false,
            maxRetransmits = 0,
            protocol = "facelink-v1",
        )

        val encoded = json.encodeToString(DataChannelConfig.serializer(), config)
        val decoded = json.decodeFromString(DataChannelConfig.serializer(), encoded)

        assertEquals("custom-tracking", decoded.label)
        assertFalse(decoded.ordered)
        assertEquals(0, decoded.maxRetransmits)
        assertEquals("facelink-v1", decoded.protocol)
        assertTrue(decoded.isUnreliable)
    }

    @Test
    fun defaultLabel() {
        assertEquals("tracking", DataChannelConfig.DEFAULT_LABEL)
        assertEquals("tracking", DataChannelConfig().label)
    }
}
