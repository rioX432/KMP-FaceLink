package io.github.kmpfacelink.util

import io.github.kmpfacelink.model.FaceLandmark
import io.github.kmpfacelink.util.LandmarkGeometry.distance2D
import io.github.kmpfacelink.util.LandmarkGeometry.remap

/**
 * Geometric solvers that compute blend shape values from MediaPipe 478 face landmarks.
 *
 * Each solver takes the full landmark list and returns a value in [0, 1].
 * Landmarks use MediaPipe normalized coordinates: x,y in [0,1], z is relative depth.
 */
@Suppress("TooManyFunctions")
internal object LandmarkSolvers {

    // ── Landmark indices (MediaPipe Face Mesh 478) ──

    // Inter-ocular distance reference
    private const val RIGHT_EYE_INNER = 133
    private const val LEFT_EYE_INNER = 362

    // Right eye
    private const val RIGHT_EYE_OUTER = 33
    private const val RIGHT_EYE_UPPER_1 = 160
    private const val RIGHT_EYE_UPPER_2 = 159
    private const val RIGHT_EYE_UPPER_3 = 158
    private const val RIGHT_EYE_LOWER_1 = 144
    private const val RIGHT_EYE_LOWER_2 = 145
    private const val RIGHT_EYE_LOWER_3 = 153

    // Left eye
    private const val LEFT_EYE_OUTER = 263
    private const val LEFT_EYE_UPPER_1 = 387
    private const val LEFT_EYE_UPPER_2 = 386
    private const val LEFT_EYE_UPPER_3 = 385
    private const val LEFT_EYE_LOWER_1 = 373
    private const val LEFT_EYE_LOWER_2 = 374
    private const val LEFT_EYE_LOWER_3 = 380

    // Nose
    private const val NOSE_BRIDGE = 6
    private const val NOSE_TIP = 1
    private const val LEFT_NOSE_WING = 48
    private const val RIGHT_NOSE_WING = 278

    // Jaw / chin
    private const val CHIN = 152

    // Mouth
    private const val MOUTH_LEFT = 61
    private const val MOUTH_RIGHT = 291

    // Cheeks
    private const val LEFT_CHEEK = 234
    private const val RIGHT_CHEEK = 454

    // ── Tuning thresholds ──
    // These are initial estimates based on MediaPipe normalized coordinates.
    // Tune with real device data.

    // Eye Aspect Ratio thresholds for "wide" detection.
    // Normal open EAR (3-point avg) is ~0.35-0.55; wide open > 0.55.
    private const val EAR_WIDE_LOW = 0.55f
    private const val EAR_WIDE_HIGH = 0.80f

    // Nose sneer: (tipY - wingY) / IOD ratio.
    // Neutral baseline is ~0.10-0.15; active sneer pushes above 0.15.
    private const val SNEER_LOW = 0.15f
    private const val SNEER_HIGH = 0.28f

    // Jaw forward: Z offset ratio thresholds
    private const val JAW_FWD_LOW = 0.0f
    private const val JAW_FWD_HIGH = 0.06f

    // Jaw lateral: X offset ratio thresholds
    private const val JAW_LAT_LOW = 0.01f
    private const val JAW_LAT_HIGH = 0.08f

    // Cheek puff: cheek-width-to-IOD expansion above neutral baseline.
    private const val PUFF_LOW = 0.0f
    private const val PUFF_HIGH = 0.08f

    // Mouth dimple: per-side half-width-to-IOD expansion above neutral baseline.
    private const val DIMPLE_LOW = 0.005f
    private const val DIMPLE_HIGH = 0.06f

    // Number of vertical lid distance samples for EAR calculation
    private const val EAR_SAMPLE_COUNT = 3f

    /** Minimum landmark count required for geometric solving (468 base mesh, without iris). */
    const val MIN_LANDMARKS = 468

    // ── Helper ──

    private fun iod(landmarks: List<FaceLandmark>): Float =
        distance2D(landmarks[RIGHT_EYE_INNER], landmarks[LEFT_EYE_INNER])

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

    /** Solve jawForward: chin pushed forward (Z-axis offset from nose bridge). */
    fun solveJawForward(landmarks: List<FaceLandmark>): Float {
        val iod = iod(landmarks)
        if (iod <= 0f) return 0f
        val chinZ = landmarks[CHIN].z
        val bridgeZ = landmarks[NOSE_BRIDGE].z
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
    // Approximate ratios for a neutral expression. Tuned via empirical testing.
    private const val NEUTRAL_CHEEK_IOD_RATIO = 3.15f
    private const val NEUTRAL_MOUTH_HALF_IOD_RATIO = 0.39f
}
