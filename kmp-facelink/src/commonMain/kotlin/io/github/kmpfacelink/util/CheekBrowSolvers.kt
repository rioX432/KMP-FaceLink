package io.github.kmpfacelink.util

import io.github.kmpfacelink.model.FaceLandmark
import io.github.kmpfacelink.util.LandmarkGeometry.remap
import io.github.kmpfacelink.util.LandmarkIndices.LEFT_BROW_INNER
import io.github.kmpfacelink.util.LandmarkIndices.LEFT_BROW_MID
import io.github.kmpfacelink.util.LandmarkIndices.LEFT_EYE_LOWER_2
import io.github.kmpfacelink.util.LandmarkIndices.LEFT_EYE_LOWER_3
import io.github.kmpfacelink.util.LandmarkIndices.LEFT_EYE_UPPER_2
import io.github.kmpfacelink.util.LandmarkIndices.LEFT_INFRAORBITAL
import io.github.kmpfacelink.util.LandmarkIndices.NOSE_BRIDGE
import io.github.kmpfacelink.util.LandmarkIndices.RIGHT_BROW_INNER
import io.github.kmpfacelink.util.LandmarkIndices.RIGHT_BROW_MID
import io.github.kmpfacelink.util.LandmarkIndices.RIGHT_EYE_LOWER_1
import io.github.kmpfacelink.util.LandmarkIndices.RIGHT_EYE_LOWER_2
import io.github.kmpfacelink.util.LandmarkIndices.RIGHT_EYE_UPPER_2
import io.github.kmpfacelink.util.LandmarkIndices.RIGHT_INFRAORBITAL

/**
 * Geometric solvers for cheek squint and brow expression blend shapes.
 *
 * Each solver uses IOD-normalized 2D landmark distances and [remap] to produce [0, 1].
 * Thresholds are initial estimates — tune with real device data.
 */
// Each public function maps 1:1 to an ARKit blend shape (7 solvers for 7 shapes).
// Splitting into separate objects would scatter related geometric computations.
@Suppress("TooManyFunctions")
internal object CheekBrowSolvers {

    // ── Tuning thresholds ──

    // Cheek squint: vertical compression of under-eye region.
    // Neutral (infraorbital - avgLowerLid) / IOD ≈ 0.075; squinting compresses gap toward 0.
    private const val CHEEK_SQUINT_LOW = 0.0f
    private const val CHEEK_SQUINT_HIGH = 0.06f
    private const val NEUTRAL_UNDER_EYE_GAP_IOD_RATIO = 0.075f

    // Brow down: brow-to-upper-eyelid gap decrease.
    // Neutral (eyelidY - avgBrowY) / IOD ≈ 0.28; descending brow compresses gap.
    private const val BROW_DOWN_LOW = 0.0f
    private const val BROW_DOWN_HIGH = 0.15f
    private const val NEUTRAL_BROW_EYELID_GAP_IOD_RATIO = 0.28f

    // Brow inner up: inner brow points rise relative to nose bridge.
    // Neutral (bridgeY - avgInnerBrowY) / IOD ≈ 0.60; raising increases this ratio.
    private const val BROW_INNER_UP_LOW = 0.0f
    private const val BROW_INNER_UP_HIGH = 0.15f
    private const val NEUTRAL_INNER_BROW_BRIDGE_IOD_RATIO = 0.60f

    // Brow outer up: outer brow mid-point rises relative to nose bridge.
    // Neutral (bridgeY - outerBrowY) / IOD ≈ 0.63; raising increases this ratio.
    private const val BROW_OUTER_UP_LOW = 0.0f
    private const val BROW_OUTER_UP_HIGH = 0.15f
    private const val NEUTRAL_OUTER_BROW_BRIDGE_IOD_RATIO = 0.63f

    // ── Solvers ──

    /**
     * Solve cheekSquintLeft: vertical compression of the left under-eye region.
     * Uses landmark 374 (LEFT_EYE_LOWER_2), 380 (LEFT_EYE_LOWER_3), and 280 (LEFT_INFRAORBITAL).
     */
    fun solveCheekSquintLeft(landmarks: List<FaceLandmark>): Float {
        val iod = LandmarkIndices.iod(landmarks)
        if (iod <= 0f) return 0f
        // Under-eye gap: average Y distance from lower eyelid to infraorbital point
        val lower2Y = landmarks[LEFT_EYE_LOWER_2].y
        val lower3Y = landmarks[LEFT_EYE_LOWER_3].y
        val infraY = landmarks[LEFT_INFRAORBITAL].y
        val avgLowerY = (lower2Y + lower3Y) / 2f
        // Positive gap means infraorbital is below (higher Y) than eyelid
        val gap = (infraY - avgLowerY) / iod
        val compression = NEUTRAL_UNDER_EYE_GAP_IOD_RATIO - gap
        return remap(compression, CHEEK_SQUINT_LOW, CHEEK_SQUINT_HIGH)
    }

    /**
     * Solve cheekSquintRight: vertical compression of the right under-eye region.
     * Uses landmark 144 (RIGHT_EYE_LOWER_1), 145 (RIGHT_EYE_LOWER_2), and 50 (RIGHT_INFRAORBITAL).
     */
    fun solveCheekSquintRight(landmarks: List<FaceLandmark>): Float {
        val iod = LandmarkIndices.iod(landmarks)
        if (iod <= 0f) return 0f
        val lower1Y = landmarks[RIGHT_EYE_LOWER_1].y
        val lower2Y = landmarks[RIGHT_EYE_LOWER_2].y
        val infraY = landmarks[RIGHT_INFRAORBITAL].y
        val avgLowerY = (lower1Y + lower2Y) / 2f
        val gap = (infraY - avgLowerY) / iod
        val compression = NEUTRAL_UNDER_EYE_GAP_IOD_RATIO - gap
        return remap(compression, CHEEK_SQUINT_LOW, CHEEK_SQUINT_HIGH)
    }

    /**
     * Solve browDownLeft: left brow descends toward upper eyelid.
     * Uses landmark 300 (LEFT_BROW_INNER), 334 (LEFT_BROW_MID), and 386 (LEFT_EYE_UPPER_2).
     */
    fun solveBrowDownLeft(landmarks: List<FaceLandmark>): Float {
        val iod = LandmarkIndices.iod(landmarks)
        if (iod <= 0f) return 0f
        // Average brow Y from inner and mid brow points
        val browInnerY = landmarks[LEFT_BROW_INNER].y
        val browMidY = landmarks[LEFT_BROW_MID].y
        val avgBrowY = (browInnerY + browMidY) / 2f
        val eyelidY = landmarks[LEFT_EYE_UPPER_2].y
        // Gap: brow is above (lower Y) eyelid; positive when brow is above eyelid
        val gap = (eyelidY - avgBrowY) / iod
        // When brow descends, gap decreases below neutral
        val descent = NEUTRAL_BROW_EYELID_GAP_IOD_RATIO - gap
        return remap(descent, BROW_DOWN_LOW, BROW_DOWN_HIGH)
    }

    /**
     * Solve browDownRight: right brow descends toward upper eyelid.
     * Uses landmark 70 (RIGHT_BROW_INNER), 105 (RIGHT_BROW_MID), and 159 (RIGHT_EYE_UPPER_2).
     */
    fun solveBrowDownRight(landmarks: List<FaceLandmark>): Float {
        val iod = LandmarkIndices.iod(landmarks)
        if (iod <= 0f) return 0f
        val browInnerY = landmarks[RIGHT_BROW_INNER].y
        val browMidY = landmarks[RIGHT_BROW_MID].y
        val avgBrowY = (browInnerY + browMidY) / 2f
        val eyelidY = landmarks[RIGHT_EYE_UPPER_2].y
        val gap = (eyelidY - avgBrowY) / iod
        val descent = NEUTRAL_BROW_EYELID_GAP_IOD_RATIO - gap
        return remap(descent, BROW_DOWN_LOW, BROW_DOWN_HIGH)
    }

    /**
     * Solve browInnerUp: both inner brow points rise relative to nose bridge.
     * Uses landmark 70 (RIGHT_BROW_INNER), 300 (LEFT_BROW_INNER), and 6 (NOSE_BRIDGE).
     */
    fun solveBrowInnerUp(landmarks: List<FaceLandmark>): Float {
        val iod = LandmarkIndices.iod(landmarks)
        if (iod <= 0f) return 0f
        val rightBrowY = landmarks[RIGHT_BROW_INNER].y
        val leftBrowY = landmarks[LEFT_BROW_INNER].y
        val bridgeY = landmarks[NOSE_BRIDGE].y
        // Average inner brow Y; when raised, Y decreases (moves up in image coords)
        val avgBrowY = (rightBrowY + leftBrowY) / 2f
        // Distance above bridge (positive = brow is above bridge)
        val distAboveBridge = (bridgeY - avgBrowY) / iod
        // Rise above neutral baseline
        val rise = distAboveBridge - NEUTRAL_INNER_BROW_BRIDGE_IOD_RATIO
        return remap(rise, BROW_INNER_UP_LOW, BROW_INNER_UP_HIGH)
    }

    /**
     * Solve browOuterUpLeft: left outer brow point rises relative to nose bridge.
     * Uses landmark 334 (LEFT_BROW_MID) and 6 (NOSE_BRIDGE).
     */
    fun solveBrowOuterUpLeft(landmarks: List<FaceLandmark>): Float {
        val iod = LandmarkIndices.iod(landmarks)
        if (iod <= 0f) return 0f
        val browY = landmarks[LEFT_BROW_MID].y
        val bridgeY = landmarks[NOSE_BRIDGE].y
        val distAboveBridge = (bridgeY - browY) / iod
        val rise = distAboveBridge - NEUTRAL_OUTER_BROW_BRIDGE_IOD_RATIO
        return remap(rise, BROW_OUTER_UP_LOW, BROW_OUTER_UP_HIGH)
    }

    /**
     * Solve browOuterUpRight: right outer brow point rises relative to nose bridge.
     * Uses landmark 105 (RIGHT_BROW_MID) and 6 (NOSE_BRIDGE).
     */
    fun solveBrowOuterUpRight(landmarks: List<FaceLandmark>): Float {
        val iod = LandmarkIndices.iod(landmarks)
        if (iod <= 0f) return 0f
        val browY = landmarks[RIGHT_BROW_MID].y
        val bridgeY = landmarks[NOSE_BRIDGE].y
        val distAboveBridge = (bridgeY - browY) / iod
        val rise = distAboveBridge - NEUTRAL_OUTER_BROW_BRIDGE_IOD_RATIO
        return remap(rise, BROW_OUTER_UP_LOW, BROW_OUTER_UP_HIGH)
    }
}
