package io.github.kmpfacelink.avatar

import io.github.kmpfacelink.avatar.Live2DParameterIds.PARAM_ANGLE_X
import io.github.kmpfacelink.avatar.Live2DParameterIds.PARAM_ANGLE_Y
import io.github.kmpfacelink.avatar.Live2DParameterIds.PARAM_ANGLE_Z
import io.github.kmpfacelink.avatar.Live2DParameterIds.PARAM_BODY_ANGLE_X
import io.github.kmpfacelink.avatar.Live2DParameterIds.PARAM_BODY_ANGLE_Y
import io.github.kmpfacelink.avatar.Live2DParameterIds.PARAM_BODY_ANGLE_Z
import io.github.kmpfacelink.avatar.Live2DParameterIds.PARAM_BROW_L_ANGLE
import io.github.kmpfacelink.avatar.Live2DParameterIds.PARAM_BROW_L_Y
import io.github.kmpfacelink.avatar.Live2DParameterIds.PARAM_BROW_R_ANGLE
import io.github.kmpfacelink.avatar.Live2DParameterIds.PARAM_BROW_R_Y
import io.github.kmpfacelink.avatar.Live2DParameterIds.PARAM_CHEEK
import io.github.kmpfacelink.avatar.Live2DParameterIds.PARAM_EYE_BALL_X
import io.github.kmpfacelink.avatar.Live2DParameterIds.PARAM_EYE_BALL_Y
import io.github.kmpfacelink.avatar.Live2DParameterIds.PARAM_EYE_L_OPEN
import io.github.kmpfacelink.avatar.Live2DParameterIds.PARAM_EYE_L_SMILE
import io.github.kmpfacelink.avatar.Live2DParameterIds.PARAM_EYE_R_OPEN
import io.github.kmpfacelink.avatar.Live2DParameterIds.PARAM_EYE_R_SMILE
import io.github.kmpfacelink.avatar.Live2DParameterIds.PARAM_MOUTH_FORM
import io.github.kmpfacelink.avatar.Live2DParameterIds.PARAM_MOUTH_OPEN_Y
import io.github.kmpfacelink.model.BlendShape
import io.github.kmpfacelink.model.valueOf

/**
 * Default blend shape to Live2D parameter mappings.
 *
 * Covers the 19 standard Live2D Cubism parameters (eyes, brows, mouth, cheek, head, body).
 * Unmapped blend shapes (jaw sub-shapes, individual mouth shapes, nose, tongue) can be
 * added via [Live2DMapperConfig.blendShapeOverrides] for VTubeStudio "Perfect Sync" models.
 */
internal object Live2DDefaultMappings {

    private const val HEAD_ROTATION_MAX = 30f
    private const val BODY_ROTATION_MAX = 10f
    private const val BODY_SCALE = 1f / 3f
    private const val HALF = 0.5f

    fun buildMappings(): Map<String, ParameterMapping> =
        eyeMappings() + browMappings() + mouthMappings() + headMappings()

    private fun eyeMappings(): Map<String, ParameterMapping> = mapOf(
        PARAM_EYE_L_OPEN to ParameterMapping.Inverted(BlendShape.EYE_BLINK_LEFT),
        PARAM_EYE_R_OPEN to ParameterMapping.Inverted(BlendShape.EYE_BLINK_RIGHT),
        PARAM_EYE_L_SMILE to ParameterMapping.Direct(BlendShape.EYE_SQUINT_LEFT),
        PARAM_EYE_R_SMILE to ParameterMapping.Direct(BlendShape.EYE_SQUINT_RIGHT),
        PARAM_EYE_BALL_X to ParameterMapping.Custom { data ->
            val bs = data.blendShapes
            val leftX = bs.valueOf(BlendShape.EYE_LOOK_OUT_LEFT) -
                bs.valueOf(BlendShape.EYE_LOOK_IN_LEFT)
            val rightX = bs.valueOf(BlendShape.EYE_LOOK_IN_RIGHT) -
                bs.valueOf(BlendShape.EYE_LOOK_OUT_RIGHT)
            ((leftX + rightX) * HALF).coerceIn(-1f, 1f)
        },
        PARAM_EYE_BALL_Y to ParameterMapping.Custom { data ->
            val bs = data.blendShapes
            val leftY = bs.valueOf(BlendShape.EYE_LOOK_UP_LEFT) -
                bs.valueOf(BlendShape.EYE_LOOK_DOWN_LEFT)
            val rightY = bs.valueOf(BlendShape.EYE_LOOK_UP_RIGHT) -
                bs.valueOf(BlendShape.EYE_LOOK_DOWN_RIGHT)
            ((leftY + rightY) * HALF).coerceIn(-1f, 1f)
        },
    )

    private fun browMappings(): Map<String, ParameterMapping> = mapOf(
        PARAM_BROW_L_Y to ParameterMapping.Custom { data ->
            val bs = data.blendShapes
            val up = bs.valueOf(BlendShape.BROW_INNER_UP) +
                bs.valueOf(BlendShape.BROW_OUTER_UP_LEFT)
            (up - bs.valueOf(BlendShape.BROW_DOWN_LEFT)).coerceIn(-1f, 1f)
        },
        PARAM_BROW_R_Y to ParameterMapping.Custom { data ->
            val bs = data.blendShapes
            val up = bs.valueOf(BlendShape.BROW_INNER_UP) +
                bs.valueOf(BlendShape.BROW_OUTER_UP_RIGHT)
            (up - bs.valueOf(BlendShape.BROW_DOWN_RIGHT)).coerceIn(-1f, 1f)
        },
        PARAM_BROW_L_ANGLE to ParameterMapping.Direct(BlendShape.BROW_OUTER_UP_LEFT),
        PARAM_BROW_R_ANGLE to ParameterMapping.Direct(BlendShape.BROW_OUTER_UP_RIGHT),
    )

    private fun mouthMappings(): Map<String, ParameterMapping> = mapOf(
        PARAM_MOUTH_OPEN_Y to ParameterMapping.Direct(BlendShape.JAW_OPEN),
        PARAM_MOUTH_FORM to ParameterMapping.Custom { data ->
            val bs = data.blendShapes
            val smileL = bs.valueOf(BlendShape.MOUTH_SMILE_LEFT)
            val smileR = bs.valueOf(BlendShape.MOUTH_SMILE_RIGHT)
            val frownL = bs.valueOf(BlendShape.MOUTH_FROWN_LEFT)
            val frownR = bs.valueOf(BlendShape.MOUTH_FROWN_RIGHT)
            ((smileL + smileR) * HALF - (frownL + frownR) * HALF).coerceIn(-1f, 1f)
        },
        PARAM_CHEEK to ParameterMapping.Direct(BlendShape.CHEEK_PUFF),
    )

    private fun headMappings(): Map<String, ParameterMapping> = mapOf(
        PARAM_ANGLE_X to ParameterMapping.Custom { data ->
            data.headTransform.yaw.coerceIn(-HEAD_ROTATION_MAX, HEAD_ROTATION_MAX)
        },
        PARAM_ANGLE_Y to ParameterMapping.Custom { data ->
            data.headTransform.pitch.coerceIn(-HEAD_ROTATION_MAX, HEAD_ROTATION_MAX)
        },
        PARAM_ANGLE_Z to ParameterMapping.Custom { data ->
            data.headTransform.roll.coerceIn(-HEAD_ROTATION_MAX, HEAD_ROTATION_MAX)
        },
        PARAM_BODY_ANGLE_X to ParameterMapping.Custom { data ->
            (data.headTransform.yaw * BODY_SCALE)
                .coerceIn(-BODY_ROTATION_MAX, BODY_ROTATION_MAX)
        },
        PARAM_BODY_ANGLE_Y to ParameterMapping.Custom { data ->
            (data.headTransform.pitch * BODY_SCALE)
                .coerceIn(-BODY_ROTATION_MAX, BODY_ROTATION_MAX)
        },
        PARAM_BODY_ANGLE_Z to ParameterMapping.Custom { data ->
            (data.headTransform.roll * BODY_SCALE)
                .coerceIn(-BODY_ROTATION_MAX, BODY_ROTATION_MAX)
        },
    )
}
