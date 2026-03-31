package io.github.kmpfacelink.stream.webrtc

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * WebRTC signaling messages exchanged over WebSocket during connection setup.
 *
 * The signaling flow follows the standard WebRTC offer/answer model:
 * 1. Offerer sends [Offer] with an SDP session description
 * 2. Answerer responds with [Answer] containing its SDP
 * 3. Both sides exchange [IceCandidate] messages as ICE gathers candidates
 *
 * All messages are JSON-serialized for transport over the signaling WebSocket.
 */
@Serializable
public sealed class SignalingMessage {

    /**
     * SDP offer — sent by the peer initiating the WebRTC connection.
     *
     * @property sdp Session Description Protocol string describing
     *           the offered media/data channel capabilities.
     */
    @Serializable
    @SerialName("offer")
    public data class Offer(
        @SerialName("sdp") val sdp: String,
    ) : SignalingMessage()

    /**
     * SDP answer — sent in response to an [Offer].
     *
     * @property sdp Session Description Protocol string describing
     *           the accepted media/data channel capabilities.
     */
    @Serializable
    @SerialName("answer")
    public data class Answer(
        @SerialName("sdp") val sdp: String,
    ) : SignalingMessage()

    /**
     * ICE candidate discovered by one peer and sent to the other.
     *
     * ICE candidates describe network addresses and protocols that
     * can be used to establish the peer-to-peer connection.
     *
     * @property candidate The ICE candidate string (SDP format).
     * @property sdpMid Media stream identification tag for the
     *           media description this candidate is associated with.
     * @property sdpMLineIndex Zero-based index of the m-line in the SDP
     *           this candidate is associated with.
     */
    @Serializable
    @SerialName("ice")
    public data class IceCandidate(
        @SerialName("candidate") val candidate: String,
        @SerialName("sdpMid") val sdpMid: String? = null,
        @SerialName("sdpMLineIndex") val sdpMLineIndex: Int? = null,
    ) : SignalingMessage()

    /**
     * Signals that the peer has finished sending ICE candidates.
     *
     * Receiving this indicates all candidates have been gathered
     * and no more [IceCandidate] messages will follow.
     */
    @Serializable
    @SerialName("ice_complete")
    public data object IceGatheringComplete : SignalingMessage()

    /**
     * Error message from the signaling server or remote peer.
     *
     * @property code Machine-readable error code.
     * @property message Human-readable error description.
     */
    @Serializable
    @SerialName("error")
    public data class Error(
        @SerialName("code") val code: String,
        @SerialName("message") val message: String,
    ) : SignalingMessage()
}
