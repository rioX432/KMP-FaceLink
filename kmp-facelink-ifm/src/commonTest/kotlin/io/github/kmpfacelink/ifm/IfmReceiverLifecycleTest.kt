package io.github.kmpfacelink.ifm

import io.github.kmpfacelink.model.BlendShape
import io.github.kmpfacelink.model.GazeData
import io.github.kmpfacelink.model.HeadTransform
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Integration-level tests for IfmReceiver lifecycle, restart, and
 * protocol compatibility scenarios.
 *
 * Since IfmReceiver requires a real UDP socket, these tests focus
 * on verifying the serializer-parser round trip under realistic
 * conditions that simulate what the receiver processes internally.
 */
class IfmReceiverLifecycleTest {

    @Test
    fun continuousStreamingSimulation() {
        // Simulate 10 seconds of 30fps tracking data processing
        val fps = 30
        val durationSec = 10
        val totalFrames = fps * durationSec

        for (frame in 0 until totalFrames) {
            val t = frame.toFloat() / fps
            val jawOpen = (kotlin.math.sin(t.toDouble()) * 0.5 + 0.5).toFloat()
            val blinkLeft = (kotlin.math.cos(t.toDouble()) * 0.5 + 0.5).toFloat()

            val blendShapes = mapOf(
                BlendShape.JAW_OPEN to jawOpen,
                BlendShape.EYE_BLINK_LEFT to blinkLeft,
            )
            val head = HeadTransform(
                pitch = (frame % 90).toFloat(),
                yaw = -(frame % 180).toFloat(),
                roll = (frame % 45).toFloat(),
            )
            val gaze = GazeData(
                rightEyePitch = (frame % 30).toFloat(),
                rightEyeYaw = -(frame % 20).toFloat(),
                leftEyePitch = (frame % 30).toFloat(),
                leftEyeYaw = -(frame % 20).toFloat(),
            )

            val serialized = IfmMessageSerializer.serialize(
                blendShapes = blendShapes,
                headTransform = head,
                gazeData = gaze,
                dataVersion = DataVersion.V1,
            )
            val parsed = IfmMessageParser.parse(serialized)

            val expectedJaw = (jawOpen * 100).toInt()
            val actualJaw = (parsed.blendShapes[BlendShape.JAW_OPEN]!! * 100).toInt()
            // Allow ±1 tolerance for float-to-int rounding differences across platforms
            check(kotlin.math.abs(expectedJaw - actualJaw) <= 1) {
                "Frame $frame: JAW_OPEN mismatch. Expected $expectedJaw, actual $actualJaw"
            }

            assertEquals(
                head.pitch,
                parsed.headTransform.pitch,
                0.01f,
                "Frame $frame: pitch mismatch",
            )
        }
    }

    @Test
    fun v1AndV2InterleavedParsing() {
        // Simulate receiving a mix of V1 and V2 messages (e.g., from different
        // sender versions), verifying the parser handles both correctly
        val blendShapes = mapOf(
            BlendShape.JAW_OPEN to 0.7f,
            BlendShape.EYE_BLINK_LEFT to 0.3f,
        )
        val head = HeadTransform(pitch = 15f, yaw = -10f)

        for (i in 0 until INTERLEAVE_FRAMES) {
            val version = if (i % 2 == 0) DataVersion.V1 else DataVersion.V2
            val serialized = IfmMessageSerializer.serialize(
                blendShapes = blendShapes,
                headTransform = head,
                gazeData = null,
                dataVersion = version,
            )
            val parsed = IfmMessageParser.parse(serialized)

            assertEquals(
                70,
                (parsed.blendShapes[BlendShape.JAW_OPEN]!! * 100).toInt(),
                "Iteration $i ($version): JAW_OPEN mismatch",
            )
            assertEquals(
                30,
                (parsed.blendShapes[BlendShape.EYE_BLINK_LEFT]!! * 100).toInt(),
                "Iteration $i ($version): EYE_BLINK_LEFT mismatch",
            )
        }
    }

    @Test
    fun emptyMessageProducesNoBlendShapes() {
        val parsed = IfmMessageParser.parse("")
        assertEquals(0, parsed.blendShapes.size)
    }

    @Test
    fun handshakeStringSkippedByParser() {
        // IfmReceiver skips handshake strings; verify parser handles it
        // gracefully (produces no blend shapes)
        val parsed = IfmMessageParser.parse(HANDSHAKE_STRING)
        assertEquals(0, parsed.blendShapes.size)
    }

    @Test
    fun fullBlendShapeSetWithGazeAndHead() {
        // Simulate a complete frame with all 52 blend shapes, head, and gaze
        val allShapes = BlendShape.entries.associateWith { shape ->
            (shape.ordinal % 101).toFloat() / 100f
        }
        val head = HeadTransform(
            pitch = 25f,
            yaw = -15f,
            roll = 8f,
            positionX = 0.1f,
            positionY = 0.2f,
            positionZ = -0.3f,
        )
        val gaze = GazeData(
            rightEyePitch = 12f,
            rightEyeYaw = -6f,
            leftEyePitch = 11f,
            leftEyeYaw = -5f,
        )

        for (version in DataVersion.entries) {
            val serialized = IfmMessageSerializer.serialize(
                blendShapes = allShapes,
                headTransform = head,
                gazeData = gaze,
                dataVersion = version,
            )
            val parsed = IfmMessageParser.parse(serialized)

            assertEquals(
                BlendShape.entries.size,
                parsed.blendShapes.size,
                "$version: should have all 52 blend shapes",
            )
            assertEquals(25f, parsed.headTransform.pitch, 0.01f, "$version: pitch")
            assertEquals(-15f, parsed.headTransform.yaw, 0.01f, "$version: yaw")
            assertEquals(12f, parsed.gazeData.rightEyePitch, 0.01f, "$version: right eye pitch")
        }
    }
}

private const val INTERLEAVE_FRAMES = 100
