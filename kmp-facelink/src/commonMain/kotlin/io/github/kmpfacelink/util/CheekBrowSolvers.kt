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
    // All ratio values are IOD-normalized unless stated otherwise.
    // "LOW" is the value at which the blend shape starts activating (output = 0).
    // "HIGH" is the value at which the blend shape reaches full activation (output = 1).
    // To tune: log the raw ratio for a target expression on a real device, then set
    // LOW just below the neutral-to-active transition and HIGH at comfortable full activation.

    /**
     * Cheek squint thresholds: vertical compression of the under-eye region.
     *
     * Raw compression = [NEUTRAL_UNDER_EYE_GAP_IOD_RATIO] - ((infraorbitalY - avgLowerLidY) / IOD).
     * The neutral gap between the lower eyelid and the infraorbital point is ~0.075 IOD units.
     * Squinting raises the cheek and compresses this gap; full squint compresses it by ~0.05–0.06.
     *
     * Tuning: if the solver activates during relaxed blinking, increase [NEUTRAL_UNDER_EYE_GAP_IOD_RATIO]
     * to match the resting gap; increase [CHEEK_SQUINT_HIGH] if full squint never saturates.
     */
    private const val CHEEK_SQUINT_LOW = 0.0f
    private const val CHEEK_SQUINT_HIGH = 0.06f

    /**
     * Neutral under-eye gap ratio: (infraorbitalY - avgLowerLidY) / IOD for a relaxed face.
     *
     * Empirically ~0.07–0.08 across a range of face geometries; 0.075 is the default.
     * Adjust this first if [solveCheekSquintLeft] or [solveCheekSquintRight] has a non-zero
     * baseline output when the face is neutral and relaxed.
     */
    private const val NEUTRAL_UNDER_EYE_GAP_IOD_RATIO = 0.075f

    /**
     * Brow-down thresholds: gap decrease between the brow and the upper eyelid.
     *
     * Raw descent = [NEUTRAL_BROW_EYELID_GAP_IOD_RATIO] - ((eyelidY - avgBrowY) / IOD).
     * The neutral gap is ~0.28 IOD units; a furrowed/lowered brow compresses it by ~0.10–0.15.
     *
     * Tuning: lower [NEUTRAL_BROW_EYELID_GAP_IOD_RATIO] if natural brow position is already
     * low; increase [BROW_DOWN_HIGH] if the solver saturates before a full frown is reached.
     */
    private const val BROW_DOWN_LOW = 0.0f
    private const val BROW_DOWN_HIGH = 0.15f

    /**
     * Neutral brow-to-eyelid gap ratio: (eyelidY - avgBrowY) / IOD for a relaxed face.
     *
     * Empirically ~0.25–0.30; 0.28 is the default.
     * Adjust when [solveBrowDownLeft] or [solveBrowDownRight] fires at rest (raise value)
     * or when it never fires during an obvious frown (lower value).
     */
    private const val NEUTRAL_BROW_EYELID_GAP_IOD_RATIO = 0.28f

    /**
     * Brow-inner-up thresholds: rise of both inner brow points above the nose bridge.
     *
     * Raw rise = ((bridgeY - avgInnerBrowY) / IOD) - [NEUTRAL_INNER_BROW_BRIDGE_IOD_RATIO].
     * Neutral distance above the bridge is ~0.60 IOD units; raising the inner brows by a
     * clearly visible amount increases the ratio by ~0.10–0.15.
     *
     * Tuning: raise [NEUTRAL_INNER_BROW_BRIDGE_IOD_RATIO] if the solver activates at rest;
     * raise [BROW_INNER_UP_HIGH] if a full raised-brow expression never reaches 1.0.
     */
    private const val BROW_INNER_UP_LOW = 0.0f
    private const val BROW_INNER_UP_HIGH = 0.15f

    /**
     * Neutral inner-brow-to-bridge distance ratio: (bridgeY - avgInnerBrowY) / IOD at rest.
     *
     * Empirically ~0.57–0.63; 0.60 is the default.
     * Adjust when [solveBrowInnerUp] has non-zero output on a relaxed face.
     */
    private const val NEUTRAL_INNER_BROW_BRIDGE_IOD_RATIO = 0.60f

    /**
     * Brow-outer-up thresholds: rise of the outer brow mid-point above the nose bridge.
     *
     * Raw rise = ((bridgeY - outerBrowY) / IOD) - [NEUTRAL_OUTER_BROW_BRIDGE_IOD_RATIO].
     * Neutral distance is ~0.63 IOD units (outer brow sits slightly higher than inner);
     * raising the outer brow adds ~0.10–0.15.
     *
     * Tuning: mirror the guidance for [BROW_INNER_UP_LOW]/[BROW_INNER_UP_HIGH] above.
     * The outer brow baseline is typically a bit higher than the inner brow baseline.
     */
    private const val BROW_OUTER_UP_LOW = 0.0f
    private const val BROW_OUTER_UP_HIGH = 0.15f

    /**
     * Neutral outer-brow-to-bridge distance ratio: (bridgeY - outerBrowMidY) / IOD at rest.
     *
     * Empirically ~0.60–0.66; 0.63 is the default.
     * Adjust when [solveBrowOuterUpLeft] or [solveBrowOuterUpRight] fires at rest.
     */
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
