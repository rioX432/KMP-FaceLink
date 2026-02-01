package io.github.kmpfacelink.util

import io.github.kmpfacelink.model.FaceLandmark
import kotlin.math.sqrt

/**
 * Geometric utility functions for landmark-based blend shape calculations.
 */
internal object LandmarkGeometry {

    /**
     * Euclidean distance between two landmarks in 2D (x, y).
     * Uses only x/y to reduce jitter from noisy Z values.
     */
    fun distance2D(a: FaceLandmark, b: FaceLandmark): Float {
        val dx = a.x - b.x
        val dy = a.y - b.y
        return sqrt(dx * dx + dy * dy)
    }

    /**
     * Euclidean distance between two landmarks in 3D (x, y, z).
     */
    fun distance3D(a: FaceLandmark, b: FaceLandmark): Float {
        val dx = a.x - b.x
        val dy = a.y - b.y
        val dz = a.z - b.z
        return sqrt(dx * dx + dy * dy + dz * dz)
    }

    /**
     * Remap a value from [inMin, inMax] to [outMin, outMax], clamped to output range.
     */
    fun remap(
        value: Float,
        inMin: Float,
        inMax: Float,
        outMin: Float = 0f,
        outMax: Float = 1f,
    ): Float {
        if (inMax <= inMin) return outMin
        val t = ((value - inMin) / (inMax - inMin)).coerceIn(0f, 1f)
        return outMin + t * (outMax - outMin)
    }
}
