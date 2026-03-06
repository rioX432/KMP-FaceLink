package io.github.kmpfacelink

import io.github.kmpfacelink.model.BlendShape
import io.github.kmpfacelink.util.CorrelationRule
import io.github.kmpfacelink.util.ExpressionCorrelator
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ExpressionCorrelatorTest {

    private fun blendShapes(vararg pairs: Pair<BlendShape, Float>): MutableMap<BlendShape, Float> =
        mutableMapOf(*pairs)

    // ── Smile → cheek squint ──

    @Test
    fun smileAboveThreshold_cheekSquintBoosted() {
        val correlator = ExpressionCorrelator()
        // Smile at 0.6 — above threshold of 0.3, so cheek should get a boost
        val bs = blendShapes(
            BlendShape.MOUTH_SMILE_LEFT to 0.6f,
            BlendShape.CHEEK_SQUINT_LEFT to 0f,
        )
        correlator.apply(bs)
        val cheek = bs[BlendShape.CHEEK_SQUINT_LEFT] ?: 0f
        assertTrue(cheek > 0f, "Cheek squint should be boosted when smile > 0.3, got $cheek")
    }

    @Test
    fun smileBelowThreshold_cheekSquintUnchanged() {
        val correlator = ExpressionCorrelator()
        // Smile at 0.2 — below threshold of 0.3, so cheek must not change
        val bs = blendShapes(
            BlendShape.MOUTH_SMILE_LEFT to 0.2f,
            BlendShape.CHEEK_SQUINT_LEFT to 0f,
        )
        correlator.apply(bs)
        val cheek = bs[BlendShape.CHEEK_SQUINT_LEFT] ?: 0f
        assertEquals(0f, cheek, "Cheek squint must remain 0 when smile <= threshold, got $cheek")
    }

    @Test
    fun smileAtThreshold_cheekSquintUnchanged() {
        val correlator = ExpressionCorrelator()
        // Smile exactly at threshold — rule uses >, not >=, so no boost
        val bs = blendShapes(
            BlendShape.MOUTH_SMILE_LEFT to 0.3f,
            BlendShape.CHEEK_SQUINT_LEFT to 0f,
        )
        correlator.apply(bs)
        val cheek = bs[BlendShape.CHEEK_SQUINT_LEFT] ?: 0f
        assertEquals(0f, cheek, "Cheek squint must remain 0 when smile == threshold, got $cheek")
    }

    // ── Clamping ──

    @Test
    fun boost_doesNotExceedOne() {
        val correlator = ExpressionCorrelator()
        // Smile at max + cheek already at 0.9 — result must be clamped to 1.0
        val bs = blendShapes(
            BlendShape.MOUTH_SMILE_LEFT to 1.0f,
            BlendShape.CHEEK_SQUINT_LEFT to 0.9f,
        )
        correlator.apply(bs)
        val cheek = bs[BlendShape.CHEEK_SQUINT_LEFT] ?: 0f
        assertTrue(cheek <= 1f, "Cheek squint must be clamped to 1.0, got $cheek")
        assertEquals(1f, cheek, "Cheek squint should be exactly 1.0 after overflow, got $cheek")
    }

    // ── Negative factor (suppression) ──

    @Test
    fun browInnerUp_suppressesBrowDown() {
        val correlator = ExpressionCorrelator()
        // BROW_INNER_UP at 0.8 (above threshold 0.4) with factor -0.15
        // boost = (0.8 - 0.4) * -0.15 = -0.06
        // BROW_DOWN_LEFT at 0.5 → result = max(0.5 - 0.06, 0) = 0.44
        val bs = blendShapes(
            BlendShape.BROW_INNER_UP to 0.8f,
            BlendShape.BROW_DOWN_LEFT to 0.5f,
        )
        correlator.apply(bs)
        val browDown = bs[BlendShape.BROW_DOWN_LEFT] ?: 0f
        assertTrue(browDown < 0.5f, "Brow down should be suppressed by brow inner up, got $browDown")
        assertTrue(browDown >= 0f, "Brow down must not go below 0, got $browDown")
    }

    @Test
    fun negativeFactor_doesNotGoBelowZero() {
        val correlator = ExpressionCorrelator()
        // BROW_INNER_UP very high + BROW_DOWN already near 0 — result must not go negative
        val bs = blendShapes(
            BlendShape.BROW_INNER_UP to 1.0f,
            BlendShape.BROW_DOWN_LEFT to 0.01f,
        )
        correlator.apply(bs)
        val browDown = bs[BlendShape.BROW_DOWN_LEFT] ?: 0f
        assertTrue(browDown >= 0f, "Brow down must be clamped to 0 after suppression, got $browDown")
    }

    // ── Multiple rules applied in sequence ──

    @Test
    fun multipleRules_appliedInSequence() {
        val correlator = ExpressionCorrelator()
        // Both smile AND sneer active simultaneously
        val bs = blendShapes(
            BlendShape.MOUTH_SMILE_LEFT to 0.7f,
            BlendShape.MOUTH_SMILE_RIGHT to 0.7f,
            BlendShape.NOSE_SNEER_LEFT to 0.5f,
            BlendShape.NOSE_SNEER_RIGHT to 0.5f,
            BlendShape.CHEEK_SQUINT_LEFT to 0f,
            BlendShape.CHEEK_SQUINT_RIGHT to 0f,
            BlendShape.BROW_DOWN_LEFT to 0.1f,
            BlendShape.BROW_DOWN_RIGHT to 0.1f,
        )
        correlator.apply(bs)

        // Cheek squint should be boosted by smile rule
        val cheekLeft = bs[BlendShape.CHEEK_SQUINT_LEFT] ?: 0f
        val cheekRight = bs[BlendShape.CHEEK_SQUINT_RIGHT] ?: 0f
        assertTrue(cheekLeft > 0f, "Cheek squint left should be boosted, got $cheekLeft")
        assertTrue(cheekRight > 0f, "Cheek squint right should be boosted, got $cheekRight")

        // Brow down should be boosted by sneer rule
        val browLeft = bs[BlendShape.BROW_DOWN_LEFT] ?: 0f
        val browRight = bs[BlendShape.BROW_DOWN_RIGHT] ?: 0f
        assertTrue(browLeft > 0.1f, "Brow down left should be boosted by sneer, got $browLeft")
        assertTrue(browRight > 0.1f, "Brow down right should be boosted by sneer, got $browRight")
    }

    // ── Custom rules ──

    @Test
    fun customRules_overrideDefaults() {
        // Single custom rule: JAW_OPEN boosts MOUTH_FUNNEL
        val rules = listOf(
            CorrelationRule(
                source = BlendShape.JAW_OPEN,
                target = BlendShape.MOUTH_FUNNEL,
                threshold = 0.2f,
                factor = 0.5f,
            ),
        )
        val correlator = ExpressionCorrelator(rules)
        val bs = blendShapes(
            BlendShape.JAW_OPEN to 0.6f,
            BlendShape.MOUTH_FUNNEL to 0f,
        )
        correlator.apply(bs)
        val funnel = bs[BlendShape.MOUTH_FUNNEL] ?: 0f
        // Expected: (0.6 - 0.2) * 0.5 = 0.2
        assertTrue(funnel > 0f, "Custom rule should boost mouth funnel, got $funnel")
    }

    // ── Boost value accuracy ──

    @Test
    fun smileBoost_correctMagnitude() {
        val correlator = ExpressionCorrelator()
        // MOUTH_SMILE_LEFT = 0.6, threshold = 0.3, factor = 0.25
        // Expected boost = (0.6 - 0.3) * 0.25 = 0.075
        val bs = blendShapes(
            BlendShape.MOUTH_SMILE_LEFT to 0.6f,
            BlendShape.CHEEK_SQUINT_LEFT to 0f,
        )
        correlator.apply(bs)
        val cheek = bs[BlendShape.CHEEK_SQUINT_LEFT] ?: 0f
        val expected = (0.6f - 0.3f) * 0.25f
        assertTrue(
            kotlin.math.abs(cheek - expected) < 0.001f,
            "Expected boost ~$expected but got $cheek",
        )
    }

    @Test
    fun sourceShapeMissing_ruleSkipped() {
        val correlator = ExpressionCorrelator()
        // MOUTH_SMILE_LEFT not in map — rule should be skipped, cheek unchanged
        val bs = blendShapes(
            BlendShape.CHEEK_SQUINT_LEFT to 0.2f,
        )
        correlator.apply(bs)
        val cheek = bs[BlendShape.CHEEK_SQUINT_LEFT] ?: 0f
        assertEquals(0.2f, cheek, "Cheek squint should be unchanged when source missing, got $cheek")
    }
}
