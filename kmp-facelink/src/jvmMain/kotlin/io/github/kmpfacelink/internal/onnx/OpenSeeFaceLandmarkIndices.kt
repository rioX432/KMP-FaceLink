package io.github.kmpfacelink.internal.onnx

/**
 * OpenSeeFace 66-point landmark index constants.
 *
 * Based on a modified iBUG 68-point layout:
 * - 0-16: Jaw contour (17 points)
 * - 17-21: Right eyebrow (5 points)
 * - 22-26: Left eyebrow (5 points)
 * - 27-35: Nose (9 points)
 * - 36-41: Right eye (6 points)
 * - 42-47: Left eye (6 points)
 * - 48-59: Outer mouth (12 points)
 * - 60-65: Inner mouth (6 points)
 *
 * Note: OpenSeeFace has 66 points (not 68) — the two outer mouth corner
 * duplicates from iBUG are omitted.
 */
@Suppress("MagicNumber")
internal object OpenSeeFaceLandmarkIndices {
    // Jaw contour
    const val JAW_RIGHT = 0
    const val JAW_CENTER = 8
    const val JAW_LEFT = 16

    // Right eyebrow (from viewer's perspective, subject's right)
    const val RIGHT_BROW_INNER = 21
    const val RIGHT_BROW_OUTER = 17
    const val RIGHT_BROW_MID = 19

    // Left eyebrow
    const val LEFT_BROW_INNER = 22
    const val LEFT_BROW_OUTER = 26
    const val LEFT_BROW_MID = 24

    // Nose
    const val NOSE_BRIDGE_TOP = 27
    const val NOSE_TIP = 30
    const val NOSE_BOTTOM = 33
    const val NOSE_RIGHT_WING = 31
    const val NOSE_LEFT_WING = 35

    // Right eye
    const val RIGHT_EYE_OUTER = 36
    const val RIGHT_EYE_UPPER_1 = 37
    const val RIGHT_EYE_UPPER_2 = 38
    const val RIGHT_EYE_INNER = 39
    const val RIGHT_EYE_LOWER_2 = 40
    const val RIGHT_EYE_LOWER_1 = 41

    // Left eye
    const val LEFT_EYE_INNER = 42
    const val LEFT_EYE_UPPER_1 = 43
    const val LEFT_EYE_UPPER_2 = 44
    const val LEFT_EYE_OUTER = 45
    const val LEFT_EYE_LOWER_2 = 46
    const val LEFT_EYE_LOWER_1 = 47

    // Outer mouth
    const val MOUTH_RIGHT = 48
    const val MOUTH_UPPER_RIGHT = 49
    const val MOUTH_UPPER_CENTER_RIGHT = 50
    const val MOUTH_UPPER_CENTER = 51
    const val MOUTH_UPPER_CENTER_LEFT = 52
    const val MOUTH_UPPER_LEFT = 53
    const val MOUTH_LEFT = 54
    const val MOUTH_LOWER_LEFT = 55
    const val MOUTH_LOWER_CENTER_LEFT = 56
    const val MOUTH_LOWER_CENTER = 57
    const val MOUTH_LOWER_CENTER_RIGHT = 58
    const val MOUTH_LOWER_RIGHT = 59

    // Inner mouth
    const val INNER_MOUTH_RIGHT = 60
    const val INNER_MOUTH_UPPER = 61
    const val INNER_MOUTH_LEFT = 62
    const val INNER_MOUTH_LOWER_LEFT = 63
    const val INNER_MOUTH_LOWER = 64
    const val INNER_MOUTH_LOWER_RIGHT = 65
}
