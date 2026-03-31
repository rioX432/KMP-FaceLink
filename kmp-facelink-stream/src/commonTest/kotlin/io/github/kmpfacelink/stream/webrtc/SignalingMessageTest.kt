package io.github.kmpfacelink.stream.webrtc

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

class SignalingMessageTest {

    private val json = Json { encodeDefaults = false }

    @Test
    fun offerRoundTrip() {
        val original = SignalingMessage.Offer(sdp = "v=0\r\no=- 123 IN IP4 0.0.0.0\r\n")
        val encoded = json.encodeToString(SignalingMessage.serializer(), original)
        val decoded = json.decodeFromString(SignalingMessage.serializer(), encoded)

        assertIs<SignalingMessage.Offer>(decoded)
        assertEquals(original.sdp, decoded.sdp)
    }

    @Test
    fun answerRoundTrip() {
        val original = SignalingMessage.Answer(sdp = "v=0\r\no=- 456 IN IP4 0.0.0.0\r\n")
        val encoded = json.encodeToString(SignalingMessage.serializer(), original)
        val decoded = json.decodeFromString(SignalingMessage.serializer(), encoded)

        assertIs<SignalingMessage.Answer>(decoded)
        assertEquals(original.sdp, decoded.sdp)
    }

    @Test
    fun iceCandidateRoundTrip() {
        val original = SignalingMessage.IceCandidate(
            candidate = "candidate:1 1 UDP 2122252543 192.168.1.10 12345 typ host",
            sdpMid = "0",
            sdpMLineIndex = 0,
        )
        val encoded = json.encodeToString(SignalingMessage.serializer(), original)
        val decoded = json.decodeFromString(SignalingMessage.serializer(), encoded)

        assertIs<SignalingMessage.IceCandidate>(decoded)
        assertEquals(original.candidate, decoded.candidate)
        assertEquals("0", decoded.sdpMid)
        assertEquals(0, decoded.sdpMLineIndex)
    }

    @Test
    fun iceCandidateWithNullOptionalFields() {
        val original = SignalingMessage.IceCandidate(candidate = "candidate:1 1 UDP 0 10.0.0.1 9 typ host")
        val encoded = json.encodeToString(SignalingMessage.serializer(), original)
        val decoded = json.decodeFromString(SignalingMessage.serializer(), encoded)

        assertIs<SignalingMessage.IceCandidate>(decoded)
        assertNull(decoded.sdpMid)
        assertNull(decoded.sdpMLineIndex)
    }

    @Test
    fun iceGatheringCompleteRoundTrip() {
        val original: SignalingMessage = SignalingMessage.IceGatheringComplete
        val encoded = json.encodeToString(SignalingMessage.serializer(), original)
        val decoded = json.decodeFromString(SignalingMessage.serializer(), encoded)

        assertIs<SignalingMessage.IceGatheringComplete>(decoded)
    }

    @Test
    fun errorRoundTrip() {
        val original = SignalingMessage.Error(code = "peer_gone", message = "Remote peer disconnected")
        val encoded = json.encodeToString(SignalingMessage.serializer(), original)
        val decoded = json.decodeFromString(SignalingMessage.serializer(), encoded)

        assertIs<SignalingMessage.Error>(decoded)
        assertEquals("peer_gone", decoded.code)
        assertEquals("Remote peer disconnected", decoded.message)
    }

    @Test
    fun offerJsonContainsTypeDiscriminator() {
        val offer = SignalingMessage.Offer(sdp = "test-sdp")
        val encoded = json.encodeToString(SignalingMessage.serializer(), offer)

        // Verify the type discriminator is present
        assertEquals(true, encoded.contains("\"type\":\"offer\""))
        assertEquals(true, encoded.contains("\"sdp\":\"test-sdp\""))
    }
}
