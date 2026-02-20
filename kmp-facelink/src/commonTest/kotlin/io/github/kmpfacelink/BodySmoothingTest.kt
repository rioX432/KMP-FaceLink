package io.github.kmpfacelink

import io.github.kmpfacelink.model.BodyJoint
import io.github.kmpfacelink.model.BodyLandmarkPoint
import io.github.kmpfacelink.model.SmoothingConfig
import io.github.kmpfacelink.model.TrackedBody
import io.github.kmpfacelink.util.BodyEmaFilter
import io.github.kmpfacelink.util.BodyOneEuroFilter
import io.github.kmpfacelink.util.createBodySmoother
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class BodySmoothingTest {

    private fun assertApprox(expected: Float, actual: Float, epsilon: Float = 0.01f) {
        assertTrue(abs(expected - actual) < epsilon, "Expected $expected but was $actual")
    }

    private fun createBody(x: Float = 0.5f, y: Float = 0.5f): TrackedBody {
        val landmarks = BodyJoint.entries.map { joint ->
            BodyLandmarkPoint(joint = joint, x = x, y = y, z = 0f, visibility = 1f)
        }
        return TrackedBody(landmarks = landmarks)
    }

    // ---- EMA ----

    @Test
    fun emaFirstCallPassesThrough() {
        val filter = BodyEmaFilter(alpha = 0.5f)
        val bodies = listOf(createBody(x = 0.8f, y = 0.3f))
        val result = filter.smooth(bodies, 0L)

        assertApprox(0.8f, result[0].landmarks[0].x)
        assertApprox(0.3f, result[0].landmarks[0].y)
    }

    @Test
    fun emaAppliesSmoothing() {
        val filter = BodyEmaFilter(alpha = 0.5f)
        filter.smooth(listOf(createBody(x = 1.0f, y = 1.0f)), 0L)

        val result = filter.smooth(listOf(createBody(x = 0.0f, y = 0.0f)), 16L)
        // smoothed = 0.5 * 0.0 + 0.5 * 1.0 = 0.5
        assertApprox(0.5f, result[0].landmarks[0].x)
        assertApprox(0.5f, result[0].landmarks[0].y)
    }

    @Test
    fun emaResetsOnBodyCountChange() {
        val filter = BodyEmaFilter(alpha = 0.5f)
        filter.smooth(listOf(createBody(x = 1.0f)), 0L)

        // Body disappears
        filter.smooth(emptyList(), 16L)

        // Body reappears — should pass through (reset state)
        val result = filter.smooth(listOf(createBody(x = 0.3f)), 32L)
        assertApprox(0.3f, result[0].landmarks[0].x)
    }

    @Test
    fun emaResetClearsAllState() {
        val filter = BodyEmaFilter(alpha = 0.5f)
        filter.smooth(listOf(createBody(x = 1.0f)), 0L)
        filter.reset()

        val result = filter.smooth(listOf(createBody(x = 0.3f)), 16L)
        assertApprox(0.3f, result[0].landmarks[0].x)
    }

    @Test
    fun emaHandlesMultipleBodies() {
        val filter = BodyEmaFilter(alpha = 0.5f)
        val bodies = listOf(
            createBody(x = 0.2f),
            createBody(x = 0.8f),
        )
        val result = filter.smooth(bodies, 0L)
        assertTrue(result.size == 2)
        assertApprox(0.2f, result[0].landmarks[0].x)
        assertApprox(0.8f, result[1].landmarks[0].x)
    }

    // ---- One Euro ----

    @Test
    fun oneEuroFirstCallPassesThrough() {
        val filter = BodyOneEuroFilter()
        val result = filter.smooth(listOf(createBody(x = 0.7f, y = 0.4f)), 0L)

        assertApprox(0.7f, result[0].landmarks[0].x)
        assertApprox(0.4f, result[0].landmarks[0].y)
    }

    @Test
    fun oneEuroSmoothsCoordinates() {
        val filter = BodyOneEuroFilter(minCutoff = 1.0f, beta = 0.0f)
        filter.smooth(listOf(createBody(x = 0.5f)), 0L)

        val result = filter.smooth(listOf(createBody(x = 0.7f)), 33L)

        // With beta=0, the filter should apply some smoothing
        // Result should be between 0.5 and 0.7
        val x = result[0].landmarks[0].x
        assertTrue(x > 0.5f, "Expected > 0.5 but was $x")
        assertTrue(x < 0.7f, "Expected < 0.7 but was $x")
    }

    @Test
    fun oneEuroResetClearsState() {
        val filter = BodyOneEuroFilter()
        filter.smooth(listOf(createBody(x = 1.0f)), 0L)
        filter.reset()

        val result = filter.smooth(listOf(createBody(x = 0.3f)), 100L)
        assertApprox(0.3f, result[0].landmarks[0].x)
    }

    // ---- Factory ----

    @Test
    fun createBodySmootherNone() {
        val smoother = SmoothingConfig.None.createBodySmoother()
        assertNull(smoother)
    }

    @Test
    fun createBodySmootherEma() {
        val smoother = SmoothingConfig.Ema(alpha = 0.4f).createBodySmoother()
        assertNotNull(smoother)
        assertTrue(smoother is BodyEmaFilter)
    }

    @Test
    fun createBodySmootherOneEuro() {
        val smoother = SmoothingConfig.OneEuro().createBodySmoother()
        assertNotNull(smoother)
        assertTrue(smoother is BodyOneEuroFilter)
    }
}
