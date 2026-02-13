package io.github.kmpfacelink.effects

import io.github.kmpfacelink.model.BlendShape
import io.github.kmpfacelink.model.BlendShapeData
import io.github.kmpfacelink.model.valueOf

/**
 * Evaluates blend shape data against an expression effect configuration.
 */
internal object ExpressionEvaluator {

    /**
     * Computes the intensity for an expression effect.
     *
     * Averages the specified blend shapes, checks against the threshold,
     * and applies the intensity mapping.
     *
     * @param blendShapes List of blend shapes to average
     * @param data Current blend shape data
     * @param threshold Minimum average to produce non-zero intensity
     * @param mapping Intensity mapping to apply
     * @return Computed intensity (0.0–1.0), or 0.0 if below threshold
     */
    fun evaluate(
        blendShapes: List<BlendShape>,
        data: BlendShapeData,
        threshold: Float,
        mapping: IntensityMapping,
    ): Float {
        if (blendShapes.isEmpty()) return 0f
        val average = blendShapes.map { data.valueOf(it) }.average().toFloat()
        if (average < threshold) return 0f
        return mapping.map(average)
    }
}
