package io.github.kmpfacelink.stream.webrtc

import io.github.kmpfacelink.stream.BinaryFrameCodec
import io.github.kmpfacelink.stream.StreamFrame
import io.github.kmpfacelink.stream.readInt
import io.github.kmpfacelink.stream.readLong
import io.github.kmpfacelink.stream.writeInt
import io.github.kmpfacelink.stream.writeLong

/**
 * Binary message format optimized for WebRTC DataChannel transport.
 *
 * Wraps [StreamFrame] binary data with a thin envelope that includes
 * a sequence number for detecting out-of-order or dropped frames
 * (important for unreliable data channels where ordering is not guaranteed).
 *
 * Wire format (little-endian):
 * ```
 * [envelope header]
 *   u8   magic       = 0xFC  (FaceLink Channel)
 *   u8   version     = 1
 *   u32  sequenceNum (monotonically increasing per sender)
 *   i64  sendTimeNs  (sender monotonic clock, nanoseconds)
 *
 * [payload]
 *   remaining bytes  = BinaryFrameCodec-encoded StreamFrame
 * ```
 *
 * Total overhead: 14 bytes on top of the existing binary frame.
 */
public object DataChannelMessage {

    /**
     * Magic byte identifying a DataChannel tracking message.
     *
     * `0xFC` stands for **F**aceLink **C**hannel. Receivers should check this byte
     * first to distinguish FaceLink tracking messages from other DataChannel traffic
     * on shared connections.
     */
    internal const val MAGIC: Byte = 0xFC.toByte()

    /** Current envelope version. */
    internal const val VERSION: Byte = 1

    /** Size of the envelope header in bytes. */
    public const val HEADER_SIZE: Int = ENVELOPE_HEADER_SIZE

    /**
     * Encodes a [StreamFrame] into a DataChannel message with envelope.
     *
     * @param frame The tracking frame to encode
     * @param sequenceNumber Monotonically increasing sequence number
     * @param sendTimeNs Sender's monotonic clock time in nanoseconds
     * @return The complete binary message
     */
    public fun encode(
        frame: StreamFrame,
        sequenceNumber: Int,
        sendTimeNs: Long,
    ): ByteArray {
        val payload = BinaryFrameCodec.encode(frame)
        val buffer = ByteArray(ENVELOPE_HEADER_SIZE + payload.size)
        writeEnvelopeHeader(buffer, sequenceNumber, sendTimeNs)
        payload.copyInto(buffer, ENVELOPE_HEADER_SIZE)
        return buffer
    }

    /**
     * Decodes a DataChannel message back into its components.
     *
     * @param data The raw binary message
     * @return Decoded envelope containing the frame and metadata
     * @throws IllegalArgumentException if the message is malformed
     */
    public fun decode(data: ByteArray): DecodedMessage {
        require(data.size >= ENVELOPE_HEADER_SIZE) {
            "Message too short: ${data.size} bytes (minimum $ENVELOPE_HEADER_SIZE)"
        }
        require(data[0] == MAGIC) {
            "Invalid magic byte: 0x${data[0].toUByte().toString(HEX_RADIX)}"
        }
        val version = data[1]
        require(version == VERSION) {
            "Unsupported version: $version (expected $VERSION)"
        }
        val sequenceNumber = readInt(data, SEQUENCE_OFFSET)
        val sendTimeNs = readLong(data, SEND_TIME_OFFSET)
        val payload = data.copyOfRange(ENVELOPE_HEADER_SIZE, data.size)
        val frame = BinaryFrameCodec.decode(payload)
        return DecodedMessage(
            sequenceNumber = sequenceNumber,
            sendTimeNs = sendTimeNs,
            frame = frame,
        )
    }

    private fun writeEnvelopeHeader(
        buffer: ByteArray,
        sequenceNumber: Int,
        sendTimeNs: Long,
    ) {
        buffer[MAGIC_OFFSET] = MAGIC
        buffer[VERSION_OFFSET] = VERSION
        writeInt(buffer, SEQUENCE_OFFSET, sequenceNumber)
        writeLong(buffer, SEND_TIME_OFFSET, sendTimeNs)
    }
}

/**
 * Decoded DataChannel message with envelope metadata.
 *
 * @property sequenceNumber Sender's monotonic sequence number.
 *           Can detect gaps (dropped frames) and reordering on unreliable channels.
 * @property sendTimeNs Sender's monotonic clock time in nanoseconds.
 *           Useful for one-way latency estimation when clocks are synchronized.
 * @property frame The decoded tracking data frame.
 */
public data class DecodedMessage(
    val sequenceNumber: Int,
    val sendTimeNs: Long,
    val frame: StreamFrame,
)

// Envelope layout constants
private const val MAGIC_OFFSET = 0
private const val VERSION_OFFSET = 1
private const val SEQUENCE_OFFSET = 2
private const val SEND_TIME_OFFSET = 6
private const val ENVELOPE_HEADER_SIZE = 14 // 1 + 1 + 4 + 8
private const val HEX_RADIX = 16
