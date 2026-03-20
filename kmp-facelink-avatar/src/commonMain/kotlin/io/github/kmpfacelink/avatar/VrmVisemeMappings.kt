package io.github.kmpfacelink.avatar

import io.github.kmpfacelink.model.BlendShape
import io.github.kmpfacelink.model.valueOf

/**
 * VRM viseme (mouth vowel) mappings derived from ARKit blend shapes.
 *
 * Maps jaw/mouth blend shapes to the five Japanese vowels (A/I/U/E/O)
 * used by VRM for lip sync.
 */
internal object VrmVisemeMappings {

    private const val HALF = 0.5f
    private const val FUNNEL_A_WEIGHT = 0.3f
    private const val PUCKER_U_WEIGHT = 0.5f
    private const val FUNNEL_U_WEIGHT = 0.5f
    private const val JAW_OPEN_E_WEIGHT = 0.3f
    private const val PUCKER_O_WEIGHT = 0.4f
    private const val JAW_OPEN_O_WEIGHT = 0.6f

    fun build(version: VrmVersion): Map<String, ParameterMapping> {
        val ids = VrmIdResolver.visemeIds(version)
        return mapOf(
            ids.a to vowelA(),
            ids.i to vowelI(),
            ids.u to vowelU(),
            ids.e to vowelE(),
            ids.o to vowelO(),
        )
    }

    private fun vowelA(): ParameterMapping = ParameterMapping.Custom { data ->
        val bs = data.blendShapes
        val jaw = bs.valueOf(BlendShape.JAW_OPEN)
        val funnel = bs.valueOf(BlendShape.MOUTH_FUNNEL) * FUNNEL_A_WEIGHT
        (jaw + funnel).coerceIn(0f, 1f)
    }

    private fun vowelI(): ParameterMapping = ParameterMapping.Custom { data ->
        val bs = data.blendShapes
        val smileL = bs.valueOf(BlendShape.MOUTH_SMILE_LEFT)
        val smileR = bs.valueOf(BlendShape.MOUTH_SMILE_RIGHT)
        ((smileL + smileR) * HALF).coerceIn(0f, 1f)
    }

    private fun vowelU(): ParameterMapping = ParameterMapping.Custom { data ->
        val bs = data.blendShapes
        val pucker = bs.valueOf(BlendShape.MOUTH_PUCKER) * PUCKER_U_WEIGHT
        val funnel = bs.valueOf(BlendShape.MOUTH_FUNNEL) * FUNNEL_U_WEIGHT
        (pucker + funnel).coerceIn(0f, 1f)
    }

    private fun vowelE(): ParameterMapping = ParameterMapping.Custom { data ->
        val bs = data.blendShapes
        val smileL = bs.valueOf(BlendShape.MOUTH_SMILE_LEFT)
        val smileR = bs.valueOf(BlendShape.MOUTH_SMILE_RIGHT)
        val jaw = bs.valueOf(BlendShape.JAW_OPEN) * JAW_OPEN_E_WEIGHT
        ((smileL + smileR) * HALF + jaw).coerceIn(0f, 1f)
    }

    private fun vowelO(): ParameterMapping = ParameterMapping.Custom { data ->
        val bs = data.blendShapes
        val jaw = bs.valueOf(BlendShape.JAW_OPEN) * JAW_OPEN_O_WEIGHT
        val pucker = bs.valueOf(BlendShape.MOUTH_PUCKER) * PUCKER_O_WEIGHT
        (jaw + pucker).coerceIn(0f, 1f)
    }
}
