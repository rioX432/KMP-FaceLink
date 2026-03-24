package io.github.kmpfacelink.vmc

import kotlin.math.cos
import kotlin.math.sin

/**
 * Minimal quaternion representation for VMC bone rotation.
 *
 * @property x X component of the quaternion vector part
 * @property y Y component of the quaternion vector part
 * @property z Z component of the quaternion vector part
 * @property w Scalar (real) component of the quaternion
 */
internal data class Quaternion(
    val x: Float,
    val y: Float,
    val z: Float,
    val w: Float,
)

/**
 * Converts Euler angles (in degrees) to a quaternion.
 *
 * Uses Unity's rotation order: Y (yaw) * X (pitch) * Z (roll),
 * matching Unity's Quaternion.Euler behavior used by VMC Protocol.
 *
 * @param pitchDeg Rotation around X-axis in degrees
 * @param yawDeg Rotation around Y-axis in degrees
 * @param rollDeg Rotation around Z-axis in degrees
 */
internal fun eulerToQuaternion(pitchDeg: Float, yawDeg: Float, rollDeg: Float): Quaternion {
    val halfPitch = (pitchDeg * DEG_TO_RAD_HALF).toDouble()
    val halfYaw = (yawDeg * DEG_TO_RAD_HALF).toDouble()
    val halfRoll = (rollDeg * DEG_TO_RAD_HALF).toDouble()

    val cp = cos(halfPitch)
    val sp = sin(halfPitch)
    val cy = cos(halfYaw)
    val sy = sin(halfYaw)
    val cr = cos(halfRoll)
    val sr = sin(halfRoll)

    // Q = Qy * Qx * Qz (Unity's YXZ rotation order)
    return Quaternion(
        x = (cy * sp * cr + sy * cp * sr).toFloat(),
        y = (sy * cp * cr - cy * sp * sr).toFloat(),
        z = (cy * cp * sr - sy * sp * cr).toFloat(),
        w = (cy * cp * cr + sy * sp * sr).toFloat(),
    )
}

/**
 * Degrees to radians conversion factor, pre-halved for quaternion conversion.
 * = PI / 360
 */
private const val DEG_TO_RAD_HALF = 0.008726646259971648f
