package io.github.kmpfacelink.stream

import io.github.kmpfacelink.model.BlendShape
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class BinaryFrameCodecTest {

    @Test
    fun encodeFaceOnlyRoundTrip() {
        val blendShapes = BlendShape.entries.associate { it.arKitName to 0f }
            .toMutableMap()
            .apply {
                put("jawOpen", 0.75f)
                put("eyeBlinkLeft", 0.5f)
            }

        val original = StreamFrame(
            timestampMs = 42L,
            face = FaceFrame(
                blendShapes = blendShapes,
                head = HeadPose(
                    pitch = 10f,
                    yaw = -5f,
                    roll = 3f,
                    x = 0.1f,
                    y = 0.2f,
                    z = -0.3f,
                ),
            ),
        )

        val encoded = BinaryFrameCodec.encode(original)
        val decoded = BinaryFrameCodec.decode(encoded)

        assertEquals(original.timestampMs, decoded.timestampMs)
        assertNotNull(decoded.face)
        assertNull(decoded.hands)

        val face = decoded.face!!
        assertEquals(0.75f, face.blendShapes["jawOpen"])
        assertEquals(0.5f, face.blendShapes["eyeBlinkLeft"])
        assertEquals(10f, face.head.pitch)
        assertEquals(-5f, face.head.yaw)
        assertEquals(3f, face.head.roll)
        assertEquals(0.1f, face.head.x)
        assertEquals(0.2f, face.head.y)
        assertEquals(-0.3f, face.head.z)
    }

    @Test
    fun encodeHandsOnlyRoundTrip() {
        val landmarks = List(63) { it.toFloat() * 0.01f }
        val original = StreamFrame(
            timestampMs = 100L,
            hands = listOf(
                HandFrame(side = "left", landmarks = landmarks),
                HandFrame(side = "right", landmarks = landmarks),
            ),
        )

        val encoded = BinaryFrameCodec.encode(original)
        val decoded = BinaryFrameCodec.decode(encoded)

        assertEquals(100L, decoded.timestampMs)
        assertNull(decoded.face)
        assertNotNull(decoded.hands)
        assertEquals(2, decoded.hands!!.size)
        assertEquals("left", decoded.hands!![0].side)
        assertEquals("right", decoded.hands!![1].side)
        assertEquals(63, decoded.hands!![0].landmarks.size)
        // Verify landmark values
        for (i in landmarks.indices) {
            assertEquals(landmarks[i], decoded.hands!![0].landmarks[i])
        }
    }

    @Test
    fun encodeFaceAndHandsRoundTrip() {
        val blendShapes = BlendShape.entries.associate { it.arKitName to 0.5f }
        val landmarks = List(63) { 0.1f }
        val original = StreamFrame(
            timestampMs = 200L,
            face = FaceFrame(
                blendShapes = blendShapes,
                head = HeadPose(1f, 2f, 3f, 4f, 5f, 6f),
            ),
            hands = listOf(HandFrame(side = "right", landmarks = landmarks)),
        )

        val encoded = BinaryFrameCodec.encode(original)
        val decoded = BinaryFrameCodec.decode(encoded)

        assertEquals(200L, decoded.timestampMs)
        assertNotNull(decoded.face)
        assertNotNull(decoded.hands)
        assertEquals(1, decoded.hands!!.size)
        assertEquals("right", decoded.hands!![0].side)
    }

    @Test
    fun emptyFrameRoundTrip() {
        val original = StreamFrame(timestampMs = 0L)
        val encoded = BinaryFrameCodec.encode(original)
        val decoded = BinaryFrameCodec.decode(encoded)

        assertEquals(0L, decoded.timestampMs)
        assertNull(decoded.face)
        assertNull(decoded.hands)
        // Header only: version(1) + flags(1) + timestamp(8) = 10 bytes
        assertEquals(10, encoded.size)
    }

    @Test
    fun faceOnlyFrameSize() {
        val blendShapes = BlendShape.entries.associate { it.arKitName to 0f }
        val frame = StreamFrame(
            timestampMs = 1L,
            face = FaceFrame(
                blendShapes = blendShapes,
                head = HeadPose(0f, 0f, 0f),
            ),
        )
        val encoded = BinaryFrameCodec.encode(frame)
        // Header(10) + 52 floats(208) + 6 floats(24) = 242
        assertEquals(242, encoded.size)
    }
}
