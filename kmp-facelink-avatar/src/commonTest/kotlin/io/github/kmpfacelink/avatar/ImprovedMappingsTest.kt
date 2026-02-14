package io.github.kmpfacelink.avatar

import io.github.kmpfacelink.model.BlendShape
import io.github.kmpfacelink.model.FaceTrackingData
import io.github.kmpfacelink.model.HeadTransform
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ImprovedMappingsTest {

    private val mapper = Live2DParameterMapper()

    private fun trackingData(
        blendShapes: Map<BlendShape, Float> = emptyMap(),
    ) = FaceTrackingData(
        blendShapes = blendShapes,
        headTransform = HeadTransform(),
        timestampMs = 0L,
        isTracking = true,
    )

    @Test
    fun eyeWideBoostsEyeOpen() {
        val data = trackingData(
            blendShapes = mapOf(
                BlendShape.EYE_BLINK_LEFT to 0.0f,
                BlendShape.EYE_WIDE_LEFT to 1.0f,
            ),
        )
        val result = mapper.map(data)
        // (1 - 0) + 1.0 * 0.3 = 1.3, clamped to 1.0
        assertEquals(1.0f, result[Live2DParameterIds.PARAM_EYE_L_OPEN]!!, TOLERANCE)
    }

    @Test
    fun eyeWideMildSurprise() {
        val data = trackingData(
            blendShapes = mapOf(
                BlendShape.EYE_BLINK_LEFT to 0.2f,
                BlendShape.EYE_WIDE_LEFT to 0.5f,
            ),
        )
        val result = mapper.map(data)
        // (1 - 0.2) + 0.5 * 0.3 = 0.8 + 0.15 = 0.95
        assertEquals(0.95f, result[Live2DParameterIds.PARAM_EYE_L_OPEN]!!, TOLERANCE)
    }

    @Test
    fun cheekSquintBoostsEyeSmile() {
        val data = trackingData(
            blendShapes = mapOf(
                BlendShape.EYE_SQUINT_LEFT to 0.5f,
                BlendShape.CHEEK_SQUINT_LEFT to 0.6f,
            ),
        )
        val result = mapper.map(data)
        // (0.5 + 0.6 * 0.3) * 1.4 = (0.5 + 0.18) * 1.4 = 0.952
        assertEquals(0.952f, result[Live2DParameterIds.PARAM_EYE_L_SMILE]!!, TOLERANCE)
    }

    @Test
    fun mouthCloseReducesMouthOpen() {
        val data = trackingData(
            blendShapes = mapOf(
                BlendShape.JAW_OPEN to 0.5f,
                BlendShape.MOUTH_CLOSE to 0.5f,
            ),
        )
        val result = mapper.map(data)
        // (0.5 - 0.5 * 0.6) * 1.4 = (0.5 - 0.3) * 1.4 = 0.28
        assertEquals(0.28f, result[Live2DParameterIds.PARAM_MOUTH_OPEN_Y]!!, TOLERANCE)
    }

    @Test
    fun mouthCloseFullyClosedClampsToZero() {
        val data = trackingData(
            blendShapes = mapOf(
                BlendShape.JAW_OPEN to 0.2f,
                BlendShape.MOUTH_CLOSE to 1.0f,
            ),
        )
        val result = mapper.map(data)
        // (0.2 - 1.0 * 0.6) * 1.4 = (0.2 - 0.6) * 1.4 = -0.56, clamped to 0
        assertEquals(0.0f, result[Live2DParameterIds.PARAM_MOUTH_OPEN_Y]!!, TOLERANCE)
    }

    @Test
    fun puckerMakesMouthFormNegative() {
        val data = trackingData(
            blendShapes = mapOf(
                BlendShape.MOUTH_PUCKER to 0.8f,
                BlendShape.MOUTH_FUNNEL to 0.4f,
            ),
        )
        val result = mapper.map(data)
        // (0 - 0 - (0.8 + 0.4) * 0.4) * 1.4 = (-0.48) * 1.4 = -0.672
        assertEquals(-0.672f, result[Live2DParameterIds.PARAM_MOUTH_FORM]!!, TOLERANCE)
    }

    @Test
    fun browAngleBipolar() {
        // Surprised (brow up)
        val surprised = trackingData(
            blendShapes = mapOf(BlendShape.BROW_OUTER_UP_LEFT to 0.7f),
        )
        val surprisedResult = mapper.map(surprised)
        // (0.7 - 0) * 1.4 = 0.98
        assertEquals(0.98f, surprisedResult[Live2DParameterIds.PARAM_BROW_L_ANGLE]!!, TOLERANCE)

        // Angry (brow down)
        val angry = trackingData(
            blendShapes = mapOf(BlendShape.BROW_DOWN_LEFT to 0.6f),
        )
        val angryResult = mapper.map(angry)
        // (0 - 0.6) * 1.4 = -0.84
        assertEquals(-0.84f, angryResult[Live2DParameterIds.PARAM_BROW_L_ANGLE]!!, TOLERANCE)
    }

    @Test
    fun noseSneerBoostsCheek() {
        val data = trackingData(
            blendShapes = mapOf(
                BlendShape.CHEEK_PUFF to 0.3f,
                BlendShape.NOSE_SNEER_LEFT to 0.6f,
                BlendShape.NOSE_SNEER_RIGHT to 0.4f,
            ),
        )
        val result = mapper.map(data)
        // (0.3 + (0.6 + 0.4) * 0.5 * 0.25) * 1.4 = (0.3 + 0.125) * 1.4 = 0.595
        assertEquals(0.595f, result[Live2DParameterIds.PARAM_CHEEK]!!, TOLERANCE)
    }

    @Test
    fun defaultMapperProduces19Parameters() {
        val data = trackingData()
        val result = mapper.map(data)
        assertTrue(result.size >= STANDARD_PARAM_COUNT)
    }

    companion object {
        private const val TOLERANCE = 0.001f
        private const val STANDARD_PARAM_COUNT = 19
    }
}
