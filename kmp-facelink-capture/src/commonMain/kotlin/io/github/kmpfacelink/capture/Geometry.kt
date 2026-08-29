package io.github.kmpfacelink.capture

import kotlin.math.sqrt

/**
 * Shared isotropy convention: normalized landmark coords are made isotropic by scaling y by
 * `aspect` (= actualAnalysisHeight / actualAnalysisWidth) before any Euclidean distance,
 * matching CheekPuffGeometricDetector's `aspect` argument. x is left untouched.
 */
internal object Geometry {

    /** Returns (x, y*aspect) for the landmark at [index] from an interleaved x,y,z array. */
    public fun point(landmarks: FloatArray, index: Int, aspect: Float): FloatArray {
        val o = index * 3
        return floatArrayOf(landmarks[o], landmarks[o + 1] * aspect)
    }

    public fun distance(a: FloatArray, b: FloatArray): Float {
        val dx = a[0] - b[0]
        val dy = a[1] - b[1]
        return sqrt(dx * dx + dy * dy)
    }
}
