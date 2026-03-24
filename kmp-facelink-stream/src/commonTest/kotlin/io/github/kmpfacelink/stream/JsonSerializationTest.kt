package io.github.kmpfacelink.stream

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class JsonSerializationTest {

    private val json = Json { encodeDefaults = false }

    @Test
    fun streamFrameJsonRoundTrip() {
        val original = StreamFrame(
            timestampMs = 1234L,
            face = FaceFrame(
                blendShapes = mapOf("jawOpen" to 0.5f, "eyeBlinkLeft" to 0.8f),
                head = HeadPose(10f, -5f, 2f, 0.1f, 0.2f, -0.3f),
            ),
        )

        val jsonStr = json.encodeToString(original)
        val decoded = json.decodeFromString<StreamFrame>(jsonStr)

        assertEquals(original.timestampMs, decoded.timestampMs)
        assertNotNull(decoded.face)
        assertEquals(0.5f, decoded.face!!.blendShapes["jawOpen"])
        assertEquals(10f, decoded.face!!.head.pitch)
    }

    @Test
    fun streamFrameWithHandsJsonRoundTrip() {
        val landmarks = List(63) { it.toFloat() * 0.01f }
        val original = StreamFrame(
            timestampMs = 500L,
            hands = listOf(
                HandFrame(side = "left", landmarks = landmarks),
            ),
        )

        val jsonStr = json.encodeToString(original)
        val decoded = json.decodeFromString<StreamFrame>(jsonStr)

        assertEquals(500L, decoded.timestampMs)
        assertNotNull(decoded.hands)
        assertEquals(1, decoded.hands!!.size)
        assertEquals("left", decoded.hands!![0].side)
        assertEquals(63, decoded.hands!![0].landmarks.size)
    }

    @Test
    fun emptyFrameSerializesToMinimalJson() {
        val frame = StreamFrame(timestampMs = 0L)
        val jsonStr = json.encodeToString(frame)
        // Should not contain face or hands keys
        val decoded = json.decodeFromString<StreamFrame>(jsonStr)
        assertEquals(0L, decoded.timestampMs)
    }
}
