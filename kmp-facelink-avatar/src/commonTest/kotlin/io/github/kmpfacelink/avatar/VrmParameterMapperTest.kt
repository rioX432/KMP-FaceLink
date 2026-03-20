package io.github.kmpfacelink.avatar

import io.github.kmpfacelink.model.BlendShape
import io.github.kmpfacelink.model.FaceTrackingData
import io.github.kmpfacelink.model.HeadTransform
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class VrmParameterMapperTest {

    private fun trackingData(
        blendShapes: Map<BlendShape, Float> = emptyMap(),
        headTransform: HeadTransform = HeadTransform(),
    ) = FaceTrackingData(
        blendShapes = blendShapes,
        headTransform = headTransform,
        timestampMs = 0L,
        isTracking = true,
    )

    // --- Not tracking ---

    @Test
    fun notTrackingReturnsEmptyMap() {
        val mapper = VrmParameterMapper()
        val data = FaceTrackingData.notTracking()
        assertTrue(mapper.map(data).isEmpty())
    }

    // --- VRM 1.0 viseme tests ---

    @Test
    fun v1VowelAFromJawOpen() {
        val mapper = VrmParameterMapper(VrmConfig(version = VrmVersion.V1_0))
        val data = trackingData(blendShapes = mapOf(BlendShape.JAW_OPEN to 0.8f))
        val result = mapper.map(data)
        assertEquals(0.8f, result[VrmExpressionIds.V1_AA]!!, TOLERANCE)
    }

    @Test
    fun v1VowelIFromSmile() {
        val mapper = VrmParameterMapper(VrmConfig(version = VrmVersion.V1_0))
        val data = trackingData(
            blendShapes = mapOf(
                BlendShape.MOUTH_SMILE_LEFT to 0.6f,
                BlendShape.MOUTH_SMILE_RIGHT to 0.8f,
            ),
        )
        val result = mapper.map(data)
        // (0.6 + 0.8) * 0.5 = 0.7
        assertEquals(0.7f, result[VrmExpressionIds.V1_IH]!!, TOLERANCE)
    }

    @Test
    fun v1VowelUFromPuckerAndFunnel() {
        val mapper = VrmParameterMapper(VrmConfig(version = VrmVersion.V1_0))
        val data = trackingData(
            blendShapes = mapOf(
                BlendShape.MOUTH_PUCKER to 0.6f,
                BlendShape.MOUTH_FUNNEL to 0.4f,
            ),
        )
        val result = mapper.map(data)
        // pucker * 0.5 + funnel * 0.5 = 0.3 + 0.2 = 0.5
        assertEquals(0.5f, result[VrmExpressionIds.V1_OU]!!, TOLERANCE)
    }

    // --- VRM 0.x naming tests ---

    @Test
    fun v0UsesPascalCaseNames() {
        val mapper = VrmParameterMapper(VrmConfig(version = VrmVersion.V0_X))
        val data = trackingData(
            blendShapes = mapOf(BlendShape.EYE_BLINK_LEFT to 0.5f),
        )
        val result = mapper.map(data)
        assertTrue(VrmExpressionIds.V0_BLINK_L in result)
        assertTrue(VrmExpressionIds.V0_JOY in result)
        assertTrue(VrmExpressionIds.V0_A in result)
    }

    @Test
    fun v1UsesCamelCaseNames() {
        val mapper = VrmParameterMapper(VrmConfig(version = VrmVersion.V1_0))
        val data = trackingData(
            blendShapes = mapOf(BlendShape.EYE_BLINK_LEFT to 0.5f),
        )
        val result = mapper.map(data)
        assertTrue(VrmExpressionIds.V1_BLINK_LEFT in result)
        assertTrue(VrmExpressionIds.V1_HAPPY in result)
        assertTrue(VrmExpressionIds.V1_AA in result)
    }

    // --- Emotion tests ---

    @Test
    fun joyFromSmileAndCheek() {
        val mapper = VrmParameterMapper(VrmConfig(version = VrmVersion.V1_0))
        val data = trackingData(
            blendShapes = mapOf(
                BlendShape.MOUTH_SMILE_LEFT to 0.9f,
                BlendShape.MOUTH_SMILE_RIGHT to 0.9f,
                BlendShape.CHEEK_SQUINT_LEFT to 0.5f,
                BlendShape.CHEEK_SQUINT_RIGHT to 0.5f,
            ),
        )
        val result = mapper.map(data)
        // smile = (0.9+0.9)*0.4=0.72, cheek = (0.5+0.5)*0.2=0.2, total=0.92
        assertEquals(0.92f, result[VrmExpressionIds.V1_HAPPY]!!, TOLERANCE)
    }

    @Test
    fun angryFromBrowDownAndFrown() {
        val mapper = VrmParameterMapper(VrmConfig(version = VrmVersion.V1_0))
        val data = trackingData(
            blendShapes = mapOf(
                BlendShape.BROW_DOWN_LEFT to 0.8f,
                BlendShape.BROW_DOWN_RIGHT to 0.8f,
                BlendShape.MOUTH_FROWN_LEFT to 0.6f,
                BlendShape.MOUTH_FROWN_RIGHT to 0.6f,
            ),
        )
        val result = mapper.map(data)
        // brow = (0.8+0.8)*0.3=0.48, frown = (0.6+0.6)*0.3=0.36, total=0.84
        assertEquals(0.84f, result[VrmExpressionIds.V1_ANGRY]!!, TOLERANCE)
    }

    @Test
    fun surpriseFromWideEyesAndJawAndBrow() {
        val mapper = VrmParameterMapper(VrmConfig(version = VrmVersion.V1_0))
        val data = trackingData(
            blendShapes = mapOf(
                BlendShape.EYE_WIDE_LEFT to 0.9f,
                BlendShape.EYE_WIDE_RIGHT to 0.9f,
                BlendShape.JAW_OPEN to 0.7f,
                BlendShape.BROW_INNER_UP to 0.8f,
            ),
        )
        val result = mapper.map(data)
        // wide=(0.9+0.9)*0.3=0.54, jaw=0.7*0.3=0.21, brow=0.8*0.4=0.32 => 1.07 clamped to 1.0
        assertEquals(1.0f, result[VrmExpressionIds.V1_SURPRISED]!!, TOLERANCE)
    }

    // --- Eye tests ---

    @Test
    fun blinkAveragesBothEyes() {
        val mapper = VrmParameterMapper(VrmConfig(version = VrmVersion.V1_0))
        val data = trackingData(
            blendShapes = mapOf(
                BlendShape.EYE_BLINK_LEFT to 0.8f,
                BlendShape.EYE_BLINK_RIGHT to 0.4f,
            ),
        )
        val result = mapper.map(data)
        assertEquals(0.6f, result[VrmExpressionIds.V1_BLINK]!!, TOLERANCE)
        assertEquals(0.8f, result[VrmExpressionIds.V1_BLINK_LEFT]!!, TOLERANCE)
        assertEquals(0.4f, result[VrmExpressionIds.V1_BLINK_RIGHT]!!, TOLERANCE)
    }

    // --- Head transform tests ---

    @Test
    fun headTransformPassedThrough() {
        val mapper = VrmParameterMapper(VrmConfig(version = VrmVersion.V1_0))
        val data = trackingData(
            headTransform = HeadTransform(yaw = 15f, pitch = -10f, roll = 5f),
        )
        val result = mapper.map(data)
        assertEquals(15f, result[VrmExpressionIds.V1_HEAD_YAW]!!, TOLERANCE)
        assertEquals(-10f, result[VrmExpressionIds.V1_HEAD_PITCH]!!, TOLERANCE)
        assertEquals(5f, result[VrmExpressionIds.V1_HEAD_ROLL]!!, TOLERANCE)
    }

    @Test
    fun headTransformSensitivityMultiplied() {
        val sensitivity = 1.5f
        val mapper = VrmParameterMapper(
            VrmConfig(version = VrmVersion.V1_0, headTransformSensitivity = sensitivity),
        )
        val data = trackingData(
            headTransform = HeadTransform(yaw = 10f, pitch = -8f, roll = 4f),
        )
        val result = mapper.map(data)
        // Head values are in degrees and not clamped to [0,1]
        assertEquals(15f, result[VrmExpressionIds.V1_HEAD_YAW]!!, TOLERANCE)
        assertEquals(-12f, result[VrmExpressionIds.V1_HEAD_PITCH]!!, TOLERANCE)
        assertEquals(6f, result[VrmExpressionIds.V1_HEAD_ROLL]!!, TOLERANCE)
    }

    // --- Config overrides ---

    @Test
    fun expressionWeightOverrideApplied() {
        val mapper = VrmParameterMapper(
            VrmConfig(
                version = VrmVersion.V1_0,
                expressionWeightOverrides = mapOf(VrmExpressionIds.V1_HAPPY to 1.5f),
            ),
        )
        val data = trackingData(
            blendShapes = mapOf(
                BlendShape.MOUTH_SMILE_LEFT to 0.5f,
                BlendShape.MOUTH_SMILE_RIGHT to 0.5f,
            ),
        )
        val result = mapper.map(data)
        // Base joy = (0.5+0.5)*0.4 = 0.4, × 1.5 = 0.6
        assertEquals(0.6f, result[VrmExpressionIds.V1_HAPPY]!!, TOLERANCE)
    }

    @Test
    fun customMappingOverridesDefault() {
        val customId = "customExpression"
        val mapper = VrmParameterMapper(
            VrmConfig(
                version = VrmVersion.V1_0,
                customMappings = mapOf(
                    customId to ParameterMapping.Direct(BlendShape.TONGUE_OUT),
                ),
            ),
        )
        val data = trackingData(
            blendShapes = mapOf(BlendShape.TONGUE_OUT to 0.7f),
        )
        val result = mapper.map(data)
        assertEquals(0.7f, result[customId]!!, TOLERANCE)
    }

    // --- Gaze tests ---

    @Test
    fun gazeDirectionMapped() {
        val mapper = VrmParameterMapper(VrmConfig(version = VrmVersion.V1_0))
        val data = trackingData(
            blendShapes = mapOf(
                BlendShape.EYE_LOOK_UP_LEFT to 0.6f,
                BlendShape.EYE_LOOK_UP_RIGHT to 0.4f,
            ),
        )
        val result = mapper.map(data)
        // (0.6 + 0.4) * 0.5 = 0.5
        assertEquals(0.5f, result[VrmExpressionIds.V1_LOOK_UP]!!, TOLERANCE)
    }

    // --- Edge case: extreme values ---

    @Test
    fun extremeValuesClampedToRange() {
        val mapper = VrmParameterMapper(
            VrmConfig(
                version = VrmVersion.V1_0,
                expressionWeightOverrides = mapOf(VrmExpressionIds.V1_HAPPY to 10f),
            ),
        )
        val data = trackingData(
            blendShapes = mapOf(
                BlendShape.MOUTH_SMILE_LEFT to 1.0f,
                BlendShape.MOUTH_SMILE_RIGHT to 1.0f,
                BlendShape.CHEEK_SQUINT_LEFT to 1.0f,
                BlendShape.CHEEK_SQUINT_RIGHT to 1.0f,
            ),
        )
        val result = mapper.map(data)
        // (1.0+1.0)*0.4 + (1.0+1.0)*0.2 = 1.2 × 10 = 12.0, clamped to 1.0
        assertEquals(1.0f, result[VrmExpressionIds.V1_HAPPY]!!, TOLERANCE)
    }

    // --- VRM 0.x specific ---

    @Test
    fun v0EmotionMapping() {
        val mapper = VrmParameterMapper(VrmConfig(version = VrmVersion.V0_X))
        val data = trackingData(
            blendShapes = mapOf(
                BlendShape.MOUTH_SMILE_LEFT to 0.9f,
                BlendShape.MOUTH_SMILE_RIGHT to 0.9f,
            ),
        )
        val result = mapper.map(data)
        // Joy = (0.9+0.9)*0.4 = 0.72
        assertEquals(0.72f, result[VrmExpressionIds.V0_JOY]!!, TOLERANCE)
    }

    @Test
    fun v0HeadTransform() {
        val mapper = VrmParameterMapper(VrmConfig(version = VrmVersion.V0_X))
        val data = trackingData(
            headTransform = HeadTransform(yaw = 20f, pitch = -15f, roll = 10f),
        )
        val result = mapper.map(data)
        assertEquals(20f, result[VrmExpressionIds.V0_HEAD_YAW]!!, TOLERANCE)
        assertEquals(-15f, result[VrmExpressionIds.V0_HEAD_PITCH]!!, TOLERANCE)
        assertEquals(10f, result[VrmExpressionIds.V0_HEAD_ROLL]!!, TOLERANCE)
    }

    // --- Neutral expression ---

    @Test
    fun neutralHighWhenNoExpression() {
        val mapper = VrmParameterMapper(VrmConfig(version = VrmVersion.V1_0))
        val data = trackingData(blendShapes = emptyMap())
        val result = mapper.map(data)
        // With no blend shapes, all expression values are 0, so neutral = 1.0
        assertEquals(1.0f, result[VrmExpressionIds.V1_NEUTRAL]!!, TOLERANCE)
    }

    @Test
    fun neutralLowWhenSmiling() {
        val mapper = VrmParameterMapper(VrmConfig(version = VrmVersion.V1_0))
        val data = trackingData(
            blendShapes = mapOf(
                BlendShape.MOUTH_SMILE_LEFT to 1.0f,
                BlendShape.MOUTH_SMILE_RIGHT to 1.0f,
            ),
        )
        val result = mapper.map(data)
        // smileAvg = 1.0, neutral = 1.0 - 1.0 = 0.0
        assertEquals(0.0f, result[VrmExpressionIds.V1_NEUTRAL]!!, TOLERANCE)
    }

    companion object {
        private const val TOLERANCE = 0.01f
    }
}
