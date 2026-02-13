package io.github.kmpfacelink.avatar

import io.github.kmpfacelink.model.BlendShape
import io.github.kmpfacelink.model.FaceTrackingData
import io.github.kmpfacelink.model.HeadTransform
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class Live2DParameterMapperTest {

    private val mapper = Live2DParameterMapper()

    private fun trackingData(
        blendShapes: Map<BlendShape, Float> = emptyMap(),
        headTransform: HeadTransform = HeadTransform(),
    ) = FaceTrackingData(
        blendShapes = blendShapes,
        headTransform = headTransform,
        timestampMs = 0L,
        isTracking = true,
    )

    @Test
    fun notTrackingReturnsEmptyMap() {
        val data = FaceTrackingData(
            blendShapes = emptyMap(),
            headTransform = HeadTransform(),
            timestampMs = 0L,
            isTracking = false,
        )
        val result = mapper.map(data)
        assertTrue(result.isEmpty())
    }

    @Test
    fun eyeBlinkInversion() {
        val data = trackingData(
            blendShapes = mapOf(
                BlendShape.EYE_BLINK_LEFT to 0.8f,
                BlendShape.EYE_BLINK_RIGHT to 0.3f,
            ),
        )
        val result = mapper.map(data)
        assertEquals(0.2f, result[Live2DParameterIds.PARAM_EYE_L_OPEN]!!, TOLERANCE)
        assertEquals(0.7f, result[Live2DParameterIds.PARAM_EYE_R_OPEN]!!, TOLERANCE)
    }

    @Test
    fun headRotationClamping() {
        val data = trackingData(
            headTransform = HeadTransform(yaw = 50f, pitch = -45f, roll = 35f),
        )
        val result = mapper.map(data)
        assertEquals(30f, result[Live2DParameterIds.PARAM_ANGLE_X]!!, TOLERANCE)
        assertEquals(-30f, result[Live2DParameterIds.PARAM_ANGLE_Y]!!, TOLERANCE)
        assertEquals(30f, result[Live2DParameterIds.PARAM_ANGLE_Z]!!, TOLERANCE)
    }

    @Test
    fun bodyRotationScaledAndClamped() {
        val data = trackingData(
            headTransform = HeadTransform(yaw = 15f, pitch = -9f, roll = 6f),
        )
        val result = mapper.map(data)
        assertEquals(5f, result[Live2DParameterIds.PARAM_BODY_ANGLE_X]!!, TOLERANCE)
        assertEquals(-3f, result[Live2DParameterIds.PARAM_BODY_ANGLE_Y]!!, TOLERANCE)
        assertEquals(2f, result[Live2DParameterIds.PARAM_BODY_ANGLE_Z]!!, TOLERANCE)
    }

    @Test
    fun mouthFormCombined() {
        val data = trackingData(
            blendShapes = mapOf(
                BlendShape.MOUTH_SMILE_LEFT to 0.9f,
                BlendShape.MOUTH_SMILE_RIGHT to 0.7f,
                BlendShape.MOUTH_FROWN_LEFT to 0.1f,
                BlendShape.MOUTH_FROWN_RIGHT to 0.1f,
            ),
        )
        val result = mapper.map(data)
        // smile avg = 0.8, frown avg = 0.1, form = 0.7
        assertEquals(0.7f, result[Live2DParameterIds.PARAM_MOUTH_FORM]!!, TOLERANCE)
    }

    @Test
    fun configOverrideReplacesMapping() {
        val config = Live2DMapperConfig(
            blendShapeOverrides = mapOf(
                Live2DParameterIds.PARAM_EYE_L_OPEN to ParameterMapping.Direct(BlendShape.EYE_BLINK_LEFT),
            ),
        )
        val customMapper = Live2DParameterMapper(config)
        val data = trackingData(
            blendShapes = mapOf(BlendShape.EYE_BLINK_LEFT to 0.6f),
        )
        val result = customMapper.map(data)
        // Direct instead of Inverted, so should be 0.6 not 0.4
        assertEquals(0.6f, result[Live2DParameterIds.PARAM_EYE_L_OPEN]!!, TOLERANCE)
    }

    @Test
    fun configOverrideNullRemovesMapping() {
        val config = Live2DMapperConfig(
            blendShapeOverrides = mapOf(
                Live2DParameterIds.PARAM_CHEEK to null,
            ),
        )
        val customMapper = Live2DParameterMapper(config)
        val data = trackingData()
        val result = customMapper.map(data)
        assertTrue(Live2DParameterIds.PARAM_CHEEK !in result)
    }

    @Test
    fun customMappingsAddNewParameters() {
        val tongueParamId = "ParamTongueOut"
        val config = Live2DMapperConfig(
            customMappings = mapOf(
                tongueParamId to ParameterMapping.Direct(BlendShape.TONGUE_OUT),
            ),
        )
        val customMapper = Live2DParameterMapper(config)
        val data = trackingData(
            blendShapes = mapOf(BlendShape.TONGUE_OUT to 0.5f),
        )
        val result = customMapper.map(data)
        assertEquals(0.5f, result[tongueParamId]!!, TOLERANCE)
    }

    companion object {
        private const val TOLERANCE = 0.001f
    }
}
