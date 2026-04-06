package io.github.kmpfacelink.voice.lipsync.internal

import io.github.kmpfacelink.model.BlendShape

/**
 * Maps neural model output indices to ARKit [BlendShape] values.
 *
 * The model outputs a fixed-size float array where each index
 * corresponds to a specific mouth-related blend shape. This mapping
 * follows the same ordering used in Audio2Face-3D style models,
 * covering the 15 mouth/jaw blend shapes most relevant to lip sync.
 */
internal object BlendShapeOutputMap {

    /**
     * Ordered list of blend shapes corresponding to model output indices.
     *
     * The model predicts weights for these shapes in this exact order.
     */
    val outputOrder: List<BlendShape> = listOf(
        BlendShape.JAW_OPEN,
        BlendShape.JAW_FORWARD,
        BlendShape.JAW_LEFT,
        BlendShape.JAW_RIGHT,
        BlendShape.MOUTH_FUNNEL,
        BlendShape.MOUTH_PUCKER,
        BlendShape.MOUTH_LEFT,
        BlendShape.MOUTH_RIGHT,
        BlendShape.MOUTH_SMILE_LEFT,
        BlendShape.MOUTH_SMILE_RIGHT,
        BlendShape.MOUTH_CLOSE,
        BlendShape.MOUTH_STRETCH_LEFT,
        BlendShape.MOUTH_STRETCH_RIGHT,
        BlendShape.MOUTH_LOWER_DOWN_LEFT,
        BlendShape.MOUTH_LOWER_DOWN_RIGHT,
    )

    /**
     * Converts a model output float array to a blend shape map.
     *
     * Zero-weight shapes are omitted to keep the map sparse.
     *
     * @param output Raw model output array
     * @return Map of blend shapes to their predicted weights
     */
    fun toBlendShapeMap(output: FloatArray): Map<BlendShape, Float> {
        val limit = minOf(output.size, outputOrder.size)
        return buildMap {
            for (i in 0 until limit) {
                val weight = output[i].coerceIn(0f, 1f)
                if (weight > WEIGHT_EPSILON) {
                    put(outputOrder[i], weight)
                }
            }
        }
    }

    private const val WEIGHT_EPSILON = 0.001f
}
