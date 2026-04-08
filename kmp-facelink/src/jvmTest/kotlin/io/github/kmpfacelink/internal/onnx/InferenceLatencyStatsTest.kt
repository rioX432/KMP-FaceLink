package io.github.kmpfacelink.internal.onnx

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class InferenceLatencyStatsTest {

    @Test
    fun firstRecordSeedsEmaExactly() {
        val stats = InferenceLatencyStats()
        stats.record(detectNs = ms(5), landmarkNs = ms(10), inferenceNs = ms(16))

        assertNear(EXPECTED_FIRST_DETECT_MS, stats.detectEmaMs())
        assertNear(EXPECTED_FIRST_LANDMARK_MS, stats.landmarkEmaMs())
        assertNear(EXPECTED_FIRST_INFERENCE_MS, stats.inferenceEmaMs())
    }

    @Test
    fun subsequentRecordsApplyExponentialSmoothing() {
        val stats = InferenceLatencyStats()
        // Seed with 10ms, then record 20ms — EMA with alpha=0.1 → 0.1*20 + 0.9*10 = 11.0
        stats.record(ms(10), ms(10), ms(10))
        stats.record(ms(20), ms(20), ms(20))

        assertNear(EXPECTED_SMOOTHED_MS, stats.detectEmaMs())
        assertNear(EXPECTED_SMOOTHED_MS, stats.landmarkEmaMs())
        assertNear(EXPECTED_SMOOTHED_MS, stats.inferenceEmaMs())
    }

    @Test
    fun emaConvergesTowardConstantValue() {
        val stats = InferenceLatencyStats()
        // Feed 500 identical samples — EMA must converge very close to the sample value
        repeat(CONVERGENCE_ITERATIONS) {
            stats.record(ms(CONVERGENCE_TARGET_MS), ms(CONVERGENCE_TARGET_MS), ms(CONVERGENCE_TARGET_MS))
        }

        assertTrue(
            abs(stats.detectEmaMs() - CONVERGENCE_TARGET_MS) < CONVERGENCE_TOLERANCE,
            "EMA should converge near ${CONVERGENCE_TARGET_MS}ms, got ${stats.detectEmaMs()}",
        )
    }

    @Test
    fun recordWithZeroLatenciesIsSafe() {
        val stats = InferenceLatencyStats()
        stats.record(detectNs = 0, landmarkNs = 0, inferenceNs = 0)

        assertEquals(0.0, stats.detectEmaMs())
        assertEquals(0.0, stats.landmarkEmaMs())
        assertEquals(0.0, stats.inferenceEmaMs())
    }

    private fun ms(value: Long): Long = value * NANOS_PER_MS

    private fun assertNear(expected: Double, actual: Double) {
        assertTrue(
            abs(actual - expected) < EPSILON,
            "Expected $expected, got $actual (delta=${abs(actual - expected)})",
        )
    }

    companion object {
        private const val NANOS_PER_MS = 1_000_000L
        private const val EPSILON = 0.001
        private const val EXPECTED_FIRST_DETECT_MS = 5.0
        private const val EXPECTED_FIRST_LANDMARK_MS = 10.0
        private const val EXPECTED_FIRST_INFERENCE_MS = 16.0
        private const val EXPECTED_SMOOTHED_MS = 11.0
        private const val CONVERGENCE_ITERATIONS = 500
        private const val CONVERGENCE_TARGET_MS = 15L
        private const val CONVERGENCE_TOLERANCE = 0.01
    }
}
