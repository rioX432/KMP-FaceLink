package io.github.kmpfacelink.avatar

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class Live2DDefaultMappingsTest {

    @Test
    fun allExpectedParametersPresent() {
        val mappings = Live2DDefaultMappings.buildMappings()
        val expectedParams = listOf(
            Live2DParameterIds.PARAM_EYE_L_OPEN,
            Live2DParameterIds.PARAM_EYE_R_OPEN,
            Live2DParameterIds.PARAM_EYE_L_SMILE,
            Live2DParameterIds.PARAM_EYE_R_SMILE,
            Live2DParameterIds.PARAM_EYE_BALL_X,
            Live2DParameterIds.PARAM_EYE_BALL_Y,
            Live2DParameterIds.PARAM_BROW_L_Y,
            Live2DParameterIds.PARAM_BROW_R_Y,
            Live2DParameterIds.PARAM_BROW_L_ANGLE,
            Live2DParameterIds.PARAM_BROW_R_ANGLE,
            Live2DParameterIds.PARAM_MOUTH_OPEN_Y,
            Live2DParameterIds.PARAM_MOUTH_FORM,
            Live2DParameterIds.PARAM_CHEEK,
            Live2DParameterIds.PARAM_ANGLE_X,
            Live2DParameterIds.PARAM_ANGLE_Y,
            Live2DParameterIds.PARAM_ANGLE_Z,
            Live2DParameterIds.PARAM_BODY_ANGLE_X,
            Live2DParameterIds.PARAM_BODY_ANGLE_Y,
            Live2DParameterIds.PARAM_BODY_ANGLE_Z,
        )
        for (param in expectedParams) {
            assertTrue(param in mappings, "Missing mapping for $param")
        }
    }

    @Test
    fun exactlyNineteenMappings() {
        val mappings = Live2DDefaultMappings.buildMappings()
        assertEquals(19, mappings.size)
    }

    @Test
    fun noDuplicateKeys() {
        val mappings = Live2DDefaultMappings.buildMappings()
        val keys = mappings.keys.toList()
        assertEquals(keys.size, keys.distinct().size)
    }
}
