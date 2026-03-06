package io.github.kmpfacelink

import io.github.kmpfacelink.model.BlendShape
import io.github.kmpfacelink.util.Calibrator
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertTrue

class CalibratorTest {

    private fun assertApprox(expected: Float, actual: Float, epsilon: Float = 0.05f) {
        assertTrue(abs(expected - actual) < epsilon, "Expected $expected but was $actual")
    }

    // ---- Warmup phase tests ----

    @Test
    fun warmupFramesArePassedThroughUnmodified() {
        // warmupFrames=3 so frames 1..3 are warmup, normalization starts at frame 4
        val calibrator = Calibrator(warmupFrames = 3)

        // Establish a wide range during warmup
        calibrator.calibrate(mapOf(BlendShape.JAW_OPEN to 0.0f))
        calibrator.calibrate(mapOf(BlendShape.JAW_OPEN to 1.0f))

        // Still in warmup — raw value must be returned unchanged
        val result = calibrator.calibrate(mapOf(BlendShape.JAW_OPEN to 0.3f))
        assertApprox(0.3f, result[BlendShape.JAW_OPEN]!!)
    }

    @Test
    fun normalizationBeginsAfterWarmup() {
        // warmupFrames=2, so frame 3 is the first normalized frame
        val calibrator = Calibrator(warmupFrames = 2)

        // Warmup: establish range [0.0, 1.0]
        calibrator.calibrate(mapOf(BlendShape.JAW_OPEN to 0.0f))
        calibrator.calibrate(mapOf(BlendShape.JAW_OPEN to 1.0f))

        // Frame 3 — normalization active; value=0.5 → normalized ≈ 0.5
        val result = calibrator.calibrate(mapOf(BlendShape.JAW_OPEN to 0.5f))
        assertApprox(0.5f, result[BlendShape.JAW_OPEN]!!)
    }

    @Test
    fun resetRestoresWarmupPhase() {
        val calibrator = Calibrator(warmupFrames = 2)

        // Warmup + one normalized frame
        calibrator.calibrate(mapOf(BlendShape.JAW_OPEN to 0.0f))
        calibrator.calibrate(mapOf(BlendShape.JAW_OPEN to 1.0f))
        calibrator.calibrate(mapOf(BlendShape.JAW_OPEN to 0.5f))

        calibrator.reset()

        // After reset, warmup restarts — first frame must be passed through unchanged
        val result = calibrator.calibrate(mapOf(BlendShape.JAW_OPEN to 0.7f))
        assertApprox(0.7f, result[BlendShape.JAW_OPEN]!!)
    }

    // ---- EMA range decay tests ----

    @Test
    fun rangeShrinksSlowlyAfterOutlier() {
        // warmupFrames=0 so normalization is immediate; high decay for visible effect in few frames
        val calibrator = Calibrator(warmupFrames = 0, decayRate = 0.1f)

        // Establish range with an extreme outlier at max
        calibrator.calibrate(mapOf(BlendShape.JAW_OPEN to 0.0f))
        calibrator.calibrate(mapOf(BlendShape.JAW_OPEN to 1.0f)) // outlier high

        // Feed many frames of a moderate value — max should decay toward it
        repeat(20) {
            calibrator.calibrate(mapOf(BlendShape.JAW_OPEN to 0.5f))
        }

        // After decay, normalizing 0.5 against a shrunk [~0, ~0.7] range should give > 0.5
        val result = calibrator.calibrate(mapOf(BlendShape.JAW_OPEN to 0.5f))
        val actual = result[BlendShape.JAW_OPEN]!!
        assertTrue(actual > 0.5f, "Expected > 0.5 after range decay, was $actual")
    }

    @Test
    fun zeroDecayRatePreservesRange() {
        // decayRate=0 means range never shrinks — traditional min/max expansion only
        val calibrator = Calibrator(warmupFrames = 0, decayRate = 0f)

        calibrator.calibrate(mapOf(BlendShape.JAW_OPEN to 0.0f))
        calibrator.calibrate(mapOf(BlendShape.JAW_OPEN to 1.0f))

        // Feed moderate values many times
        repeat(50) {
            calibrator.calibrate(mapOf(BlendShape.JAW_OPEN to 0.5f))
        }

        // Range must remain [0, 1]; 0.5 should still normalize to ~0.5
        val result = calibrator.calibrate(mapOf(BlendShape.JAW_OPEN to 0.5f))
        assertApprox(0.5f, result[BlendShape.JAW_OPEN]!!)
    }

    // ---- Original behavioural tests (adjusted for warmup=0 to bypass warmup phase) ----

    @Test
    fun firstCallDuringWarmupReturnsInputDirectly() {
        val calibrator = Calibrator(warmupFrames = 60)
        val input = mapOf(BlendShape.JAW_OPEN to 0.5f)
        val result = calibrator.calibrate(input)
        // During warmup, raw value is returned unchanged
        assertApprox(0.5f, result[BlendShape.JAW_OPEN]!!)
    }

    @Test
    fun normalizesAfterRangeIsEstablished() {
        // decayRate=1.0 means EMA fully adopts new extremes immediately (classic min/max behavior)
        val calibrator = Calibrator(warmupFrames = 0, decayRate = 1.0f)

        // Frame 1: value = 0.2 → min=0.2, max=0.2
        calibrator.calibrate(mapOf(BlendShape.JAW_OPEN to 0.2f))

        // Frame 2: value = 0.8 → range expands instantly (decayRate=1.0); normalized ≈ 1.0
        val result = calibrator.calibrate(mapOf(BlendShape.JAW_OPEN to 0.8f))
        assertApprox(1.0f, result[BlendShape.JAW_OPEN]!!)
    }

    @Test
    fun midRangeValueNormalizesToHalf() {
        // decayRate=1.0 means EMA fully adopts new extremes immediately (classic min/max behavior)
        val calibrator = Calibrator(warmupFrames = 0, decayRate = 1.0f)

        calibrator.calibrate(mapOf(BlendShape.JAW_OPEN to 0.0f))
        calibrator.calibrate(mapOf(BlendShape.JAW_OPEN to 1.0f))

        // value=0.5, min=0.0, max=1.0 → normalized ≈ 0.5
        val result = calibrator.calibrate(mapOf(BlendShape.JAW_OPEN to 0.5f))
        assertApprox(0.5f, result[BlendShape.JAW_OPEN]!!)
    }

    @Test
    fun resetClearsRange() {
        val calibrator = Calibrator(warmupFrames = 0, decayRate = 1.0f)

        calibrator.calibrate(mapOf(BlendShape.JAW_OPEN to 0.0f))
        calibrator.calibrate(mapOf(BlendShape.JAW_OPEN to 1.0f))
        calibrator.reset()

        // After reset with warmupFrames=0, first call: min=max=value, range<EPSILON → pass-through
        val result = calibrator.calibrate(mapOf(BlendShape.JAW_OPEN to 0.7f))
        assertApprox(0.7f, result[BlendShape.JAW_OPEN]!!)
    }
}
