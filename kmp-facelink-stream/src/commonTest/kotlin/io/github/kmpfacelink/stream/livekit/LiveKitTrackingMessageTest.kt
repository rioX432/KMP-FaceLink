package io.github.kmpfacelink.stream.livekit

import io.github.kmpfacelink.stream.FaceFrame
import io.github.kmpfacelink.stream.HandFrame
import io.github.kmpfacelink.stream.HeadPose
import io.github.kmpfacelink.stream.StreamFrame
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class LiveKitTrackingMessageTest {

    private val json = Json { encodeDefaults = false }

    @Test
    fun messageJsonRoundTrip() {
        val original = LiveKitTrackingMessage(
            timestampMs = 1000L,
            source = "test-device",
            face = LiveKitFaceData(
                blendShapes = List(LiveKitBlendShapeOrder.COUNT) { 0.5f },
                headPose = listOf(10f, -5f, 2f, 0.1f, 0.2f, -0.3f),
            ),
        )

        val encoded = json.encodeToString(original)
        val decoded = json.decodeFromString<LiveKitTrackingMessage>(encoded)

        assertEquals(original.version, decoded.version)
        assertEquals(original.timestampMs, decoded.timestampMs)
        assertEquals(original.source, decoded.source)
        assertNotNull(decoded.face)
        assertEquals(LiveKitBlendShapeOrder.COUNT, decoded.face!!.blendShapes.size)
        assertEquals(original.face!!.headPose, decoded.face!!.headPose)
    }

    @Test
    fun messageWithHandsRoundTrip() {
        val landmarks = List(63) { it * 0.01f }
        val original = LiveKitTrackingMessage(
            timestampMs = 500L,
            source = "test",
            hands = listOf(
                LiveKitHandData(side = "left", landmarks = landmarks),
                LiveKitHandData(side = "right", landmarks = landmarks),
            ),
        )

        val encoded = json.encodeToString(original)
        val decoded = json.decodeFromString<LiveKitTrackingMessage>(encoded)

        assertNotNull(decoded.hands)
        assertEquals(2, decoded.hands!!.size)
        assertEquals("left", decoded.hands!![0].side)
        assertEquals(63, decoded.hands!![0].landmarks.size)
    }

    @Test
    fun emptyMessageOmitsNullFields() {
        val message = LiveKitTrackingMessage(timestampMs = 0L, source = "s")
        val encoded = json.encodeToString(message)
        val decoded = json.decodeFromString<LiveKitTrackingMessage>(encoded)

        assertNull(decoded.face)
        assertNull(decoded.hands)
        assertEquals(LiveKitTrackingMessage.PROTOCOL_VERSION, decoded.version)
    }

    @Test
    fun streamFrameToLiveKitMessageConvertsBlendShapes() {
        val blendShapes = mapOf(
            "jawOpen" to 0.9f,
            "eyeBlinkLeft" to 0.7f,
            "tongueOut" to 0.3f,
        )
        val frame = StreamFrame(
            timestampMs = 2000L,
            face = FaceFrame(
                blendShapes = blendShapes,
                head = HeadPose(1f, 2f, 3f, 0.1f, 0.2f, 0.3f),
            ),
        )

        val message = frame.toLiveKitMessage("device-1")

        assertEquals(2000L, message.timestampMs)
        assertEquals("device-1", message.source)
        assertNotNull(message.face)

        val faceData = message.face!!
        assertEquals(LiveKitBlendShapeOrder.COUNT, faceData.blendShapes.size)

        // Verify known blend shapes are at correct indices
        val jawOpenIndex = LiveKitBlendShapeOrder.indexOf("jawOpen")
        assertEquals(0.9f, faceData.blendShapes[jawOpenIndex])

        val eyeBlinkLeftIndex = LiveKitBlendShapeOrder.indexOf("eyeBlinkLeft")
        assertEquals(0.7f, faceData.blendShapes[eyeBlinkLeftIndex])

        val tongueOutIndex = LiveKitBlendShapeOrder.indexOf("tongueOut")
        assertEquals(0.3f, faceData.blendShapes[tongueOutIndex])

        // Verify unset blend shapes are zero
        val browDownLeftIndex = LiveKitBlendShapeOrder.indexOf("browDownLeft")
        assertEquals(0f, faceData.blendShapes[browDownLeftIndex])

        // Verify head pose
        assertEquals(listOf(1f, 2f, 3f, 0.1f, 0.2f, 0.3f), faceData.headPose)
    }

    @Test
    fun streamFrameToLiveKitMessageConvertsHands() {
        val landmarks = List(63) { it * 0.02f }
        val frame = StreamFrame(
            timestampMs = 3000L,
            hands = listOf(HandFrame(side = "right", landmarks = landmarks)),
        )

        val message = frame.toLiveKitMessage("device-2")

        assertNull(message.face)
        assertNotNull(message.hands)
        assertEquals(1, message.hands!!.size)
        assertEquals("right", message.hands!![0].side)
        assertEquals(landmarks, message.hands!![0].landmarks)
    }

    @Test
    fun blendShapeOrderHasCorrectCount() {
        assertEquals(
            LiveKitBlendShapeOrder.COUNT,
            LiveKitBlendShapeOrder.orderedNames.size,
        )
    }

    @Test
    fun blendShapeOrderIndexLookup() {
        assertEquals(0, LiveKitBlendShapeOrder.indexOf("browDownLeft"))
        assertEquals(
            LiveKitBlendShapeOrder.COUNT - 1,
            LiveKitBlendShapeOrder.indexOf("tongueOut"),
        )
        assertEquals(-1, LiveKitBlendShapeOrder.indexOf("nonexistent"))
    }
}
