package io.github.kmpfacelink.capture

import kotlin.math.abs

/**
 * Frontality gate derived from MediaPipe's 4x4 facialTransformationMatrix (column-major).
 *
 * The face's local +X (right) axis is the first column (r00, r10, r20). Under a pure yaw of θ
 * about the camera's vertical axis it maps to (cosθ, 0, -sinθ), so the camera-space z component
 * r20 == -sinθ. |r20| is therefore a yaw magnitude proxy that needs no Euler-convention guess.
 */
public object HeadPose {
    private const val YAW_SIN_INDEX = 2 // column-major r20 (col 0, row 2): -sin(yaw)
    private const val PITCH_SIN_INDEX = 9 // column-major r12 (col 2, row 1): -sin(pitch)
    private const val DEFAULT_MAX_YAW_SIN = 0.42f // ~sin(25deg)

    private fun degenerate(transform: FloatArray?): Boolean =
        transform == null || transform.size < 16 ||
            (transform[0] == 0f && transform[5] == 0f && transform[10] == 0f)

    public fun yawSin(transform: FloatArray?): Float? =
        if (degenerate(transform)) null else transform!![YAW_SIN_INDEX]

    public fun pitchSin(transform: FloatArray?): Float? =
        if (degenerate(transform)) null else transform!![PITCH_SIN_INDEX]

    public fun isFrontal(transform: FloatArray?, maxYawSin: Float = DEFAULT_MAX_YAW_SIN): Boolean {
        val yaw = yawSin(transform) ?: return false
        return abs(yaw) <= maxYawSin
    }
}
