package io.github.kmpfacelink.util

import io.github.kmpfacelink.model.HeadTransform
import kotlin.math.atan2
import kotlin.math.sqrt

/**
 * Utility to extract Euler angles from a 4×4 transformation matrix.
 *
 * Expects a column-major 16-element float array (standard OpenGL / ARKit / MediaPipe convention).
 * Returns pitch, yaw, roll in degrees.
 */
internal object TransformUtils {

    private const val RAD_TO_DEG = 180f / kotlin.math.PI.toFloat()

    /**
     * Extract [HeadTransform] from a column-major 4×4 matrix.
     *
     * Column-major layout:
     * [m0  m4  m8  m12]
     * [m1  m5  m9  m13]
     * [m2  m6  m10 m14]
     * [m3  m7  m11 m15]
     */
    fun fromMatrix(matrix: FloatArray): HeadTransform {
        require(matrix.size == 16) { "Matrix must have 16 elements, got ${matrix.size}" }

        // Rotation matrix elements (column-major)
        val r00 = matrix[0]
        val r10 = matrix[1]
        val r20 = matrix[2]
        val r11 = matrix[5]
        val r12 = matrix[9]
        val r21 = matrix[6]
        val r22 = matrix[10]

        // Euler angle extraction (ZYX convention)
        val sy = sqrt(r00 * r00 + r10 * r10)
        val singular = sy < 1e-6f

        val pitch: Float
        val yaw: Float
        val roll: Float

        if (!singular) {
            pitch = atan2(r21, r22) * RAD_TO_DEG
            yaw = atan2(-r20, sy) * RAD_TO_DEG
            roll = atan2(r10, r00) * RAD_TO_DEG
        } else {
            pitch = atan2(-r12, r11) * RAD_TO_DEG
            yaw = atan2(-r20, sy) * RAD_TO_DEG
            roll = 0f
        }

        // Translation
        val posX = matrix[12]
        val posY = matrix[13]
        val posZ = matrix[14]

        return HeadTransform(
            pitch = pitch,
            yaw = yaw,
            roll = roll,
            positionX = posX,
            positionY = posY,
            positionZ = posZ,
            transformMatrix = matrix.copyOf(),
        )
    }
}
