package io.github.kmpfacelink

import io.github.kmpfacelink.model.FaceLandmark
import io.github.kmpfacelink.util.MouthSolvers
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertTrue

class MouthSolversTest {

    private fun assertApprox(expected: Float, actual: Float, epsilon: Float = 0.05f) {
        assertTrue(
            abs(expected - actual) < epsilon,
            "Expected ~$expected but got $actual (epsilon=$epsilon)",
        )
    }

    private fun assertInRange(value: Float, min: Float = 0f, max: Float = 1f) {
        assertTrue(
            value in min..max,
            "Expected value in [$min, $max] but got $value",
        )
    }

    private fun makeLandmarks(
        overrides: Map<Int, FaceLandmark> = emptyMap(),
        default: FaceLandmark = FaceLandmark(0.5f, 0.5f, 0f),
    ): List<FaceLandmark> {
        val list = MutableList(478) { default }
        for ((index, landmark) in overrides) {
            list[index] = landmark
        }
        return list
    }

    /**
     * Neutral face with mouth landmarks in relaxed positions.
     * IOD = distance(133, 362) = distance((0.42, 0.38), (0.58, 0.38)) = 0.16
     */
    private fun neutralFace(): List<FaceLandmark> = makeLandmarks(
        overrides = mapOf(
            // IOD reference
            133 to FaceLandmark(0.42f, 0.38f, 0f),
            362 to FaceLandmark(0.58f, 0.38f, 0f),
            // Mouth corners (level with lip center)
            61 to FaceLandmark(0.44f, 0.60f, -0.02f),
            291 to FaceLandmark(0.56f, 0.60f, -0.02f),
            // Lip center reference
            13 to FaceLandmark(0.50f, 0.58f, -0.03f),
            14 to FaceLandmark(0.50f, 0.62f, -0.03f),
            // Left outer lip (upper/lower)
            185 to FaceLandmark(0.44f, 0.58f, -0.02f),
            146 to FaceLandmark(0.44f, 0.62f, -0.02f),
            // Right outer lip (upper/lower)
            409 to FaceLandmark(0.56f, 0.58f, -0.02f),
            375 to FaceLandmark(0.56f, 0.62f, -0.02f),
        ),
    )

    // ── mouthSmile tests ──

    @Test
    fun mouthSmile_neutral_lowValue() {
        val lm = neutralFace()
        val left = MouthSolvers.solveMouthSmileLeft(lm)
        val right = MouthSolvers.solveMouthSmileRight(lm)
        assertInRange(left, 0f, 0.5f)
        assertInRange(right, 0f, 0.5f)
    }

    @Test
    fun mouthSmile_activated_highValue() {
        val lm = neutralFace().toMutableList()
        // Move both corners upward (Y decreases)
        lm[61] = FaceLandmark(0.44f, 0.56f, -0.02f)
        lm[291] = FaceLandmark(0.56f, 0.56f, -0.02f)
        val left = MouthSolvers.solveMouthSmileLeft(lm)
        val right = MouthSolvers.solveMouthSmileRight(lm)
        assertTrue(left > 0.3f, "Smiling left should produce high value, got $left")
        assertTrue(right > 0.3f, "Smiling right should produce high value, got $right")
    }

    @Test
    fun mouthSmile_symmetric_equalValues() {
        val lm = neutralFace().toMutableList()
        lm[61] = FaceLandmark(0.44f, 0.57f, -0.02f)
        lm[291] = FaceLandmark(0.56f, 0.57f, -0.02f)
        val left = MouthSolvers.solveMouthSmileLeft(lm)
        val right = MouthSolvers.solveMouthSmileRight(lm)
        assertApprox(left, right, 0.01f)
    }

    // ── mouthFrown tests ──

    @Test
    fun mouthFrown_neutral_lowValue() {
        val lm = neutralFace()
        val left = MouthSolvers.solveMouthFrownLeft(lm)
        val right = MouthSolvers.solveMouthFrownRight(lm)
        assertInRange(left, 0f, 0.5f)
        assertInRange(right, 0f, 0.5f)
    }

    @Test
    fun mouthFrown_activated_highValue() {
        val lm = neutralFace().toMutableList()
        // Move both corners downward (Y increases)
        lm[61] = FaceLandmark(0.44f, 0.64f, -0.02f)
        lm[291] = FaceLandmark(0.56f, 0.64f, -0.02f)
        val left = MouthSolvers.solveMouthFrownLeft(lm)
        val right = MouthSolvers.solveMouthFrownRight(lm)
        assertTrue(left > 0.3f, "Frowning left should produce high value, got $left")
        assertTrue(right > 0.3f, "Frowning right should produce high value, got $right")
    }

    @Test
    fun mouthFrown_symmetric_equalValues() {
        val lm = neutralFace().toMutableList()
        lm[61] = FaceLandmark(0.44f, 0.63f, -0.02f)
        lm[291] = FaceLandmark(0.56f, 0.63f, -0.02f)
        val left = MouthSolvers.solveMouthFrownLeft(lm)
        val right = MouthSolvers.solveMouthFrownRight(lm)
        assertApprox(left, right, 0.01f)
    }

    // ── mouthStretch tests ──

    @Test
    fun mouthStretch_neutral_lowValue() {
        val lm = neutralFace()
        val left = MouthSolvers.solveMouthStretchLeft(lm)
        val right = MouthSolvers.solveMouthStretchRight(lm)
        assertInRange(left, 0f, 0.5f)
        assertInRange(right, 0f, 0.5f)
    }

    @Test
    fun mouthStretch_compressed_highValue() {
        val lm = neutralFace().toMutableList()
        // Compress left outer lip pair vertically
        lm[185] = FaceLandmark(0.44f, 0.595f, -0.02f)
        lm[146] = FaceLandmark(0.44f, 0.605f, -0.02f)
        val left = MouthSolvers.solveMouthStretchLeft(lm)
        assertTrue(left > 0.3f, "Stretched left should produce high value, got $left")
    }

    @Test
    fun mouthStretch_symmetric_equalValues() {
        val lm = neutralFace().toMutableList()
        // Compress both sides equally
        lm[185] = FaceLandmark(0.44f, 0.595f, -0.02f)
        lm[146] = FaceLandmark(0.44f, 0.605f, -0.02f)
        lm[409] = FaceLandmark(0.56f, 0.595f, -0.02f)
        lm[375] = FaceLandmark(0.56f, 0.605f, -0.02f)
        val left = MouthSolvers.solveMouthStretchLeft(lm)
        val right = MouthSolvers.solveMouthStretchRight(lm)
        assertApprox(left, right, 0.01f)
    }

    // ── mouthPress tests ──

    @Test
    fun mouthPress_neutral_lowValue() {
        val lm = neutralFace()
        val left = MouthSolvers.solveMouthPressLeft(lm)
        val right = MouthSolvers.solveMouthPressRight(lm)
        assertInRange(left, 0f, 0.5f)
        assertInRange(right, 0f, 0.5f)
    }

    @Test
    fun mouthPress_activated_highValue() {
        val lm = neutralFace().toMutableList()
        // Press corners: corner gap shrinks while center gap stays normal
        lm[185] = FaceLandmark(0.44f, 0.598f, -0.02f)
        lm[146] = FaceLandmark(0.44f, 0.602f, -0.02f)
        val left = MouthSolvers.solveMouthPressLeft(lm)
        assertTrue(left > 0.3f, "Pressed left should produce high value, got $left")
    }

    @Test
    fun mouthPress_symmetric_equalValues() {
        val lm = neutralFace().toMutableList()
        lm[185] = FaceLandmark(0.44f, 0.598f, -0.02f)
        lm[146] = FaceLandmark(0.44f, 0.602f, -0.02f)
        lm[409] = FaceLandmark(0.56f, 0.598f, -0.02f)
        lm[375] = FaceLandmark(0.56f, 0.602f, -0.02f)
        val left = MouthSolvers.solveMouthPressLeft(lm)
        val right = MouthSolvers.solveMouthPressRight(lm)
        assertApprox(left, right, 0.01f)
    }

    // ── Edge cases ──

    @Test
    fun solvers_returnZero_whenIODIsZero() {
        val lm = makeLandmarks()
        assertApprox(0f, MouthSolvers.solveMouthSmileLeft(lm), 0.01f)
        assertApprox(0f, MouthSolvers.solveMouthSmileRight(lm), 0.01f)
        assertApprox(0f, MouthSolvers.solveMouthFrownLeft(lm), 0.01f)
        assertApprox(0f, MouthSolvers.solveMouthFrownRight(lm), 0.01f)
        assertApprox(0f, MouthSolvers.solveMouthStretchLeft(lm), 0.01f)
        assertApprox(0f, MouthSolvers.solveMouthStretchRight(lm), 0.01f)
        assertApprox(0f, MouthSolvers.solveMouthPressLeft(lm), 0.01f)
        assertApprox(0f, MouthSolvers.solveMouthPressRight(lm), 0.01f)
    }
}
