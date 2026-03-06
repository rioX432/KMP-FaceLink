package io.github.kmpfacelink.util

import io.github.kmpfacelink.model.BlendShape

/**
 * A single FACS co-activation rule that boosts a target AU when a source AU exceeds a threshold.
 *
 * The boost is proportional to (sourceValue - threshold) * factor, and is applied additively
 * to the target blend shape value, clamped to [0, 1].
 *
 * A negative factor can be used to suppress the target AU (e.g., brow inner up suppresses brow down).
 */
internal data class CorrelationRule(
    val source: BlendShape,
    val target: BlendShape,
    val threshold: Float,
    val factor: Float,
)

/**
 * Applies FACS-based expression co-activation rules to a mutable blend shape map.
 *
 * FACS (Facial Action Coding System) defines anatomical correlations between AUs. For example,
 * genuine smiles (AU12) co-activate cheek squints (AU6). These rules encode those relationships
 * so that geometric solver output reflects physiologically plausible expression combinations.
 *
 * Processing is applied in rule order; earlier rules may affect later ones if they share targets.
 */
internal class ExpressionCorrelator(
    private val rules: List<CorrelationRule> = defaultRules,
) {
    /**
     * Apply all co-activation rules in order to [blendShapes].
     *
     * For each rule where [CorrelationRule.source] exceeds [CorrelationRule.threshold],
     * the target blend shape is boosted by (source - threshold) * factor, clamped to [0, 1].
     */
    fun apply(blendShapes: MutableMap<BlendShape, Float>) {
        for (rule in rules) {
            val sourceValue = blendShapes[rule.source] ?: continue
            if (sourceValue > rule.threshold) {
                val boost = (sourceValue - rule.threshold) * rule.factor
                val current = blendShapes[rule.target] ?: 0f
                blendShapes[rule.target] = (current + boost).coerceIn(0f, 1f)
            }
        }
    }

    companion object {
        /**
         * Default FACS co-activation rules derived from facial anatomy research.
         *
         * AU12 (smile) → AU6 (cheek squint): genuine smiles compress the cheeks upward.
         * AU12 (smile) → AU25 (stretch): wide smiles pull the mouth corners laterally.
         * AU9 (sneer) → AU4 (brow down): nose wrinkling accompanies brow lowering.
         * AU1 (brow inner up) → AU4 (brow down): brow raising suppresses brow furrowing.
         * AU10 (upper lip up) → AU9 (sneer): upper lip raising accompanies nose wrinkling.
         */
        val defaultRules: List<CorrelationRule> = listOf(
            // AU12 smile → AU6 cheek squint (left)
            CorrelationRule(
                source = BlendShape.MOUTH_SMILE_LEFT,
                target = BlendShape.CHEEK_SQUINT_LEFT,
                threshold = 0.3f,
                factor = 0.25f,
            ),
            // AU12 smile → AU6 cheek squint (right)
            CorrelationRule(
                source = BlendShape.MOUTH_SMILE_RIGHT,
                target = BlendShape.CHEEK_SQUINT_RIGHT,
                threshold = 0.3f,
                factor = 0.25f,
            ),
            // AU12 smile → AU25 mouth stretch (left)
            CorrelationRule(
                source = BlendShape.MOUTH_SMILE_LEFT,
                target = BlendShape.MOUTH_STRETCH_LEFT,
                threshold = 0.5f,
                factor = 0.15f,
            ),
            // AU12 smile → AU25 mouth stretch (right)
            CorrelationRule(
                source = BlendShape.MOUTH_SMILE_RIGHT,
                target = BlendShape.MOUTH_STRETCH_RIGHT,
                threshold = 0.5f,
                factor = 0.15f,
            ),
            // AU9 sneer → AU4 brow down (left)
            CorrelationRule(
                source = BlendShape.NOSE_SNEER_LEFT,
                target = BlendShape.BROW_DOWN_LEFT,
                threshold = 0.3f,
                factor = 0.20f,
            ),
            // AU9 sneer → AU4 brow down (right)
            CorrelationRule(
                source = BlendShape.NOSE_SNEER_RIGHT,
                target = BlendShape.BROW_DOWN_RIGHT,
                threshold = 0.3f,
                factor = 0.20f,
            ),
            // AU1 brow inner up → AU4 brow down suppression (left, negative factor)
            CorrelationRule(
                source = BlendShape.BROW_INNER_UP,
                target = BlendShape.BROW_DOWN_LEFT,
                threshold = 0.4f,
                factor = -0.15f,
            ),
            // AU1 brow inner up → AU4 brow down suppression (right, negative factor)
            CorrelationRule(
                source = BlendShape.BROW_INNER_UP,
                target = BlendShape.BROW_DOWN_RIGHT,
                threshold = 0.4f,
                factor = -0.15f,
            ),
            // AU10 upper lip up → AU9 sneer (left)
            CorrelationRule(
                source = BlendShape.MOUTH_UPPER_UP_LEFT,
                target = BlendShape.NOSE_SNEER_LEFT,
                threshold = 0.3f,
                factor = 0.20f,
            ),
            // AU10 upper lip up → AU9 sneer (right)
            CorrelationRule(
                source = BlendShape.MOUTH_UPPER_UP_RIGHT,
                target = BlendShape.NOSE_SNEER_RIGHT,
                threshold = 0.3f,
                factor = 0.20f,
            ),
        )
    }
}
