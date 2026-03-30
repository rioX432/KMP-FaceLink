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
 * Simulates a real VMC receiver (like VSeeFace) by encoding and decoding
 * complete VMC frames, verifying that all message types are correctly
 * built and parsed in a realistic scenario.
 */
class VmcReceiverVerificationTest {

    @Test
    fun fullFrameWithAllBlendShapesAndHeadTransform() {
        // Simulate a Performer (phone tracker) sending a full frame
        val blendShapes = BlendShape.entries.associateWith { shape ->
            (shape.ordinal.toFloat() / BlendShape.entries.size)
        }
        val head = HeadTransform(
            pitch = 15f,
            yaw = -10f,
            roll = 3f,
            positionX = 0.05f,
            positionY = 1.6f,
            positionZ = -0.3f,
        )

        val messages = buildList {
            add(VmcMessageBuilder.buildStatusMessage(loaded = 1))
            addAll(VmcMessageBuilder.buildBlendShapeMessages(blendShapes))
            add(VmcMessageBuilder.buildHeadBoneMessage(head))
            add(VmcMessageBuilder.buildTimestampMessage(SAMPLE_TIMESTAMP))
        }

        // Encode as bundle (default VmcSender behavior)
        val bundleBytes = OscEncoder.encodeBundle(messages)
        val decoded = OscDecoder.decode(bundleBytes)
        val received = VmcMessageParser.parse(decoded)

        // Verify all blend shapes arrived
        assertEquals(BlendShape.entries.size, received.blendShapes.size)
        for ((shape, expected) in blendShapes) {
            assertEquals(
                expected,
                received.blendShapes[shape],
                "BlendShape ${shape.name} mismatch",
            )
        }

        // Verify head transform
        assertNotNull(received.headPosition)
        assertHeadPosition(received, head)

        // Verify quaternion is non-trivial (non-zero rotation)
        assertNotNull(received.headRotation)
        assertTrue(
            received.headRotation!![3] < 1f,
            "Quaternion W should be < 1 for non-zero rotation",
        )

        // Verify status and time
        assertTrue(received.isLoaded)
        assertEquals(SAMPLE_TIMESTAMP, received.senderTime)
    }

    @Test
    fun expressionParameterFrameRoundTrip() {
        // Simulate sending VRM 1.0 expression parameters (pre-mapped)
        val expressions = mapOf(
            "happy" to 0.8f,
            "aa" to 0.5f,
            "blinkLeft" to 0.3f,
            "blinkRight" to 0.3f,
            "headYaw" to -10f,
            "headPitch" to 15f,
            "headRoll" to 3f,
        )
        val headKeys = VmcHeadParamKeys.VRM_1_0
        val headKeySet = setOf(headKeys.yaw, headKeys.pitch, headKeys.roll)

        val messages = buildList {
            add(VmcMessageBuilder.buildStatusMessage(loaded = 1))
            addAll(
                VmcMessageBuilder.buildExpressionMessages(
                    expressions,
                    excludeKeys = headKeySet,
                ),
            )
            val headTransform = HeadTransform(
                yaw = expressions[headKeys.yaw] ?: 0f,
                pitch = expressions[headKeys.pitch] ?: 0f,
                roll = expressions[headKeys.roll] ?: 0f,
            )
            add(VmcMessageBuilder.buildHeadBoneMessage(headTransform))
            add(VmcMessageBuilder.buildTimestampMessage(SAMPLE_TIMESTAMP))
        }

        val bundleBytes = OscEncoder.encodeBundle(messages)
        val decoded = OscDecoder.decode(bundleBytes)

        // Verify the decoded messages contain expression blend vals
        val blendVals = decoded.filter { it.address == VmcAddress.BLEND_VAL }
        // 4 expressions (happy, aa, blinkLeft, blinkRight) — head keys excluded
        assertEquals(EXPECTED_EXPRESSION_COUNT, blendVals.size)
    }

    @Test
    fun multipleFramesProcessedIndependently() {
        // Frame 1: partial tracking
        val frame1Messages = buildList {
            add(VmcMessageBuilder.buildStatusMessage(loaded = 1))
            addAll(
                VmcMessageBuilder.buildBlendShapeMessages(
                    mapOf(BlendShape.JAW_OPEN to 0.5f),
                ),
            )
            add(VmcMessageBuilder.buildTimestampMessage(1.0f))
        }

        // Frame 2: different tracking state
        val frame2Messages = buildList {
            add(VmcMessageBuilder.buildStatusMessage(loaded = 1))
            addAll(
                VmcMessageBuilder.buildBlendShapeMessages(
                    mapOf(BlendShape.EYE_BLINK_LEFT to 0.9f),
                ),
            )
            add(VmcMessageBuilder.buildTimestampMessage(2.0f))
        }

        val bundle1 = OscEncoder.encodeBundle(frame1Messages)
        val bundle2 = OscEncoder.encodeBundle(frame2Messages)

        val received1 = VmcMessageParser.parse(OscDecoder.decode(bundle1))
        val received2 = VmcMessageParser.parse(OscDecoder.decode(bundle2))

        // Each frame is independent
        assertEquals(1, received1.blendShapes.size)
        assertEquals(0.5f, received1.blendShapes[BlendShape.JAW_OPEN])
        assertEquals(1.0f, received1.senderTime)

        assertEquals(1, received2.blendShapes.size)
        assertEquals(0.9f, received2.blendShapes[BlendShape.EYE_BLINK_LEFT])
        assertEquals(2.0f, received2.senderTime)
    }

    @Test
    fun headBoneUsesVrm10Name() {
        val head = HeadTransform(pitch = 0f, yaw = 0f, roll = 0f)
        val message = VmcMessageBuilder.buildHeadBoneMessage(head)
        val encoded = OscEncoder.encode(message)
        val decoded = OscDecoder.decode(encoded)

        // The bone name should be "Head" matching VRM 1.0 spec
        val boneName = decoded[0].arguments[0]
        assertTrue(boneName is io.github.kmpfacelink.vmc.osc.OscArgument.StringArg)
        assertEquals(VmcBoneMapping.HEAD, boneName.value)
    }

    private fun assertHeadPosition(received: VmcReceivedData, head: HeadTransform) {
        assertEquals(head.positionX, received.headPosition!![0])
        assertEquals(head.positionY, received.headPosition!![1])
        assertEquals(head.positionZ, received.headPosition!![2])
    }
}

private const val SAMPLE_TIMESTAMP = 1.23f
private const val EXPECTED_EXPRESSION_COUNT = 4
