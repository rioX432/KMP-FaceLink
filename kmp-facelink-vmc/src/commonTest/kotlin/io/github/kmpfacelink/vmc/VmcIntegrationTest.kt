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
 * Integration tests for VMC protocol verifying end-to-end encode/decode
 * under realistic streaming scenarios.
 */
class VmcIntegrationTest {

    @Test
    fun longDurationStreamMaintainsTimestampMonotonicity() {
        // Simulate 60 seconds of 60fps streaming (3600 frames)
        val totalFrames = 3600
        var lastTime = -1f

        for (frame in 0 until totalFrames) {
            val time = frame.toFloat() / FPS
            val messages = buildList {
                add(VmcMessageBuilder.buildStatusMessage(loaded = 1))
                add(VmcMessageBuilder.buildTimestampMessage(time))
            }
            val bytes = OscEncoder.encodeBundle(messages)
            val decoded = OscDecoder.decode(bytes)
            val received = VmcMessageParser.parse(decoded)

            val receivedTime = requireNotNull(received.senderTime) { "senderTime should not be null" }
            assertTrue(
                receivedTime >= lastTime,
                "Frame $frame: timestamp $time < $lastTime",
            )
            lastTime = receivedTime
        }
    }

    @Test
    fun allBlendShapesWithHeadAndTimestampRoundTrip() {
        val allShapes = BlendShape.entries.associateWith { it.ordinal.toFloat() / 100f }
        val head = HeadTransform(
            pitch = 30f,
            yaw = -45f,
            roll = 10f,
            positionX = 0.1f,
            positionY = 1.5f,
            positionZ = -0.5f,
        )

        val messages = buildList {
            add(VmcMessageBuilder.buildStatusMessage(loaded = 1))
            addAll(VmcMessageBuilder.buildBlendShapeMessages(allShapes))
            add(VmcMessageBuilder.buildHeadBoneMessage(head))
            add(VmcMessageBuilder.buildTimestampMessage(SAMPLE_TIME))
        }

        val bytes = OscEncoder.encodeBundle(messages)
        val decoded = OscDecoder.decode(bytes)
        val received = VmcMessageParser.parse(decoded)

        assertEquals(BlendShape.entries.size, received.blendShapes.size)
        assertTrue(received.isLoaded)
        assertNotNull(received.headRotation)
        assertNotNull(received.headPosition)
        assertEquals(SAMPLE_TIME, received.senderTime)

        // Verify head position preserved
        assertEquals(head.positionX, received.headPosition!![0])
        assertEquals(head.positionY, received.headPosition!![1])
        assertEquals(head.positionZ, received.headPosition!![2])
    }

    @Test
    fun expressionParametersExcludeHeadKeys() {
        val headKeys = VmcHeadParamKeys.VRM_1_0
        val expressions = mapOf(
            "happy" to 0.8f,
            "angry" to 0.2f,
            "aa" to 0.5f,
            headKeys.yaw to -10f,
            headKeys.pitch to 15f,
            headKeys.roll to 3f,
        )

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
        }

        val bytes = OscEncoder.encodeBundle(messages)
        val decoded = OscDecoder.decode(bytes)

        // Expression messages should not include head keys
        val blendVals = decoded.filter { it.address == VmcAddress.BLEND_VAL }
        assertEquals(
            EXPECTED_EXPRESSION_COUNT,
            blendVals.size,
            "Only non-head expressions should be sent as blend vals",
        )
    }

    @Test
    fun rapidLoadUnloadCycles() {
        // Simulate tracking loss and recovery cycles
        for (cycle in 0 until LOAD_UNLOAD_CYCLES) {
            val loaded = cycle % 2 == 0
            val messages = buildList {
                add(VmcMessageBuilder.buildStatusMessage(loaded = if (loaded) 1 else 0))
                if (loaded) {
                    addAll(
                        VmcMessageBuilder.buildBlendShapeMessages(
                            mapOf(BlendShape.JAW_OPEN to 0.5f),
                        ),
                    )
                }
                add(VmcMessageBuilder.buildTimestampMessage(cycle.toFloat()))
            }

            val bytes = OscEncoder.encodeBundle(messages)
            val decoded = OscDecoder.decode(bytes)
            val received = VmcMessageParser.parse(decoded)

            assertEquals(loaded, received.isLoaded, "Cycle $cycle: load state mismatch")
        }
    }

    @Test
    fun bundleAndIndividualDecodingProduceSameResult() {
        val blendShapes = mapOf(
            BlendShape.JAW_OPEN to 0.6f,
            BlendShape.EYE_BLINK_LEFT to 0.4f,
            BlendShape.MOUTH_SMILE_LEFT to 0.8f,
        )
        val head = HeadTransform(pitch = 20f, yaw = -15f, roll = 5f)
        val messages = buildList {
            add(VmcMessageBuilder.buildStatusMessage(loaded = 1))
            addAll(VmcMessageBuilder.buildBlendShapeMessages(blendShapes))
            add(VmcMessageBuilder.buildHeadBoneMessage(head))
            add(VmcMessageBuilder.buildTimestampMessage(SAMPLE_TIME))
        }

        // Bundle
        val bundleResult = VmcMessageParser.parse(
            OscDecoder.decode(OscEncoder.encodeBundle(messages)),
        )

        // Individual
        val individualMessages = messages.flatMap { msg ->
            OscDecoder.decode(OscEncoder.encode(msg))
        }
        val individualResult = VmcMessageParser.parse(individualMessages)

        assertEquals(bundleResult.blendShapes, individualResult.blendShapes)
        assertEquals(bundleResult.isLoaded, individualResult.isLoaded)
        assertEquals(bundleResult.senderTime, individualResult.senderTime)
        assertEquals(bundleResult.headRotation, individualResult.headRotation)
        assertEquals(bundleResult.headPosition, individualResult.headPosition)
    }
}

private const val FPS = 60
private const val SAMPLE_TIME = 5.0f
private const val EXPECTED_EXPRESSION_COUNT = 3
private const val LOAD_UNLOAD_CYCLES = 20
