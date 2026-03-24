package io.github.kmpfacelink.avatar

import io.github.kmpfacelink.model.BlendShape
import io.github.kmpfacelink.model.FaceTrackingData
import io.github.kmpfacelink.model.HeadTransform
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Comprehensive VRM 1.0 expression mapping tests.
 *
 * Verifies that all VRM 1.0 preset expressions are correctly produced
 * from ARKit blend shape inputs, using the camelCase naming convention
 * defined in the VRMC_vrm-1.0 specification.
 */
class Vrm10MappingTest {

    private val mapper = VrmParameterMapper(VrmConfig(version = VrmVersion.V1_0))

    private fun trackingData(
        blendShapes: Map<BlendShape, Float> = emptyMap(),
        headTransform: HeadTransform = HeadTransform(),
    ) = FaceTrackingData(
        blendShapes = blendShapes,
        headTransform = headTransform,
        timestampMs = 0L,
        isTracking = true,
    )

    // --- All VRM 1.0 expression keys are present ---

    @Test
    fun allVrm10PresetExpressionsAreOutput() {
        val data = trackingData()
        val result = mapper.map(data)

        val expectedKeys = listOf(
            // Emotions
            VrmExpressionIds.V1_HAPPY,
            VrmExpressionIds.V1_ANGRY,
            VrmExpressionIds.V1_SAD,
            VrmExpressionIds.V1_RELAXED,
            VrmExpressionIds.V1_SURPRISED,
            VrmExpressionIds.V1_NEUTRAL,
            // Visemes
            VrmExpressionIds.V1_AA,
            VrmExpressionIds.V1_IH,
            VrmExpressionIds.V1_OU,
            VrmExpressionIds.V1_EE,
            VrmExpressionIds.V1_OH,
            // Eyes
            VrmExpressionIds.V1_BLINK,
            VrmExpressionIds.V1_BLINK_LEFT,
            VrmExpressionIds.V1_BLINK_RIGHT,
            // Gaze
            VrmExpressionIds.V1_LOOK_UP,
            VrmExpressionIds.V1_LOOK_DOWN,
            VrmExpressionIds.V1_LOOK_LEFT,
            VrmExpressionIds.V1_LOOK_RIGHT,
            // Head
            VrmExpressionIds.V1_HEAD_YAW,
            VrmExpressionIds.V1_HEAD_PITCH,
            VrmExpressionIds.V1_HEAD_ROLL,
        )

        for (key in expectedKeys) {
            assertTrue(key in result, "Missing VRM 1.0 expression: $key")
        }
    }

    @Test
    fun noVrm0xKeysInVrm10Output() {
        val data = trackingData(
            blendShapes = mapOf(
                BlendShape.MOUTH_SMILE_LEFT to 0.5f,
                BlendShape.EYE_BLINK_LEFT to 0.5f,
            ),
        )
        val result = mapper.map(data)

        val v0Keys = listOf(
            VrmExpressionIds.V0_JOY,
            VrmExpressionIds.V0_ANGRY,
            VrmExpressionIds.V0_SORROW,
            VrmExpressionIds.V0_FUN,
            VrmExpressionIds.V0_A,
            VrmExpressionIds.V0_BLINK,
            VrmExpressionIds.V0_BLINK_L,
        )

        for (key in v0Keys) {
            assertFalse(key in result, "VRM 0.x key should not appear: $key")
        }
    }

    // --- Viseme naming ---

    @Test
    fun visemeAaFromJawOpen() {
        val result = mapper.map(trackingData(blendShapes = mapOf(BlendShape.JAW_OPEN to 1.0f)))
        assertTrue(result[VrmExpressionIds.V1_AA]!! > 0.9f)
    }

    @Test
    fun visemeIhFromSmile() {
        val result = mapper.map(
            trackingData(
                blendShapes = mapOf(
                    BlendShape.MOUTH_SMILE_LEFT to 1.0f,
                    BlendShape.MOUTH_SMILE_RIGHT to 1.0f,
                ),
            ),
        )
        assertTrue(result[VrmExpressionIds.V1_IH]!! > 0.9f)
    }

    @Test
    fun visemeOuFromPucker() {
        val result = mapper.map(
            trackingData(blendShapes = mapOf(BlendShape.MOUTH_PUCKER to 1.0f)),
        )
        assertTrue(result[VrmExpressionIds.V1_OU]!! > 0.4f)
    }

    @Test
    fun visemeEeFromSmileAndJaw() {
        val result = mapper.map(
            trackingData(
                blendShapes = mapOf(
                    BlendShape.MOUTH_SMILE_LEFT to 0.8f,
                    BlendShape.MOUTH_SMILE_RIGHT to 0.8f,
                    BlendShape.JAW_OPEN to 0.5f,
                ),
            ),
        )
        assertTrue(result[VrmExpressionIds.V1_EE]!! > 0.5f)
    }

    @Test
    fun visemeOhFromJawAndPucker() {
        val result = mapper.map(
            trackingData(
                blendShapes = mapOf(
                    BlendShape.JAW_OPEN to 0.8f,
                    BlendShape.MOUTH_PUCKER to 0.5f,
                ),
            ),
        )
        assertTrue(result[VrmExpressionIds.V1_OH]!! > 0.5f)
    }

    // --- Emotion naming ---

    @Test
    fun sadFromBrowUpAndFrown() {
        val result = mapper.map(
            trackingData(
                blendShapes = mapOf(
                    BlendShape.BROW_INNER_UP to 0.8f,
                    BlendShape.MOUTH_FROWN_LEFT to 0.7f,
                    BlendShape.MOUTH_FROWN_RIGHT to 0.7f,
                ),
            ),
        )
        assertTrue(result[VrmExpressionIds.V1_SAD]!! > 0.5f)
    }

    @Test
    fun relaxedFromSmileWithoutBrowDown() {
        val result = mapper.map(
            trackingData(
                blendShapes = mapOf(
                    BlendShape.MOUTH_SMILE_LEFT to 0.8f,
                    BlendShape.MOUTH_SMILE_RIGHT to 0.8f,
                ),
            ),
        )
        assertTrue(result[VrmExpressionIds.V1_RELAXED]!! > 0.7f)
    }

    // --- Gaze naming ---

    @Test
    fun lookLeftFromEyeLookOutLeftAndInRight() {
        val result = mapper.map(
            trackingData(
                blendShapes = mapOf(
                    BlendShape.EYE_LOOK_OUT_LEFT to 0.8f,
                    BlendShape.EYE_LOOK_IN_RIGHT to 0.6f,
                ),
            ),
        )
        // (0.8 + 0.6) * 0.5 = 0.7
        assertEquals(0.7f, result[VrmExpressionIds.V1_LOOK_LEFT]!!, TOLERANCE)
    }

    @Test
    fun lookRightFromEyeLookInLeftAndOutRight() {
        val result = mapper.map(
            trackingData(
                blendShapes = mapOf(
                    BlendShape.EYE_LOOK_IN_LEFT to 0.6f,
                    BlendShape.EYE_LOOK_OUT_RIGHT to 0.8f,
                ),
            ),
        )
        assertEquals(0.7f, result[VrmExpressionIds.V1_LOOK_RIGHT]!!, TOLERANCE)
    }

    // --- Version switching ---

    @Test
    fun defaultConfigUsesVrm10() {
        val defaultMapper = VrmParameterMapper()
        val result = defaultMapper.map(trackingData())
        assertTrue(VrmExpressionIds.V1_HAPPY in result)
        assertFalse(VrmExpressionIds.V0_JOY in result)
    }

    companion object {
        private const val TOLERANCE = 0.01f
    }
}
