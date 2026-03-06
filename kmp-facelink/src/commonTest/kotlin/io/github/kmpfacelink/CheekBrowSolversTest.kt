package io.github.kmpfacelink

import io.github.kmpfacelink.model.FaceLandmark
import io.github.kmpfacelink.util.CheekBrowSolvers
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertTrue

class CheekBrowSolversTest {

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
     * Neutral face with landmark positions representing a relaxed expression.
     * IOD = distance(133, 362) = distance((0.42, 0.38), (0.58, 0.38)) = 0.16
     *
     * Coordinate conventions (MediaPipe normalized):
     * - Y increases downward
     * - Brow landmarks have lower Y values (higher on face) than eyelid landmarks
     */
    private fun neutralFace(): List<FaceLandmark> = makeLandmarks(
        overrides = mapOf(
            // IOD reference
            133 to FaceLandmark(0.42f, 0.38f, 0f),
            362 to FaceLandmark(0.58f, 0.38f, 0f),
            // Nose bridge (vertical reference for brow-up solvers)
            6 to FaceLandmark(0.50f, 0.42f, -0.01f),
            // Right eye upper eyelid (landmark 159 = RIGHT_EYE_UPPER_2)
            159 to FaceLandmark(0.44f, 0.365f, -0.01f),
            // Left eye upper eyelid (landmark 386 = LEFT_EYE_UPPER_2)
            386 to FaceLandmark(0.56f, 0.365f, -0.01f),
            // Right eye lower lid (144 = RIGHT_EYE_LOWER_1, 145 = RIGHT_EYE_LOWER_2)
            144 to FaceLandmark(0.43f, 0.395f, -0.01f),
            145 to FaceLandmark(0.44f, 0.397f, -0.01f),
            // Left eye lower lid (374 = LEFT_EYE_LOWER_2, 380 = LEFT_EYE_LOWER_3)
            374 to FaceLandmark(0.56f, 0.395f, -0.01f),
            380 to FaceLandmark(0.57f, 0.397f, -0.01f),
            // Right infraorbital (50): below right eye
            50 to FaceLandmark(0.44f, 0.408f, -0.01f),
            // Left infraorbital (280): below left eye
            280 to FaceLandmark(0.56f, 0.408f, -0.01f),
            // Right brow inner (70), mid (105): above right eye, neutral is ~0.12 IOD above eyelid
            70 to FaceLandmark(0.43f, 0.323f, 0f),
            105 to FaceLandmark(0.44f, 0.319f, 0f),
            // Left brow inner (300), mid (334): above left eye
            300 to FaceLandmark(0.57f, 0.323f, 0f),
            334 to FaceLandmark(0.56f, 0.319f, 0f),
        ),
    )

    // ── cheekSquint tests ──

    @Test
    fun cheekSquint_neutral_lowValue() {
        val lm = neutralFace()
        val left = CheekBrowSolvers.solveCheekSquintLeft(lm)
        val right = CheekBrowSolvers.solveCheekSquintRight(lm)
        assertInRange(left, 0f, 0.5f)
        assertInRange(right, 0f, 0.5f)
    }

    @Test
    fun cheekSquint_activated_highValue() {
        val lm = neutralFace().toMutableList()
        // Compress left under-eye: move infraorbital closer to lower eyelid
        lm[280] = FaceLandmark(0.56f, 0.398f, -0.01f)
        // Compress right under-eye: move infraorbital closer to lower eyelid
        lm[50] = FaceLandmark(0.44f, 0.398f, -0.01f)
        val left = CheekBrowSolvers.solveCheekSquintLeft(lm)
        val right = CheekBrowSolvers.solveCheekSquintRight(lm)
        assertTrue(left > 0.3f, "Cheek squint left activated should produce high value, got $left")
        assertTrue(right > 0.3f, "Cheek squint right activated should produce high value, got $right")
    }

    @Test
    fun cheekSquint_symmetric_equalValues() {
        val lm = neutralFace().toMutableList()
        lm[280] = FaceLandmark(0.56f, 0.399f, -0.01f)
        lm[374] = FaceLandmark(0.56f, 0.396f, -0.01f)
        lm[380] = FaceLandmark(0.57f, 0.396f, -0.01f)
        lm[50] = FaceLandmark(0.44f, 0.399f, -0.01f)
        lm[144] = FaceLandmark(0.43f, 0.396f, -0.01f)
        lm[145] = FaceLandmark(0.44f, 0.396f, -0.01f)
        val left = CheekBrowSolvers.solveCheekSquintLeft(lm)
        val right = CheekBrowSolvers.solveCheekSquintRight(lm)
        assertApprox(left, right, 0.05f)
    }

    // ── browDown tests ──

    @Test
    fun browDown_neutral_lowValue() {
        val lm = neutralFace()
        val left = CheekBrowSolvers.solveBrowDownLeft(lm)
        val right = CheekBrowSolvers.solveBrowDownRight(lm)
        assertInRange(left, 0f, 0.5f)
        assertInRange(right, 0f, 0.5f)
    }

    @Test
    fun browDown_activated_highValue() {
        val lm = neutralFace().toMutableList()
        // Move left brow down (Y increases) toward eyelid
        lm[300] = FaceLandmark(0.57f, 0.350f, 0f)
        lm[334] = FaceLandmark(0.56f, 0.346f, 0f)
        // Move right brow down (Y increases) toward eyelid
        lm[70] = FaceLandmark(0.43f, 0.350f, 0f)
        lm[105] = FaceLandmark(0.44f, 0.346f, 0f)
        val left = CheekBrowSolvers.solveBrowDownLeft(lm)
        val right = CheekBrowSolvers.solveBrowDownRight(lm)
        assertTrue(left > 0.3f, "Brow down left activated should produce high value, got $left")
        assertTrue(right > 0.3f, "Brow down right activated should produce high value, got $right")
    }

    @Test
    fun browDown_symmetric_equalValues() {
        val lm = neutralFace().toMutableList()
        lm[300] = FaceLandmark(0.57f, 0.348f, 0f)
        lm[334] = FaceLandmark(0.56f, 0.344f, 0f)
        lm[70] = FaceLandmark(0.43f, 0.348f, 0f)
        lm[105] = FaceLandmark(0.44f, 0.344f, 0f)
        val left = CheekBrowSolvers.solveBrowDownLeft(lm)
        val right = CheekBrowSolvers.solveBrowDownRight(lm)
        assertApprox(left, right, 0.05f)
    }

    // ── browInnerUp tests ──

    @Test
    fun browInnerUp_neutral_lowValue() {
        val lm = neutralFace()
        val value = CheekBrowSolvers.solveBrowInnerUp(lm)
        assertInRange(value, 0f, 0.5f)
    }

    @Test
    fun browInnerUp_activated_highValue() {
        val lm = neutralFace().toMutableList()
        // Raise both inner brows upward (Y decreases)
        lm[70] = FaceLandmark(0.43f, 0.290f, 0f)
        lm[300] = FaceLandmark(0.57f, 0.290f, 0f)
        val value = CheekBrowSolvers.solveBrowInnerUp(lm)
        assertTrue(value > 0.3f, "Brow inner up activated should produce high value, got $value")
    }

    // ── browOuterUp tests ──

    @Test
    fun browOuterUp_neutral_lowValue() {
        val lm = neutralFace()
        val left = CheekBrowSolvers.solveBrowOuterUpLeft(lm)
        val right = CheekBrowSolvers.solveBrowOuterUpRight(lm)
        assertInRange(left, 0f, 0.5f)
        assertInRange(right, 0f, 0.5f)
    }

    @Test
    fun browOuterUp_activated_highValue() {
        val lm = neutralFace().toMutableList()
        // Raise left outer brow upward (Y decreases)
        lm[334] = FaceLandmark(0.56f, 0.280f, 0f)
        // Raise right outer brow upward (Y decreases)
        lm[105] = FaceLandmark(0.44f, 0.280f, 0f)
        val left = CheekBrowSolvers.solveBrowOuterUpLeft(lm)
        val right = CheekBrowSolvers.solveBrowOuterUpRight(lm)
        assertTrue(left > 0.3f, "Brow outer up left activated should produce high value, got $left")
        assertTrue(right > 0.3f, "Brow outer up right activated should produce high value, got $right")
    }

    @Test
    fun browOuterUp_symmetric_equalValues() {
        val lm = neutralFace().toMutableList()
        lm[334] = FaceLandmark(0.56f, 0.285f, 0f)
        lm[105] = FaceLandmark(0.44f, 0.285f, 0f)
        val left = CheekBrowSolvers.solveBrowOuterUpLeft(lm)
        val right = CheekBrowSolvers.solveBrowOuterUpRight(lm)
        assertApprox(left, right, 0.01f)
    }

    // ── Edge cases ──

    @Test
    fun solvers_returnZero_whenIODIsZero() {
        val lm = makeLandmarks()
        assertApprox(0f, CheekBrowSolvers.solveCheekSquintLeft(lm), 0.01f)
        assertApprox(0f, CheekBrowSolvers.solveCheekSquintRight(lm), 0.01f)
        assertApprox(0f, CheekBrowSolvers.solveBrowDownLeft(lm), 0.01f)
        assertApprox(0f, CheekBrowSolvers.solveBrowDownRight(lm), 0.01f)
        assertApprox(0f, CheekBrowSolvers.solveBrowInnerUp(lm), 0.01f)
        assertApprox(0f, CheekBrowSolvers.solveBrowOuterUpLeft(lm), 0.01f)
        assertApprox(0f, CheekBrowSolvers.solveBrowOuterUpRight(lm), 0.01f)
    }
}
