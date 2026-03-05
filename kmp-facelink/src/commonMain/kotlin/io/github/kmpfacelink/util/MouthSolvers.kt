package io.github.kmpfacelink.util

import io.github.kmpfacelink.model.FaceLandmark
import io.github.kmpfacelink.util.LandmarkGeometry.distance2D
import io.github.kmpfacelink.util.LandmarkGeometry.remap
import io.github.kmpfacelink.util.LandmarkIndices.LEFT_LOWER_OUTER_LIP
import io.github.kmpfacelink.util.LandmarkIndices.LEFT_UPPER_OUTER_LIP
import io.github.kmpfacelink.util.LandmarkIndices.LOWER_LIP_BOTTOM
import io.github.kmpfacelink.util.LandmarkIndices.MOUTH_LEFT
import io.github.kmpfacelink.util.LandmarkIndices.MOUTH_RIGHT
import io.github.kmpfacelink.util.LandmarkIndices.RIGHT_LOWER_OUTER_LIP
import io.github.kmpfacelink.util.LandmarkIndices.RIGHT_UPPER_OUTER_LIP
import io.github.kmpfacelink.util.LandmarkIndices.UPPER_LIP_TOP

/**
 * Geometric solvers for mouth expression blend shapes.
 *
 * Each solver uses IOD-normalized 2D landmark distances and [remap] to produce [0, 1].
 * Thresholds are initial estimates — tune with real device data.
 */
internal object MouthSolvers {

    // ── Tuning thresholds ──
    // Smile: mouth corner vertical uplift relative to lip center.
    // Neutral corners are roughly level with lip center; smiling lifts them ~0.03-0.08 IOD.
    private const val SMILE_LOW = 0.0f
    private const val SMILE_HIGH = 0.08f

    // Frown: mouth corner downward displacement relative to lip center.
    private const val FROWN_LOW = 0.0f
    private const val FROWN_HIGH = 0.06f

    // Stretch: lip vertical compression near corners (grimace).
    // Neutral outer lip gap ~0.25 IOD; stretched compresses to near 0.
    private const val NEUTRAL_OUTER_LIP_GAP_IOD_RATIO = 0.25f
    private const val STRETCH_LOW = 0.0f
    private const val STRETCH_HIGH = 0.20f

    // Press: upper-lower lip proximity at corner relative to lip center gap.
    // When pressing, corner gap shrinks more than center gap.
    private const val PRESS_LOW = 0.0f
    private const val PRESS_HIGH = 0.15f

    // ── Helpers ──

    /** Vertical center of upper and lower lip reference points. */
    private fun lipCenterY(lm: List<FaceLandmark>): Float =
        (lm[UPPER_LIP_TOP].y + lm[LOWER_LIP_BOTTOM].y) / 2f

    // ── Solvers ──

    /** Solve mouthSmileLeft: left mouth corner upward lift relative to lip center. */
    fun solveMouthSmileLeft(landmarks: List<FaceLandmark>): Float {
        val iod = LandmarkIndices.iod(landmarks)
        if (iod <= 0f) return 0f
        // When smiling, corner moves up (Y decreases), so lipCenter - corner is positive
        val delta = (lipCenterY(landmarks) - landmarks[MOUTH_LEFT].y) / iod
        return remap(delta, SMILE_LOW, SMILE_HIGH)
    }

    /** Solve mouthSmileRight: right mouth corner upward lift relative to lip center. */
    fun solveMouthSmileRight(landmarks: List<FaceLandmark>): Float {
        val iod = LandmarkIndices.iod(landmarks)
        if (iod <= 0f) return 0f
        val delta = (lipCenterY(landmarks) - landmarks[MOUTH_RIGHT].y) / iod
        return remap(delta, SMILE_LOW, SMILE_HIGH)
    }

    /** Solve mouthFrownLeft: left mouth corner downward displacement. */
    fun solveMouthFrownLeft(landmarks: List<FaceLandmark>): Float {
        val iod = LandmarkIndices.iod(landmarks)
        if (iod <= 0f) return 0f
        // When frowning, corner moves down (Y increases), so corner - lipCenter is positive
        val delta = (landmarks[MOUTH_LEFT].y - lipCenterY(landmarks)) / iod
        return remap(delta, FROWN_LOW, FROWN_HIGH)
    }

    /** Solve mouthFrownRight: right mouth corner downward displacement. */
    fun solveMouthFrownRight(landmarks: List<FaceLandmark>): Float {
        val iod = LandmarkIndices.iod(landmarks)
        if (iod <= 0f) return 0f
        val delta = (landmarks[MOUTH_RIGHT].y - lipCenterY(landmarks)) / iod
        return remap(delta, FROWN_LOW, FROWN_HIGH)
    }

    /** Solve mouthStretchLeft: left lip vertical compression near corner (grimace). */
    fun solveMouthStretchLeft(landmarks: List<FaceLandmark>): Float {
        val iod = LandmarkIndices.iod(landmarks)
        if (iod <= 0f) return 0f
        val gap = distance2D(landmarks[LEFT_UPPER_OUTER_LIP], landmarks[LEFT_LOWER_OUTER_LIP])
        val compression = NEUTRAL_OUTER_LIP_GAP_IOD_RATIO - gap / iod
        return remap(compression, STRETCH_LOW, STRETCH_HIGH)
    }

    /** Solve mouthStretchRight: right lip vertical compression near corner (grimace). */
    fun solveMouthStretchRight(landmarks: List<FaceLandmark>): Float {
        val iod = LandmarkIndices.iod(landmarks)
        if (iod <= 0f) return 0f
        val gap = distance2D(landmarks[RIGHT_UPPER_OUTER_LIP], landmarks[RIGHT_LOWER_OUTER_LIP])
        val compression = NEUTRAL_OUTER_LIP_GAP_IOD_RATIO - gap / iod
        return remap(compression, STRETCH_LOW, STRETCH_HIGH)
    }

    /** Solve mouthPressLeft: left lip corner pressing together relative to center gap. */
    fun solveMouthPressLeft(landmarks: List<FaceLandmark>): Float {
        val iod = LandmarkIndices.iod(landmarks)
        if (iod <= 0f) return 0f
        val centerGap = distance2D(landmarks[UPPER_LIP_TOP], landmarks[LOWER_LIP_BOTTOM]) / iod
        val cornerGap = distance2D(landmarks[LEFT_UPPER_OUTER_LIP], landmarks[LEFT_LOWER_OUTER_LIP]) / iod
        // When pressing, corner gap shrinks more than center gap
        val delta = centerGap - cornerGap
        return remap(delta, PRESS_LOW, PRESS_HIGH)
    }

    /** Solve mouthPressRight: right lip corner pressing together relative to center gap. */
    fun solveMouthPressRight(landmarks: List<FaceLandmark>): Float {
        val iod = LandmarkIndices.iod(landmarks)
        if (iod <= 0f) return 0f
        val centerGap = distance2D(landmarks[UPPER_LIP_TOP], landmarks[LOWER_LIP_BOTTOM]) / iod
        val cornerGap = distance2D(landmarks[RIGHT_UPPER_OUTER_LIP], landmarks[RIGHT_LOWER_OUTER_LIP]) / iod
        val delta = centerGap - cornerGap
        return remap(delta, PRESS_LOW, PRESS_HIGH)
    }
}
