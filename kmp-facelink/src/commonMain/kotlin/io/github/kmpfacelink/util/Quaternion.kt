package io.github.kmpfacelink.util

import kotlin.math.PI
import kotlin.math.acos
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Euler angles in degrees using the project's head-pose convention:
 * - `pitch` = rotation around the X axis (nod up/down)
 * - `yaw`   = rotation around the Y axis (turn left/right)
 * - `roll`  = rotation around the Z axis (tilt)
 *
 * Returned by [Quaternion.toEulerDegrees] to avoid the magic-index coupling
 * that a `FloatArray` return would impose on callers.
 */
internal data class EulerDegrees(val pitch: Float, val yaw: Float, val roll: Float)

/**
 * Unit quaternion `(w, x, y, z)` used for head-pose interpolation.
 *
 * Euler ↔ quaternion conversions assume the Z-Y-X intrinsic Tait-Bryan
 * convention: `R = Rz(roll) · Ry(yaw) · Rx(pitch)`, matching the
 * [io.github.kmpfacelink.model.HeadTransform] field naming. As long as the
 * same convention is used for both directions the round-trip is exact up to
 * float precision.
 */
internal data class Quaternion(val w: Float, val x: Float, val y: Float, val z: Float) {

    /** Dot product with [other]. */
    fun dot(other: Quaternion): Float =
        w * other.w + x * other.x + y * other.y + z * other.z

    /** Returns a unit-length copy. If the length is zero, returns the identity quaternion. */
    fun normalize(): Quaternion {
        val norm = sqrt(w * w + x * x + y * y + z * z)
        if (norm < NORMALISATION_EPSILON) return IDENTITY
        val inv = 1f / norm
        return Quaternion(w * inv, x * inv, y * inv, z * inv)
    }

    /**
     * Converts this unit quaternion back to Euler degrees using the inverse of
     * [fromEulerDegrees]. Near the ±90° yaw singularity the result stays
     * continuous — pitch and roll fold into a single value but never produce
     * NaN because `sin(yaw)` is clamped to [[-GIMBAL_LOCK_LIMIT], [GIMBAL_LOCK_LIMIT]].
     */
    fun toEulerDegrees(): EulerDegrees {
        val sinP = 2f * (w * x + y * z)
        val cosP = 1f - 2f * (x * x + y * y)
        val pitchRad = atan2(sinP, cosP)

        val sinY = (2f * (w * y - z * x)).coerceIn(-GIMBAL_LOCK_LIMIT, GIMBAL_LOCK_LIMIT)
        val yawRad = asin(sinY)

        val sinR = 2f * (w * z + x * y)
        val cosR = 1f - 2f * (y * y + z * z)
        val rollRad = atan2(sinR, cosR)

        return EulerDegrees(
            pitch = pitchRad * RAD_TO_DEG,
            yaw = yawRad * RAD_TO_DEG,
            roll = rollRad * RAD_TO_DEG,
        )
    }

    internal companion object {
        /** Neutral (no rotation) quaternion. */
        internal val IDENTITY: Quaternion = Quaternion(1f, 0f, 0f, 0f)

        private const val NORMALISATION_EPSILON = 1e-12f
        private const val DEG_TO_RAD = (PI / 180.0).toFloat()
        private const val RAD_TO_DEG = (180.0 / PI).toFloat()

        /**
         * Threshold on the cosine of the half-angle above which [slerp] falls
         * back to a normalized linear interpolation (nlerp). Above this the
         * two quaternions are nearly aligned and `acos` / `sin` become
         * numerically unstable. Matches the classic value from Shoemake's
         * 1985 SIGGRAPH paper on quaternion curves.
         */
        private const val SLERP_LERP_THRESHOLD = 0.9995f

        /**
         * Clamp applied to `sin(yaw)` when extracting Euler angles — prevents
         * `asin` from tipping into the gimbal-lock singularity at ±90°. The
         * value is `sin(yaw)` (full angle), not `sin(yaw/2)`.
         */
        private const val GIMBAL_LOCK_LIMIT = 0.9999f

        /**
         * Builds a quaternion from the project's `(pitch, yaw, roll)` degrees
         * convention using the Z-Y-X intrinsic ordering:
         * `R = Rz(roll) · Ry(yaw) · Rx(pitch)`.
         */
        internal fun fromEulerDegrees(pitch: Float, yaw: Float, roll: Float): Quaternion {
            val halfPitch = (pitch * DEG_TO_RAD) * 0.5f
            val halfYaw = (yaw * DEG_TO_RAD) * 0.5f
            val halfRoll = (roll * DEG_TO_RAD) * 0.5f

            val cx = cos(halfPitch)
            val sx = sin(halfPitch)
            val cy = cos(halfYaw)
            val sy = sin(halfYaw)
            val cz = cos(halfRoll)
            val sz = sin(halfRoll)

            // Z-Y-X intrinsic composition expanded.
            val w = cx * cy * cz + sx * sy * sz
            val x = sx * cy * cz - cx * sy * sz
            val y = cx * sy * cz + sx * cy * sz
            val z = cx * cy * sz - sx * sy * cz
            return Quaternion(w, x, y, z)
        }

        /**
         * Spherical linear interpolation between two unit quaternions.
         *
         * Accepts `t` outside `[0, 1]` for extrapolation — useful for
         * motion-aware frame interpolation where the caller wants to continue
         * the rotation along the same great-circle arc. The antipodal case is
         * handled so slerp always takes the shorter path.
         *
         * When `from` and `to` are nearly aligned the implementation falls
         * back to a normalized linear interpolation. That branch does not
         * preserve constant angular velocity across extrapolation, but for
         * the tiny rotations it targets the geometric error is below float
         * precision.
         */
        internal fun slerp(from: Quaternion, to: Quaternion, t: Float): Quaternion {
            var target = Quaternion(to.w, to.x, to.y, to.z)
            var cosHalfTheta = from.dot(target)

            // Take the shorter arc — a quaternion and its negation represent the same rotation.
            if (cosHalfTheta < 0f) {
                target = Quaternion(-target.w, -target.x, -target.y, -target.z)
                cosHalfTheta = -cosHalfTheta
            }

            // If the quaternions are nearly aligned the trigonometric path becomes unstable
            // — fall back to component-wise lerp + normalisation.
            if (cosHalfTheta > SLERP_LERP_THRESHOLD) {
                val lerped = Quaternion(
                    w = from.w + (target.w - from.w) * t,
                    x = from.x + (target.x - from.x) * t,
                    y = from.y + (target.y - from.y) * t,
                    z = from.z + (target.z - from.z) * t,
                )
                return lerped.normalize()
            }

            val halfTheta = acos(cosHalfTheta.coerceIn(-1f, 1f))
            val sinHalfTheta = sin(halfTheta)
            val ratioA = sin((1f - t) * halfTheta) / sinHalfTheta
            val ratioB = sin(t * halfTheta) / sinHalfTheta

            return Quaternion(
                w = from.w * ratioA + target.w * ratioB,
                x = from.x * ratioA + target.x * ratioB,
                y = from.y * ratioA + target.y * ratioB,
                z = from.z * ratioA + target.z * ratioB,
            ).normalize()
        }
    }
}
