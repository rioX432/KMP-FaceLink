package io.github.kmpfacelink.pipeline.expression

import io.github.kmpfacelink.model.BlendShape

private const val DEFAULT_LIP_SYNC_WEIGHT = 1.0f
private const val DEFAULT_EMOTION_WEIGHT = 0.7f

/**
 * Blends lip sync and emotion-driven blend shapes into a single output.
 *
 * Blending strategy by region:
 * - **Mouth region** (JAW_OPEN, MOUTH_*, TONGUE_*): lip sync values dominate,
 *   scaled by [lipSyncWeight].
 * - **Eye/brow region** (EYE_*, BROW_*, CHEEK_*): emotion values dominate,
 *   scaled by [emotionWeight].
 * - **Other**: whichever source provides the higher value is used.
 *
 * @property lipSyncWeight Weight applied to lip sync blend shapes (0.0–1.0).
 * @property emotionWeight Weight applied to emotion blend shapes (0.0–1.0).
 */
public class ExpressionBlender(
    public val lipSyncWeight: Float = DEFAULT_LIP_SYNC_WEIGHT,
    public val emotionWeight: Float = DEFAULT_EMOTION_WEIGHT,
) {

    /**
     * Blends lip sync and emotion blend shapes into a combined output.
     *
     * @param lipSync Blend shapes from lip sync (speech animation).
     * @param emotion Blend shapes from emotion expression.
     * @return Merged blend shape map with values clamped to 0.0–1.0.
     */
    public fun blend(
        lipSync: Map<BlendShape, Float>,
        emotion: Map<BlendShape, Float>,
    ): Map<BlendShape, Float> {
        // sparse-contract-exempt: blending two independent sources over the union of their keys.
        // A shape one source does not carry contributes nothing to the blend, and zero is the
        // correct weight for "no contribution" — unlike an estimator output, where zero would
        // claim a measurement. The result carries only shapes at least one source produced.
        val allKeys = lipSync.keys + emotion.keys
        val result = mutableMapOf<BlendShape, Float>()
        for (shape in allKeys) {
            val lipValue = lipSync[shape] ?: 0f
            val emotionValue = emotion[shape] ?: 0f
            result[shape] = blendForRegion(shape, lipValue, emotionValue)
        }
        return result
    }

    private fun blendForRegion(
        shape: BlendShape,
        lipValue: Float,
        emotionValue: Float,
    ): Float {
        val blended = when {
            isMouthRegion(shape) -> lipValue * lipSyncWeight
            isEyeBrowRegion(shape) -> emotionValue * emotionWeight
            else -> maxOf(lipValue, emotionValue)
        }
        return blended.coerceIn(0f, 1f)
    }

    internal companion object {
        internal fun isMouthRegion(shape: BlendShape): Boolean {
            val name = shape.name
            return name.startsWith("JAW_OPEN") ||
                name.startsWith("MOUTH_") ||
                name.startsWith("TONGUE_")
        }

        internal fun isEyeBrowRegion(shape: BlendShape): Boolean {
            val name = shape.name
            return name.startsWith("EYE_") ||
                name.startsWith("BROW_") ||
                name.startsWith("CHEEK_")
        }
    }
}
