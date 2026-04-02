package io.github.kmpfacelink.internal.onnx

import kotlin.math.sqrt

/**
 * Geometric utility functions for computing blend shape values
 * from OpenSeeFace 66-point landmarks.
 *
 * Landmarks are stored as a flat FloatArray of (x, y) pairs:
 * landmarks[i*2] = x, landmarks[i*2+1] = y, both in [0,1] normalized coordinates.
 */
internal object BlendShapeGeometry {

    /** 2D distance between two landmark points. */
    fun dist(landmarks: FloatArray, a: Int, b: Int): Float {
        val dx = landmarks[a * 2] - landmarks[b * 2]
        val dy = landmarks[a * 2 + 1] - landmarks[b * 2 + 1]
        return sqrt(dx * dx + dy * dy)
    }

    /** X coordinate of a landmark. */
    fun x(landmarks: FloatArray, idx: Int): Float = landmarks[idx * 2]

    /** Y coordinate of a landmark. */
    fun y(landmarks: FloatArray, idx: Int): Float = landmarks[idx * 2 + 1]

    /**
     * Inter-ocular distance (IOD): distance between outer eye corners.
     * Used as a face-size-independent normalization factor.
     */
    fun iod(landmarks: FloatArray): Float =
        dist(landmarks, OpenSeeFaceLandmarkIndices.RIGHT_EYE_OUTER, OpenSeeFaceLandmarkIndices.LEFT_EYE_OUTER)

    /**
     * Eye Aspect Ratio (EAR) for blink detection.
     * EAR = (d(upper1,lower1) + d(upper2,lower2)) / (2 * d(outer,inner))
     *
     * Lower EAR = more closed eye.
     */
    fun eyeAspectRatio(
        landmarks: FloatArray,
        outer: Int,
        upper1: Int,
        upper2: Int,
        inner: Int,
        lower2: Int,
        lower1: Int,
    ): Float {
        val vertical1 = dist(landmarks, upper1, lower1)
        val vertical2 = dist(landmarks, upper2, lower2)
        val horizontal = dist(landmarks, outer, inner)
        return if (horizontal > 0f) (vertical1 + vertical2) / (2f * horizontal) else 0f
    }

    /**
     * Remap a value from [inMin, inMax] to [0, 1], clamped.
     */
    fun remap(value: Float, inMin: Float, inMax: Float): Float {
        if (inMax <= inMin) return 0f
        return ((value - inMin) / (inMax - inMin)).coerceIn(0f, 1f)
    }
}
