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
 * Integration test verifying end-to-end VMC message encoding and decoding.
 * Simulates a Performer sending tracking data and a Marionette receiving it.
 */
class VmcRoundTripTest {

    @Test
    fun fullFrameRoundTrip() {
        // Build messages as a Performer would
        val blendShapes = mapOf(
            BlendShape.EYE_BLINK_LEFT to 0.3f,
            BlendShape.EYE_BLINK_RIGHT to 0.4f,
            BlendShape.JAW_OPEN to 0.6f,
            BlendShape.MOUTH_SMILE_LEFT to 0.9f,
        )
        val headTransform = HeadTransform(
            pitch = 0f,
            yaw = 0f,
            roll = 0f,
            positionX = 0.1f,
            positionY = 0.2f,
            positionZ = -0.5f,
        )

        val messages = buildList {
            add(VmcMessageBuilder.buildStatusMessage(loaded = 1))
            addAll(VmcMessageBuilder.buildBlendShapeMessages(blendShapes))
            add(VmcMessageBuilder.buildHeadBoneMessage(headTransform))
            add(VmcMessageBuilder.buildTimestampMessage(1.23f))
        }

        // Encode as a bundle (what VmcSender does)
        val bundleBytes = OscEncoder.encodeBundle(messages)

        // Decode as a Marionette would
        val decoded = OscDecoder.decode(bundleBytes)
        val received = VmcMessageParser.parse(decoded)

        // Verify blend shapes
        assertEquals(4, received.blendShapes.size)
        assertEquals(0.3f, received.blendShapes[BlendShape.EYE_BLINK_LEFT])
        assertEquals(0.4f, received.blendShapes[BlendShape.EYE_BLINK_RIGHT])
        assertEquals(0.6f, received.blendShapes[BlendShape.JAW_OPEN])
        assertEquals(0.9f, received.blendShapes[BlendShape.MOUTH_SMILE_LEFT])

        // Verify head position
        assertNotNull(received.headPosition)
        assertEquals(0.1f, received.headPosition!![0])
        assertEquals(0.2f, received.headPosition!![1])
        assertEquals(-0.5f, received.headPosition!![2])

        // Verify head rotation (identity for zero angles)
        assertNotNull(received.headRotation)
        assertEquals(0f, received.headRotation!![0]) // qx
        assertEquals(0f, received.headRotation!![1]) // qy
        assertEquals(0f, received.headRotation!![2]) // qz
        assertEquals(1f, received.headRotation!![3]) // qw

        // Verify status and time
        assertTrue(received.isLoaded)
        assertEquals(1.23f, received.senderTime)
    }

    @Test
    fun all52BlendShapesRoundTrip() {
        val allBlendShapes = BlendShape.entries.withIndex().associate { (index, shape) ->
            shape to (index.toFloat() / BlendShape.entries.size)
        }

        val messages = VmcMessageBuilder.buildBlendShapeMessages(allBlendShapes)
        val bundleBytes = OscEncoder.encodeBundle(messages)
        val decoded = OscDecoder.decode(bundleBytes)
        val received = VmcMessageParser.parse(decoded)

        assertEquals(BlendShape.entries.size, received.blendShapes.size)
        for ((shape, expectedValue) in allBlendShapes) {
            assertEquals(
                expectedValue,
                received.blendShapes[shape],
                "BlendShape ${shape.name} value mismatch",
            )
        }
    }

    @Test
    fun individualMessageRoundTrip() {
        // Test sending individual messages (non-bundled mode)
        val msg = VmcMessageBuilder.buildBlendShapeMessages(
            mapOf(BlendShape.TONGUE_OUT to 0.7f),
        )

        for (original in msg) {
            val encoded = OscEncoder.encode(original)
            val decoded = OscDecoder.decode(encoded)
            assertEquals(1, decoded.size)
            assertEquals(original.address, decoded[0].address)
            assertEquals(original.arguments.size, decoded[0].arguments.size)
        }
    }
}
