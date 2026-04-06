package io.github.kmpfacelink.vmc

import io.github.kmpfacelink.model.BlendShape
import io.github.kmpfacelink.model.HeadTransform
import io.github.kmpfacelink.vmc.osc.OscDecoder
import io.github.kmpfacelink.vmc.osc.OscEncoder
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Stress and edge-case tests for VMC protocol encoding/decoding.
 *
 * Verifies correctness under rapid frame bursts, boundary values,
 * and continuous streaming scenarios.
 */
class VmcStressTest {

    @Test
    fun rapidFrameBurstMaintainsDataIntegrity() {
        val frameCount = 500
        for (i in 0 until frameCount) {
            val value = (i % 101).toFloat() / 100f
            val blendShapes = mapOf(
                BlendShape.JAW_OPEN to value,
                BlendShape.EYE_BLINK_LEFT to 1f - value,
            )
            val head = HeadTransform(
                pitch = (i % 90).toFloat(),
                yaw = -(i % 180).toFloat(),
                roll = (i % 45).toFloat(),
            )
            val messages = buildList {
                add(VmcMessageBuilder.buildStatusMessage(loaded = 1))
                addAll(VmcMessageBuilder.buildBlendShapeMessages(blendShapes))
                add(VmcMessageBuilder.buildHeadBoneMessage(head))
                add(VmcMessageBuilder.buildTimestampMessage(i.toFloat() / 60f))
            }
            val bundleBytes = OscEncoder.encodeBundle(messages)
            val decoded = OscDecoder.decode(bundleBytes)
            val received = VmcMessageParser.parse(decoded)

            assertEquals(
                value,
                received.blendShapes[BlendShape.JAW_OPEN],
                "Frame $i: JAW_OPEN mismatch",
            )
            assertEquals(
                1f - value,
                received.blendShapes[BlendShape.EYE_BLINK_LEFT],
                "Frame $i: EYE_BLINK_LEFT mismatch",
            )
            assertTrue(received.isLoaded, "Frame $i: should be loaded")
        }
    }

    @Test
    fun emptyBlendShapeMapProducesApplyOnly() {
        val messages = VmcMessageBuilder.buildBlendShapeMessages(emptyMap())
        // Should still have the Apply message
        assertEquals(1, messages.size)
        assertEquals(VmcAddress.BLEND_APPLY, messages[0].address)
    }

    @Test
    fun zeroRotationProducesIdentityQuaternion() {
        val head = HeadTransform(pitch = 0f, yaw = 0f, roll = 0f)
        val messages = listOf(VmcMessageBuilder.buildHeadBoneMessage(head))
        val bundleBytes = OscEncoder.encodeBundle(messages)
        val decoded = OscDecoder.decode(bundleBytes)
        val received = VmcMessageParser.parse(decoded)

        assertNotNull(received.headRotation)
        assertEquals(0f, received.headRotation!![0]) // qx
        assertEquals(0f, received.headRotation!![1]) // qy
        assertEquals(0f, received.headRotation!![2]) // qz
        assertEquals(1f, received.headRotation!![3]) // qw
    }

    @Test
    fun extremeRotationValuesEncodeCorrectly() {
        val head = HeadTransform(pitch = 90f, yaw = -180f, roll = 360f)
        val messages = listOf(
            VmcMessageBuilder.buildStatusMessage(loaded = 1),
            VmcMessageBuilder.buildHeadBoneMessage(head),
        )
        val bundleBytes = OscEncoder.encodeBundle(messages)
        val decoded = OscDecoder.decode(bundleBytes)
        val received = VmcMessageParser.parse(decoded)

        assertNotNull(received.headRotation)
        // Quaternion components should be valid (normalized)
        val q = received.headRotation!!
        val magnitude = q[0] * q[0] + q[1] * q[1] + q[2] * q[2] + q[3] * q[3]
        assertEquals(1f, magnitude, 0.01f, "Quaternion should be normalized")
    }

    @Test
    fun continuousStreamingSimulation() {
        // Simulate 10 seconds of 60fps tracking
        val fps = 60
        val durationSec = 10
        val totalFrames = fps * durationSec
        var lastTime = -1f

        for (frame in 0 until totalFrames) {
            val time = frame.toFloat() / fps
            val blendShapes = mapOf(
                BlendShape.JAW_OPEN to (kotlin.math.sin(time.toDouble()) * 0.5 + 0.5).toFloat(),
            )
            val messages = buildList {
                add(VmcMessageBuilder.buildStatusMessage(loaded = 1))
                addAll(VmcMessageBuilder.buildBlendShapeMessages(blendShapes))
                add(VmcMessageBuilder.buildTimestampMessage(time))
            }
            val bundleBytes = OscEncoder.encodeBundle(messages)
            val decoded = OscDecoder.decode(bundleBytes)
            val received = VmcMessageParser.parse(decoded)

            assertTrue(received.senderTime >= lastTime, "Timestamps should be monotonic")
            lastTime = received.senderTime
        }
    }

    @Test
    fun singleBlendShapePerFrame() {
        for (shape in BlendShape.entries) {
            val messages = buildList {
                add(VmcMessageBuilder.buildStatusMessage(loaded = 1))
                addAll(VmcMessageBuilder.buildBlendShapeMessages(mapOf(shape to 0.5f)))
            }
            val bundleBytes = OscEncoder.encodeBundle(messages)
            val decoded = OscDecoder.decode(bundleBytes)
            val received = VmcMessageParser.parse(decoded)

            assertEquals(1, received.blendShapes.size, "Shape ${shape.name}")
            assertEquals(0.5f, received.blendShapes[shape], "Shape ${shape.name} value")
        }
    }

    @Test
    fun notLoadedStatusParsesCorrectly() {
        val messages = listOf(VmcMessageBuilder.buildStatusMessage(loaded = 0))
        val bundleBytes = OscEncoder.encodeBundle(messages)
        val decoded = OscDecoder.decode(bundleBytes)
        val received = VmcMessageParser.parse(decoded)

        assertTrue(!received.isLoaded)
    }

    @Test
    fun individualMessagesVsBundleProduceSameResult() {
        val blendShapes = mapOf(
            BlendShape.JAW_OPEN to 0.7f,
            BlendShape.EYE_BLINK_LEFT to 0.3f,
        )
        val messages = buildList {
            add(VmcMessageBuilder.buildStatusMessage(loaded = 1))
            addAll(VmcMessageBuilder.buildBlendShapeMessages(blendShapes))
            add(VmcMessageBuilder.buildTimestampMessage(1.0f))
        }

        // Bundle mode
        val bundleBytes = OscEncoder.encodeBundle(messages)
        val bundleDecoded = OscDecoder.decode(bundleBytes)
        val bundleReceived = VmcMessageParser.parse(bundleDecoded)

        // Individual mode
        val allIndividualDecoded = messages.flatMap { msg ->
            val encoded = OscEncoder.encode(msg)
            OscDecoder.decode(encoded)
        }
        val individualReceived = VmcMessageParser.parse(allIndividualDecoded)

        assertEquals(bundleReceived.blendShapes, individualReceived.blendShapes)
        assertEquals(bundleReceived.isLoaded, individualReceived.isLoaded)
        assertEquals(bundleReceived.senderTime, individualReceived.senderTime)
    }
}
