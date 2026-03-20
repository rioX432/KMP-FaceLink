package io.github.kmpfacelink.avatar

import io.github.kmpfacelink.model.BlendShape
import io.github.kmpfacelink.model.valueOf

/**
 * Default ARKit BlendShape to VRM expression mappings.
 *
 * Derives VRM expressions (emotions, visemes, eye states, gaze, head rotation)
 * from the 52 ARKit blend shapes and head transform data. Supports both
 * VRM 0.x (PascalCase) and VRM 1.0 (camelCase) naming conventions.
 */
internal object VrmDefaultMappings {

    private const val HALF = 0.5f

    /**
     * Builds the complete mapping set for the given VRM version.
     */
    fun buildMappings(version: VrmVersion): Map<String, ParameterMapping> =
        VrmVisemeMappings.build(version) +
            VrmEmotionMappings.build(version) +
            eyeMappings(version) +
            gazeMappings(version) +
            headMappings(version)

    private fun eyeMappings(version: VrmVersion): Map<String, ParameterMapping> {
        val ids = VrmIdResolver.blinkIds(version)
        return mapOf(
            ids.both to ParameterMapping.Custom { data ->
                val bs = data.blendShapes
                (bs.valueOf(BlendShape.EYE_BLINK_LEFT) + bs.valueOf(BlendShape.EYE_BLINK_RIGHT)) * HALF
            },
            ids.left to ParameterMapping.Direct(BlendShape.EYE_BLINK_LEFT),
            ids.right to ParameterMapping.Direct(BlendShape.EYE_BLINK_RIGHT),
        )
    }

    private fun gazeMappings(version: VrmVersion): Map<String, ParameterMapping> {
        val ids = VrmIdResolver.gazeIds(version)
        return mapOf(
            ids.up to gazeMapping(BlendShape.EYE_LOOK_UP_LEFT, BlendShape.EYE_LOOK_UP_RIGHT),
            ids.down to gazeMapping(BlendShape.EYE_LOOK_DOWN_LEFT, BlendShape.EYE_LOOK_DOWN_RIGHT),
            ids.left to gazeMapping(BlendShape.EYE_LOOK_OUT_LEFT, BlendShape.EYE_LOOK_IN_RIGHT),
            ids.right to gazeMapping(BlendShape.EYE_LOOK_IN_LEFT, BlendShape.EYE_LOOK_OUT_RIGHT),
        )
    }

    private fun gazeMapping(left: BlendShape, right: BlendShape): ParameterMapping =
        ParameterMapping.Custom { data ->
            val bs = data.blendShapes
            (bs.valueOf(left) + bs.valueOf(right)) * HALF
        }

    private fun headMappings(version: VrmVersion): Map<String, ParameterMapping> {
        val ids = VrmIdResolver.headIds(version)
        return mapOf(
            ids.yaw to ParameterMapping.Custom { it.headTransform.yaw },
            ids.pitch to ParameterMapping.Custom { it.headTransform.pitch },
            ids.roll to ParameterMapping.Custom { it.headTransform.roll },
        )
    }
}
