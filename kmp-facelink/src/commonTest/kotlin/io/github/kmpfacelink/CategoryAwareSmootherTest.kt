package io.github.kmpfacelink

import io.github.kmpfacelink.model.BlendShape
import io.github.kmpfacelink.model.SmoothingConfig
import io.github.kmpfacelink.util.CategoryAwareSmoother
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertTrue

class CategoryAwareSmootherTest {

    private fun assertApprox(expected: Float, actual: Float, epsilon: Float = 0.05f) {
        assertTrue(abs(expected - actual) < epsilon, "Expected ~$expected but was $actual")
    }

    private fun defaultSmoother() = CategoryAwareSmoother(SmoothingConfig.CategoryAware())

    @Test
    fun firstCallReturnsInputAsIs() {
        val smoother = defaultSmoother()
        val input = mapOf(
            BlendShape.JAW_OPEN to 0.6f,
            BlendShape.EYE_BLINK_LEFT to 0.4f,
            BlendShape.MOUTH_SMILE_LEFT to 0.8f,
        )
        val result = smoother.smooth(input, 0L)
        assertApprox(0.6f, result.getValue(BlendShape.JAW_OPEN), epsilon = 0.001f)
        assertApprox(0.4f, result.getValue(BlendShape.EYE_BLINK_LEFT), epsilon = 0.001f)
        assertApprox(0.8f, result.getValue(BlendShape.MOUTH_SMILE_LEFT), epsilon = 0.001f)
    }

    @Test
    fun differentCategoriesGetDifferentSmoothing() {
        // Mouth has higher minCutoff (3.0) = less smoothing = tracks faster
        // Brow has lower minCutoff (1.0) = more smoothing = tracks slower
        val mouthOnlySmoother = CategoryAwareSmoother(SmoothingConfig.CategoryAware())
        val browOnlySmoother = CategoryAwareSmoother(SmoothingConfig.CategoryAware())

        // Initial frame — both at 0.0
        val initial = mapOf(
            BlendShape.MOUTH_SMILE_LEFT to 0.0f,
            BlendShape.BROW_DOWN_LEFT to 0.0f,
        )
        mouthOnlySmoother.smooth(initial, 0L)
        browOnlySmoother.smooth(initial, 0L)

        // Sudden jump to 1.0 for both
        val jump = mapOf(
            BlendShape.MOUTH_SMILE_LEFT to 1.0f,
            BlendShape.BROW_DOWN_LEFT to 1.0f,
        )
        val mouthResult = mouthOnlySmoother.smooth(jump, 33L)
        val browResult = browOnlySmoother.smooth(jump, 33L)

        val mouthVal = mouthResult.getValue(BlendShape.MOUTH_SMILE_LEFT)
        val browVal = browResult.getValue(BlendShape.BROW_DOWN_LEFT)

        // Mouth (higher minCutoff = less smoothing) should be closer to 1.0 than brow
        assertTrue(
            mouthVal > browVal,
            "Mouth ($mouthVal) should track faster than brow ($browVal) due to higher minCutoff",
        )
    }

    @Test
    fun jawTracksMoreResponsivelyThanCheek() {
        val smoother = CategoryAwareSmoother(SmoothingConfig.CategoryAware())

        // Initial frame at 0.0
        val initial = mapOf(
            BlendShape.JAW_OPEN to 0.0f,
            BlendShape.CHEEK_PUFF to 0.0f,
        )
        smoother.smooth(initial, 0L)

        // Sudden jump to 1.0
        val jump = mapOf(
            BlendShape.JAW_OPEN to 1.0f,
            BlendShape.CHEEK_PUFF to 1.0f,
        )
        val result = smoother.smooth(jump, 33L)

        val jawVal = result.getValue(BlendShape.JAW_OPEN)
        val cheekVal = result.getValue(BlendShape.CHEEK_PUFF)

        // Jaw minCutoff=1.5 > Cheek minCutoff=0.8 => jaw tracks faster
        assertTrue(
            jawVal > cheekVal,
            "JAW ($jawVal) should track faster than CHEEK ($cheekVal)",
        )
    }

    @Test
    fun stableSignalConvergesForAllCategories() {
        val smoother = defaultSmoother()
        val targetValue = 0.5f
        var result = mapOf(
            BlendShape.JAW_OPEN to targetValue,
            BlendShape.EYE_BLINK_LEFT to targetValue,
            BlendShape.MOUTH_SMILE_LEFT to targetValue,
            BlendShape.BROW_DOWN_LEFT to targetValue,
            BlendShape.CHEEK_PUFF to targetValue,
            BlendShape.NOSE_SNEER_LEFT to targetValue,
        )

        for (i in 0..80) {
            result = smoother.smooth(result, i * 33L)
        }

        for ((_, value) in result) {
            assertApprox(targetValue, value, epsilon = 0.02f)
        }
    }

    @Test
    fun resetClearsAllCategoryState() {
        val smoother = defaultSmoother()

        // Feed some data
        smoother.smooth(
            mapOf(BlendShape.JAW_OPEN to 0.8f, BlendShape.EYE_BLINK_LEFT to 0.9f),
            0L,
        )
        smoother.smooth(
            mapOf(BlendShape.JAW_OPEN to 0.2f, BlendShape.EYE_BLINK_LEFT to 0.1f),
            33L,
        )

        smoother.reset()

        // After reset, first call should return input as-is (no prior state)
        val result = smoother.smooth(
            mapOf(BlendShape.JAW_OPEN to 0.5f, BlendShape.EYE_BLINK_LEFT to 0.5f),
            100L,
        )
        assertApprox(0.5f, result.getValue(BlendShape.JAW_OPEN), epsilon = 0.001f)
        assertApprox(0.5f, result.getValue(BlendShape.EYE_BLINK_LEFT), epsilon = 0.001f)
    }

    @Test
    fun sameTimestampPassesThrough() {
        val smoother = defaultSmoother()

        smoother.smooth(mapOf(BlendShape.JAW_OPEN to 0.3f), 100L)

        // Same timestamp — should pass through as-is (avoids division by zero)
        val result = smoother.smooth(mapOf(BlendShape.JAW_OPEN to 0.9f), 100L)
        assertApprox(0.9f, result.getValue(BlendShape.JAW_OPEN), epsilon = 0.001f)
    }

    @Test
    fun customCategoryConfigsAreRespected() {
        // Give mouth very heavy smoothing (low minCutoff) and eye very light smoothing (high minCutoff)
        val config = SmoothingConfig.CategoryAware(
            mouthConfig = SmoothingConfig.OneEuro(minCutoff = 0.5f, beta = 0.0f),
            eyeConfig = SmoothingConfig.OneEuro(minCutoff = 5.0f, beta = 0.0f),
        )
        val smoother = CategoryAwareSmoother(config)

        val initial = mapOf(
            BlendShape.MOUTH_SMILE_LEFT to 0.0f,
            BlendShape.EYE_BLINK_LEFT to 0.0f,
        )
        smoother.smooth(initial, 0L)

        val jump = mapOf(
            BlendShape.MOUTH_SMILE_LEFT to 1.0f,
            BlendShape.EYE_BLINK_LEFT to 1.0f,
        )
        val result = smoother.smooth(jump, 33L)

        val mouthVal = result.getValue(BlendShape.MOUTH_SMILE_LEFT)
        val eyeVal = result.getValue(BlendShape.EYE_BLINK_LEFT)

        // Eye (higher minCutoff) should respond faster than mouth (lower minCutoff)
        assertTrue(
            eyeVal > mouthVal,
            "Eye ($eyeVal) with high minCutoff should track faster than mouth ($mouthVal) with low minCutoff",
        )
    }

    @Test
    fun tongueOutClassifiedAsMouth() {
        // TONGUE_OUT should use mouth config (higher minCutoff = faster response)
        val config = SmoothingConfig.CategoryAware(
            mouthConfig = SmoothingConfig.OneEuro(minCutoff = 5.0f, beta = 0.0f),
            otherConfig = SmoothingConfig.OneEuro(minCutoff = 0.5f, beta = 0.0f),
        )
        val smoother = CategoryAwareSmoother(config)

        val initial = mapOf(BlendShape.TONGUE_OUT to 0.0f)
        smoother.smooth(initial, 0L)

        val result = smoother.smooth(mapOf(BlendShape.TONGUE_OUT to 1.0f), 33L)
        val tongueVal = result.getValue(BlendShape.TONGUE_OUT)

        // Should behave like mouth (high minCutoff = fast), not OTHER (low minCutoff = slow)
        assertTrue(tongueVal > 0.5f, "TONGUE_OUT ($tongueVal) should use mouth config (fast tracking)")
    }
}
