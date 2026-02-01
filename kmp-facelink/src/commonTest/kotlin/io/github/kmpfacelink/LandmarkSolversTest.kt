package io.github.kmpfacelink

import io.github.kmpfacelink.model.FaceLandmark
import io.github.kmpfacelink.util.LandmarkSolvers
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertTrue

class LandmarkSolversTest {

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

    /**
     * Generate a minimal landmark list with 478 points, all at the given default position.
     * Then override specific indices as needed.
     */
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
     * Create landmarks for a "neutral face" with reasonable geometry.
     * Eyes, nose, jaw, mouth all in typical neutral positions.
     */
    private fun neutralFace(): List<FaceLandmark> = makeLandmarks(
        overrides = mapOf(
            // IOD reference: right eye inner (133), left eye inner (362)
            133 to FaceLandmark(0.42f, 0.38f, 0f),
            362 to FaceLandmark(0.58f, 0.38f, 0f),
            // Right eye: outer=33, upper=160/159/158, lower=144/145/153
            33 to FaceLandmark(0.35f, 0.38f, 0f),
            160 to FaceLandmark(0.38f, 0.36f, 0f),
            159 to FaceLandmark(0.40f, 0.355f, 0f),
            158 to FaceLandmark(0.41f, 0.36f, 0f),
            144 to FaceLandmark(0.38f, 0.40f, 0f),
            145 to FaceLandmark(0.40f, 0.405f, 0f),
            153 to FaceLandmark(0.41f, 0.40f, 0f),
            // Left eye: outer=263, upper=387/386/385, lower=373/374/380
            263 to FaceLandmark(0.65f, 0.38f, 0f),
            387 to FaceLandmark(0.59f, 0.36f, 0f),
            386 to FaceLandmark(0.60f, 0.355f, 0f),
            385 to FaceLandmark(0.61f, 0.36f, 0f),
            373 to FaceLandmark(0.59f, 0.40f, 0f),
            374 to FaceLandmark(0.60f, 0.405f, 0f),
            380 to FaceLandmark(0.61f, 0.40f, 0f),
            // Nose
            6 to FaceLandmark(0.50f, 0.37f, -0.05f),
            1 to FaceLandmark(0.50f, 0.52f, -0.06f),
            48 to FaceLandmark(0.46f, 0.50f, -0.03f),
            278 to FaceLandmark(0.54f, 0.50f, -0.03f),
            // Chin
            152 to FaceLandmark(0.50f, 0.72f, -0.02f),
            // Mouth corners
            61 to FaceLandmark(0.44f, 0.60f, -0.02f),
            291 to FaceLandmark(0.56f, 0.60f, -0.02f),
            // Cheeks (face oval sides)
            234 to FaceLandmark(0.25f, 0.45f, 0.03f),
            454 to FaceLandmark(0.75f, 0.45f, 0.03f),
        ),
    )

    // ── eyeWide tests ──

    @Test
    fun eyeWide_neutralFace_lowValue() {
        val lm = neutralFace()
        val right = LandmarkSolvers.solveEyeWideRight(lm)
        val left = LandmarkSolvers.solveEyeWideLeft(lm)
        // Neutral open eyes should map to moderate value (below "wide")
        assertInRange(right, 0f, 1f)
        assertInRange(left, 0f, 1f)
    }

    @Test
    fun eyeWide_wideOpen_highValue() {
        // Wide open eyes: increase vertical lid distance
        val lm = neutralFace().toMutableList()
        // Right eye: push upper lids up, lower lids down
        lm[160] = FaceLandmark(0.38f, 0.33f, 0f)
        lm[159] = FaceLandmark(0.40f, 0.32f, 0f)
        lm[158] = FaceLandmark(0.41f, 0.33f, 0f)
        lm[144] = FaceLandmark(0.38f, 0.43f, 0f)
        lm[145] = FaceLandmark(0.40f, 0.44f, 0f)
        lm[153] = FaceLandmark(0.41f, 0.43f, 0f)
        val right = LandmarkSolvers.solveEyeWideRight(lm)
        assertTrue(right > 0.5f, "Wide open right eye should produce high value, got $right")
    }

    @Test
    fun eyeWide_closed_zero() {
        // Closed eyes: upper and lower lids at same Y
        val lm = neutralFace().toMutableList()
        lm[160] = FaceLandmark(0.38f, 0.38f, 0f)
        lm[159] = FaceLandmark(0.40f, 0.38f, 0f)
        lm[158] = FaceLandmark(0.41f, 0.38f, 0f)
        lm[144] = FaceLandmark(0.38f, 0.38f, 0f)
        lm[145] = FaceLandmark(0.40f, 0.38f, 0f)
        lm[153] = FaceLandmark(0.41f, 0.38f, 0f)
        val right = LandmarkSolvers.solveEyeWideRight(lm)
        assertApprox(0f, right, 0.1f)
    }

    // ── noseSneer tests ──

    @Test
    fun noseSneer_neutral_nearZero() {
        val lm = neutralFace()
        val left = LandmarkSolvers.solveNoseSneerLeft(lm)
        val right = LandmarkSolvers.solveNoseSneerRight(lm)
        // Neutral should be low (below sneer threshold after baseline adjustment)
        assertInRange(left, 0f, 0.5f)
        assertInRange(right, 0f, 0.5f)
    }

    @Test
    fun noseSneer_sneering_highValue() {
        val lm = neutralFace().toMutableList()
        // Move left nose wing upward significantly (Y decreases)
        lm[48] = FaceLandmark(0.46f, 0.46f, -0.03f)
        val left = LandmarkSolvers.solveNoseSneerLeft(lm)
        assertTrue(left > 0.3f, "Sneering left should produce higher value, got $left")
    }

    // ── jawForward tests ──

    @Test
    fun jawForward_neutral_nearZero() {
        val lm = neutralFace()
        val value = LandmarkSolvers.solveJawForward(lm)
        assertInRange(value, 0f, 1f)
    }

    @Test
    fun jawForward_pushed_highValue() {
        val lm = neutralFace().toMutableList()
        // Push chin forward (more negative Z, closer to camera)
        lm[152] = FaceLandmark(0.50f, 0.72f, -0.06f)
        val value = LandmarkSolvers.solveJawForward(lm)
        assertInRange(value, 0f, 1f)
    }

    // ── jawLeft/Right tests ──

    @Test
    fun jawLeft_neutral_nearZero() {
        val lm = neutralFace()
        val value = LandmarkSolvers.solveJawLeft(lm)
        assertApprox(0f, value, 0.15f)
    }

    @Test
    fun jawRight_neutral_nearZero() {
        val lm = neutralFace()
        val value = LandmarkSolvers.solveJawRight(lm)
        assertApprox(0f, value, 0.15f)
    }

    @Test
    fun jawLeft_shifted_highValue() {
        val lm = neutralFace().toMutableList()
        // Shift chin to the left (lower X)
        lm[152] = FaceLandmark(0.47f, 0.72f, -0.02f)
        val value = LandmarkSolvers.solveJawLeft(lm)
        assertTrue(value > 0f, "Jaw shifted left should produce positive value, got $value")
    }

    // ── cheekPuff tests ──

    @Test
    fun cheekPuff_neutral_nearZero() {
        val lm = neutralFace()
        val value = LandmarkSolvers.solveCheekPuff(lm)
        // Neutral cheeks should have low puff value
        assertInRange(value, 0f, 0.6f)
    }

    @Test
    fun cheekPuff_puffed_highValue() {
        val lm = neutralFace().toMutableList()
        // Expand cheeks outward
        lm[234] = FaceLandmark(0.22f, 0.45f, 0.03f)
        lm[454] = FaceLandmark(0.78f, 0.45f, 0.03f)
        val value = LandmarkSolvers.solveCheekPuff(lm)
        assertTrue(value > 0.3f, "Puffed cheeks should produce higher value, got $value")
    }

    // ── mouthDimple tests ──

    @Test
    fun mouthDimple_neutral_low() {
        val lm = neutralFace()
        val value = LandmarkSolvers.solveMouthDimpleLeft(lm)
        assertInRange(value, 0f, 0.5f)
    }

    @Test
    fun mouthDimple_pulled_highValue() {
        val lm = neutralFace().toMutableList()
        // Pull mouth corners outward
        lm[61] = FaceLandmark(0.40f, 0.60f, -0.02f)
        lm[291] = FaceLandmark(0.60f, 0.60f, -0.02f)
        val value = LandmarkSolvers.solveMouthDimpleLeft(lm)
        assertTrue(value > 0.2f, "Pulled mouth corners should produce higher value, got $value")
    }

    // ── Edge cases ──

    @Test
    fun solvers_returnZero_whenIODIsZero() {
        // All landmarks at same position → IOD = 0
        val lm = makeLandmarks()
        assertApprox(0f, LandmarkSolvers.solveEyeWideRight(lm), 0.01f)
        assertApprox(0f, LandmarkSolvers.solveNoseSneerLeft(lm), 0.01f)
        assertApprox(0f, LandmarkSolvers.solveJawForward(lm), 0.01f)
        assertApprox(0f, LandmarkSolvers.solveJawLeft(lm), 0.01f)
        assertApprox(0f, LandmarkSolvers.solveCheekPuff(lm), 0.01f)
        assertApprox(0f, LandmarkSolvers.solveMouthDimpleLeft(lm), 0.01f)
    }
}
