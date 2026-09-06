package io.github.kmpfacelink.util

import io.github.kmpfacelink.model.FaceLandmark
import io.github.kmpfacelink.util.LandmarkGeometry.distance2D
import io.github.kmpfacelink.util.LandmarkGeometry.remap
import io.github.kmpfacelink.util.LandmarkIndices.CHIN
import io.github.kmpfacelink.util.LandmarkIndices.LEFT_CHEEK
import io.github.kmpfacelink.util.LandmarkIndices.LEFT_EYE_INNER
import io.github.kmpfacelink.util.LandmarkIndices.LEFT_EYE_LOWER_1
import io.github.kmpfacelink.util.LandmarkIndices.LEFT_EYE_LOWER_2
import io.github.kmpfacelink.util.LandmarkIndices.LEFT_EYE_LOWER_3
import io.github.kmpfacelink.util.LandmarkIndices.LEFT_EYE_OUTER
import io.github.kmpfacelink.util.LandmarkIndices.LEFT_EYE_UPPER_1
import io.github.kmpfacelink.util.LandmarkIndices.LEFT_EYE_UPPER_2
import io.github.kmpfacelink.util.LandmarkIndices.LEFT_EYE_UPPER_3
import io.github.kmpfacelink.util.LandmarkIndices.LEFT_NOSE_WING
import io.github.kmpfacelink.util.LandmarkIndices.MOUTH_LEFT
import io.github.kmpfacelink.util.LandmarkIndices.MOUTH_RIGHT
import io.github.kmpfacelink.util.LandmarkIndices.NOSE_BRIDGE
import io.github.kmpfacelink.util.LandmarkIndices.NOSE_TIP
import io.github.kmpfacelink.util.LandmarkIndices.RIGHT_CHEEK
import io.github.kmpfacelink.util.LandmarkIndices.RIGHT_EYE_INNER
import io.github.kmpfacelink.util.LandmarkIndices.RIGHT_EYE_LOWER_1
import io.github.kmpfacelink.util.LandmarkIndices.RIGHT_EYE_LOWER_2
import io.github.kmpfacelink.util.LandmarkIndices.RIGHT_EYE_LOWER_3
import io.github.kmpfacelink.util.LandmarkIndices.RIGHT_EYE_OUTER
import io.github.kmpfacelink.util.LandmarkIndices.RIGHT_EYE_UPPER_1
import io.github.kmpfacelink.util.LandmarkIndices.RIGHT_EYE_UPPER_2
import io.github.kmpfacelink.util.LandmarkIndices.RIGHT_EYE_UPPER_3
import io.github.kmpfacelink.util.LandmarkIndices.RIGHT_NOSE_WING

/**
 * Geometric solvers that compute blend shape values from MediaPipe 478 face landmarks.
 *
 * Each solver takes the full landmark list and returns a value in [0, 1].
 * Landmarks use MediaPipe normalized coordinates: x,y in [0,1], z is relative depth.
 */
// Each public function maps 1:1 to an ARKit blend shape (11 solvers for 11 shapes).
// All solvers share common helpers (iod, EAR) and thresholds — splitting would duplicate state.
@Suppress("TooManyFunctions")
internal object LandmarkSolvers {

    // ── Tuning thresholds ──
    // All ratio values are IOD-normalized unless stated otherwise.
    // "LOW" is the value at which the blend shape starts activating (output = 0).
    // "HIGH" is the value at which the blend shape reaches full activation (output = 1).
    // To tune: log the raw ratio for a target expression on a real device, then set
    // LOW just below the neutral-to-active transition and HIGH at comfortable full activation.

    /**
     * Eye Aspect Ratio (EAR) thresholds for wide-eye detection.
     *
     * EAR = (sum of 3 vertical lid distances) / (3 * horizontal width).
     * Typical values: neutral/resting ~0.35–0.50, comfortably open ~0.50–0.55, wide open > 0.55.
     *
     * Tuning: increase [EAR_WIDE_LOW] if the solver activates during normal blinking recovery;
     * decrease [EAR_WIDE_HIGH] if full-wide expression never reaches 1.0 on the target device.
     */
    private const val EAR_WIDE_LOW = 0.55f
    private const val EAR_WIDE_HIGH = 0.80f

    /**
     * Nose sneer thresholds: vertical lift of the nose wing relative to the nose tip, IOD-normalized.
     *
     * Raw ratio = (tipY - wingY) / IOD. Positive when the wing is above the tip (sneering).
     * Neutral baseline is approximately 0.10–0.15; an active sneer pushes the ratio above 0.15.
     *
     * Tuning: raise [SNEER_LOW] if subtle head tilts trigger false positives;
     * raise [SNEER_HIGH] if the solver saturates before a full sneer is reached.
     */
    private const val SNEER_LOW = 0.15f
    private const val SNEER_HIGH = 0.28f

    /**
     * Jaw forward thresholds: chin Z-offset relative to nose bridge, IOD-normalized.
     *
     * Raw ratio = (bridgeZ - chinZ) / IOD. Increases as the chin protrudes toward the camera.
     * Z coordinates in MediaPipe are relative depth (more negative = closer to camera).
     * Neutral is near 0; a perceptible forward thrust reaches ~0.04–0.06.
     *
     * Tuning: Z depth from MediaPipe can be noisy — if jitter causes false activation,
     * raise [JAW_FWD_LOW] slightly. If the gesture feels unresponsive, lower [JAW_FWD_HIGH].
     */
    private const val JAW_FWD_LOW = 0.0f
    private const val JAW_FWD_HIGH = 0.06f

    /**
     * Jaw lateral thresholds: chin X-offset relative to nose bridge, IOD-normalized.
     *
     * Raw ratio = |chinX - bridgeX| / IOD. A small baseline shift (~0.01) accounts for
     * natural asymmetry; a clearly visible lateral shift reaches ~0.06–0.08.
     *
     * Tuning: raise [JAW_LAT_LOW] to suppress jitter or natural jaw asymmetry;
     * lower [JAW_LAT_HIGH] if the user cannot comfortably reach full activation.
     */
    private const val JAW_LAT_LOW = 0.01f
    private const val JAW_LAT_HIGH = 0.08f

    /**
     * Cheek puff thresholds: expansion of cheek-to-cheek width above the neutral IOD ratio.
     *
     * Raw expansion = (cheekWidth / IOD) - [NEUTRAL_CHEEK_IOD_RATIO].
     * At rest this expansion is ~0; puffing visibly expands the cheeks by ~0.06–0.10 IOD units.
     *
     * Tuning: adjust [NEUTRAL_CHEEK_IOD_RATIO] first if the solver has a non-zero output at rest;
     * then widen [PUFF_HIGH] if full puff never saturates, or narrow it if it saturates too early.
     */
    private const val PUFF_LOW = 0.0f
    private const val PUFF_HIGH = 0.08f

    /**
     * Mouth dimple thresholds: lateral pull of one mouth corner above the neutral half-width ratio.
     *
     * Raw pull = (halfWidth / IOD) - [NEUTRAL_MOUTH_HALF_IOD_RATIO], where halfWidth is the
     * distance from the face midline to one mouth corner. A natural smile pulls each corner
     * outward by ~0.02–0.06 IOD units beyond the neutral position.
     *
     * Tuning: if normal speech triggers the solver, raise [DIMPLE_LOW];
     * if a big smile never fully activates it, raise [DIMPLE_HIGH].
     */
    private const val DIMPLE_LOW = 0.005f
    private const val DIMPLE_HIGH = 0.06f

    // Number of vertical lid distance samples used in EAR calculation (upper1/2/3 vs lower1/2/3).
    private const val EAR_SAMPLE_COUNT = 3f

    /** Minimum landmark count required for geometric solving (468 base mesh, without iris). */
    const val MIN_LANDMARKS = 468

    // ── Helper ──

    private fun iod(landmarks: List<FaceLandmark>): Float =
        LandmarkIndices.iod(landmarks)

    // ── Solvers ──

    /**
     * Eye Aspect Ratio for the right eye.
     * Higher values = more open eye. Used to detect "eye wide" expression.
     */
    private fun rightEyeAspectRatio(lm: List<FaceLandmark>): Float {
        val v1 = distance2D(lm[RIGHT_EYE_UPPER_1], lm[RIGHT_EYE_LOWER_1])
        val v2 = distance2D(lm[RIGHT_EYE_UPPER_2], lm[RIGHT_EYE_LOWER_2])
        val v3 = distance2D(lm[RIGHT_EYE_UPPER_3], lm[RIGHT_EYE_LOWER_3])
        val h = distance2D(lm[RIGHT_EYE_OUTER], lm[RIGHT_EYE_INNER])
        return if (h > 0f) (v1 + v2 + v3) / (EAR_SAMPLE_COUNT * h) else 0f
    }

    /**
     * Eye Aspect Ratio for the left eye.
     */
    private fun leftEyeAspectRatio(lm: List<FaceLandmark>): Float {
        val v1 = distance2D(lm[LEFT_EYE_UPPER_1], lm[LEFT_EYE_LOWER_1])
        val v2 = distance2D(lm[LEFT_EYE_UPPER_2], lm[LEFT_EYE_LOWER_2])
        val v3 = distance2D(lm[LEFT_EYE_UPPER_3], lm[LEFT_EYE_LOWER_3])
        val h = distance2D(lm[LEFT_EYE_OUTER], lm[LEFT_EYE_INNER])
        return if (h > 0f) (v1 + v2 + v3) / (EAR_SAMPLE_COUNT * h) else 0f
    }

    /** Solve eyeWideRight from landmarks. */
    fun solveEyeWideRight(landmarks: List<FaceLandmark>): Float {
        val ear = rightEyeAspectRatio(landmarks)
        return remap(ear, EAR_WIDE_LOW, EAR_WIDE_HIGH)
    }

    /** Solve eyeWideLeft from landmarks. */
    fun solveEyeWideLeft(landmarks: List<FaceLandmark>): Float {
        val ear = leftEyeAspectRatio(landmarks)
        return remap(ear, EAR_WIDE_LOW, EAR_WIDE_HIGH)
    }

    /** Solve noseSneerLeft: vertical lift of left nose wing relative to nose tip. */
    fun solveNoseSneerLeft(landmarks: List<FaceLandmark>): Float {
        val iod = iod(landmarks)
        if (iod <= 0f) return 0f
        // When sneering, nose wing moves upward (Y decreases in image coords)
        val wingY = landmarks[LEFT_NOSE_WING].y
        val tipY = landmarks[NOSE_TIP].y
        // Positive delta = wing is above tip (sneering)
        val delta = (tipY - wingY) / iod
        return remap(delta, SNEER_LOW, SNEER_HIGH)
    }

    /** Solve noseSneerRight: vertical lift of right nose wing relative to nose tip. */
    fun solveNoseSneerRight(landmarks: List<FaceLandmark>): Float {
        val iod = iod(landmarks)
        if (iod <= 0f) return 0f
        val wingY = landmarks[RIGHT_NOSE_WING].y
        val tipY = landmarks[NOSE_TIP].y
        val delta = (tipY - wingY) / iod
        return remap(delta, SNEER_LOW, SNEER_HIGH)
    }

    /**
     * Solve jawForward: chin pushed forward (depth offset from the nose bridge).
     *
     * Returns null when the landmarks carry no depth. This movement is along the camera axis, so
     * without depth there is no signal to read and a returned number would be about nothing.
     */
    fun solveJawForward(landmarks: List<FaceLandmark>): Float? {
        val iod = iod(landmarks)
        if (iod <= 0f) return 0f
        val chinZ = landmarks[CHIN].relativeDepth ?: return null
        val bridgeZ = landmarks[NOSE_BRIDGE].relativeDepth ?: return null
        // When jaw is forward, chin Z approaches (or exceeds) bridge Z
        // More negative Z = closer to camera
        val delta = (bridgeZ - chinZ) / iod
        return remap(delta, JAW_FWD_LOW, JAW_FWD_HIGH)
    }

    /** Solve jawLeft: chin shifted to the left (negative X offset). */
    fun solveJawLeft(landmarks: List<FaceLandmark>): Float {
        val iod = iod(landmarks)
        if (iod <= 0f) return 0f
        val chinX = landmarks[CHIN].x
        val bridgeX = landmarks[NOSE_BRIDGE].x
        // Positive when chin is to the left of bridge (lower X = more left in image)
        val delta = (bridgeX - chinX) / iod
        return remap(delta, JAW_LAT_LOW, JAW_LAT_HIGH)
    }

    /** Solve jawRight: chin shifted to the right (positive X offset). */
    fun solveJawRight(landmarks: List<FaceLandmark>): Float {
        val iod = iod(landmarks)
        if (iod <= 0f) return 0f
        val chinX = landmarks[CHIN].x
        val bridgeX = landmarks[NOSE_BRIDGE].x
        val delta = (chinX - bridgeX) / iod
        return remap(delta, JAW_LAT_LOW, JAW_LAT_HIGH)
    }

    /** Solve cheekPuff: face width expansion at cheek level relative to IOD. */
    fun solveCheekPuff(landmarks: List<FaceLandmark>): Float {
        val iod = iod(landmarks)
        if (iod <= 0f) return 0f
        // Measure face width at cheek level
        val cheekWidth = distance2D(landmarks[LEFT_CHEEK], landmarks[RIGHT_CHEEK])
        // Neutral cheek-to-IOD ratio is approximately 3.0-3.2 for most faces.
        // When puffing, this ratio increases.
        val ratio = cheekWidth / iod
        val expansion = ratio - NEUTRAL_CHEEK_IOD_RATIO
        return remap(expansion, PUFF_LOW, PUFF_HIGH)
    }

    /** Solve mouthDimpleLeft: left mouth corner pulled laterally outward. */
    fun solveMouthDimpleLeft(landmarks: List<FaceLandmark>): Float {
        val iod = iod(landmarks)
        if (iod <= 0f) return 0f
        val centerX = landmarks[NOSE_BRIDGE].x
        val halfWidth = centerX - landmarks[MOUTH_LEFT].x
        val ratio = halfWidth / iod
        val pull = ratio - NEUTRAL_MOUTH_HALF_IOD_RATIO
        return remap(pull, DIMPLE_LOW, DIMPLE_HIGH)
    }

    /** Solve mouthDimpleRight: right mouth corner pulled laterally outward. */
    fun solveMouthDimpleRight(landmarks: List<FaceLandmark>): Float {
        val iod = iod(landmarks)
        if (iod <= 0f) return 0f
        val centerX = landmarks[NOSE_BRIDGE].x
        val halfWidth = landmarks[MOUTH_RIGHT].x - centerX
        val ratio = halfWidth / iod
        val pull = ratio - NEUTRAL_MOUTH_HALF_IOD_RATIO
        return remap(pull, DIMPLE_LOW, DIMPLE_HIGH)
    }

    // ── Neutral ratio baselines ──

    /**
     * Neutral cheek-to-IOD width ratio for a relaxed, forward-facing face.
     *
     * Measured as (left-cheek to right-cheek distance) / IOD.
     * Empirically ~3.0–3.2 across a range of face geometries; 3.15 is the default.
     * If [solveCheekPuff] returns a non-zero value when the face is relaxed, adjust this
     * constant to match the actual resting ratio observed on the target device/person.
     */
    private const val NEUTRAL_CHEEK_IOD_RATIO = 3.15f

    /**
     * Neutral mouth half-width-to-IOD ratio for a relaxed, closed mouth.
     *
     * Measured as (nose-bridge X − mouth corner X) / IOD for one side.
     * Empirically ~0.37–0.41; 0.39 is the default.
     * If [solveMouthDimpleLeft] or [solveMouthDimpleRight] activates during normal speech,
     * raise this value to match the observed resting half-width ratio.
     */
    private const val NEUTRAL_MOUTH_HALF_IOD_RATIO = 0.39f
}
