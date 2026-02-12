package io.github.kmpfacelink

import io.github.kmpfacelink.model.HandJoint
import io.github.kmpfacelink.model.HandLandmarkPoint
import io.github.kmpfacelink.model.Handedness
import io.github.kmpfacelink.model.SmoothingConfig
import io.github.kmpfacelink.model.TrackedHand
import io.github.kmpfacelink.util.HandEmaFilter
import io.github.kmpfacelink.util.HandOneEuroFilter
import io.github.kmpfacelink.util.createHandSmoother
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class HandSmoothingTest {

    private fun assertApprox(expected: Float, actual: Float, epsilon: Float = 0.01f) {
        assertTrue(abs(expected - actual) < epsilon, "Expected $expected but was $actual")
    }

    private fun createHand(
        handedness: Handedness = Handedness.RIGHT,
        x: Float = 0.5f,
        y: Float = 0.5f,
    ): TrackedHand {
        val landmarks = HandJoint.entries.map { joint ->
            HandLandmarkPoint(joint = joint, x = x, y = y, z = 0f)
        }
        return TrackedHand(handedness = handedness, landmarks = landmarks)
    }

    // ---- EMA ----

    @Test
    fun emaFirstCallPassesThrough() {
        val filter = HandEmaFilter(alpha = 0.5f)
        val hands = listOf(createHand(x = 0.8f, y = 0.3f))
        val result = filter.smooth(hands, 0L)

        assertApprox(0.8f, result[0].landmarks[0].x)
        assertApprox(0.3f, result[0].landmarks[0].y)
    }

    @Test
    fun emaAppliesSmoothing() {
        val filter = HandEmaFilter(alpha = 0.5f)
        filter.smooth(listOf(createHand(x = 1.0f, y = 1.0f)), 0L)

        val result = filter.smooth(listOf(createHand(x = 0.0f, y = 0.0f)), 16L)
        // smoothed = 0.5 * 0.0 + 0.5 * 1.0 = 0.5
        assertApprox(0.5f, result[0].landmarks[0].x)
        assertApprox(0.5f, result[0].landmarks[0].y)
    }

    @Test
    fun emaResetsOnHandLost() {
        val filter = HandEmaFilter(alpha = 0.5f)
        filter.smooth(listOf(createHand(handedness = Handedness.RIGHT, x = 1.0f)), 0L)

        // Hand disappears
        filter.smooth(emptyList(), 16L)

        // Hand reappears — should pass through (reset state)
        val result = filter.smooth(listOf(createHand(handedness = Handedness.RIGHT, x = 0.3f)), 32L)
        assertApprox(0.3f, result[0].landmarks[0].x)
    }

    @Test
    fun emaResetClearsAllState() {
        val filter = HandEmaFilter(alpha = 0.5f)
        filter.smooth(listOf(createHand(x = 1.0f)), 0L)
        filter.reset()

        val result = filter.smooth(listOf(createHand(x = 0.3f)), 16L)
        assertApprox(0.3f, result[0].landmarks[0].x)
    }

    @Test
    fun emaHandlesTwoHands() {
        val filter = HandEmaFilter(alpha = 0.5f)
        val hands = listOf(
            createHand(handedness = Handedness.LEFT, x = 0.2f),
            createHand(handedness = Handedness.RIGHT, x = 0.8f),
        )
        val result = filter.smooth(hands, 0L)
        assertEquals(2, result.size)
        assertApprox(0.2f, result[0].landmarks[0].x)
        assertApprox(0.8f, result[1].landmarks[0].x)
    }

    // ---- One Euro ----

    @Test
    fun oneEuroFirstCallPassesThrough() {
        val filter = HandOneEuroFilter()
        val result = filter.smooth(listOf(createHand(x = 0.7f, y = 0.4f)), 0L)

        assertApprox(0.7f, result[0].landmarks[0].x)
        assertApprox(0.4f, result[0].landmarks[0].y)
    }

    @Test
    fun oneEuroSmoothsCoordinates() {
        val filter = HandOneEuroFilter(minCutoff = 1.0f, beta = 0.0f)
        filter.smooth(listOf(createHand(x = 0.5f)), 0L)

        val result = filter.smooth(listOf(createHand(x = 0.7f)), 33L)

        // With beta=0, the filter should apply some smoothing
        // Result should be between 0.5 and 0.7
        val x = result[0].landmarks[0].x
        assertTrue(x > 0.5f, "Expected > 0.5 but was $x")
        assertTrue(x < 0.7f, "Expected < 0.7 but was $x")
    }

    @Test
    fun oneEuroResetClearsState() {
        val filter = HandOneEuroFilter()
        filter.smooth(listOf(createHand(x = 1.0f)), 0L)
        filter.reset()

        val result = filter.smooth(listOf(createHand(x = 0.3f)), 100L)
        assertApprox(0.3f, result[0].landmarks[0].x)
    }

    // ---- Factory ----

    @Test
    fun createHandSmootherNone() {
        val smoother = SmoothingConfig.None.createHandSmoother()
        assertNull(smoother)
    }

    @Test
    fun createHandSmootherEma() {
        val smoother = SmoothingConfig.Ema(alpha = 0.4f).createHandSmoother()
        assertNotNull(smoother)
        assertTrue(smoother is HandEmaFilter)
    }

    @Test
    fun createHandSmootherOneEuro() {
        val smoother = SmoothingConfig.OneEuro().createHandSmoother()
        assertNotNull(smoother)
        assertTrue(smoother is HandOneEuroFilter)
    }
}
