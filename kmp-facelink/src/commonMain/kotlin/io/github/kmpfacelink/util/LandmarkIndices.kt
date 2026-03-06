package io.github.kmpfacelink.util

import io.github.kmpfacelink.model.FaceLandmark
import io.github.kmpfacelink.util.LandmarkGeometry.distance2D

/**
 * Shared MediaPipe Face Mesh 478 landmark indices and inter-ocular distance helper.
 *
 * Centralizes index constants so multiple solver objects can reference them
 * without duplicating magic numbers.
 */
internal object LandmarkIndices {

    // ── Inter-ocular distance reference ──
    const val RIGHT_EYE_INNER = 133
    const val LEFT_EYE_INNER = 362

    // ── Right eye ──
    const val RIGHT_EYE_OUTER = 33
    const val RIGHT_EYE_UPPER_1 = 160
    const val RIGHT_EYE_UPPER_2 = 159
    const val RIGHT_EYE_UPPER_3 = 158
    const val RIGHT_EYE_LOWER_1 = 144
    const val RIGHT_EYE_LOWER_2 = 145
    const val RIGHT_EYE_LOWER_3 = 153

    // ── Left eye ──
    const val LEFT_EYE_OUTER = 263
    const val LEFT_EYE_UPPER_1 = 387
    const val LEFT_EYE_UPPER_2 = 386
    const val LEFT_EYE_UPPER_3 = 385
    const val LEFT_EYE_LOWER_1 = 373
    const val LEFT_EYE_LOWER_2 = 374
    const val LEFT_EYE_LOWER_3 = 380

    // ── Nose ──
    const val NOSE_BRIDGE = 6
    const val NOSE_TIP = 1
    const val LEFT_NOSE_WING = 48
    const val RIGHT_NOSE_WING = 278

    // ── Jaw / chin ──
    const val CHIN = 152

    // ── Mouth corners ──
    const val MOUTH_LEFT = 61
    const val MOUTH_RIGHT = 291

    // ── Lip center reference ──
    const val UPPER_LIP_TOP = 13
    const val LOWER_LIP_BOTTOM = 14

    // ── Lip corner press / stretch ──
    const val LEFT_UPPER_OUTER_LIP = 185
    const val LEFT_LOWER_OUTER_LIP = 146
    const val RIGHT_UPPER_OUTER_LIP = 409
    const val RIGHT_LOWER_OUTER_LIP = 375

    // ── Cheeks ──
    const val LEFT_CHEEK = 234
    const val RIGHT_CHEEK = 454

    // ── Under-eye (for cheek squint) ──
    const val RIGHT_INFRAORBITAL = 50
    const val LEFT_INFRAORBITAL = 280

    // ── Brow ──
    const val RIGHT_BROW_INNER = 70
    const val RIGHT_BROW_MID = 105
    const val LEFT_BROW_INNER = 300
    const val LEFT_BROW_MID = 334

    /**
     * Inter-ocular distance: 2D distance between right and left inner eye corners.
     * Used as a face-size-independent normalization factor.
     */
    fun iod(landmarks: List<FaceLandmark>): Float =
        distance2D(landmarks[RIGHT_EYE_INNER], landmarks[LEFT_EYE_INNER])
}
