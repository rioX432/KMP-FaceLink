package io.github.kmpfacelink.model

/**
 * 3D head transformation data from face tracking.
 *
 * Uses a right-handed coordinate system:
 * - Rotation: Euler angles in degrees (pitch, yaw, roll)
 * - Position: Translation in the camera's coordinate space
 *
 * @property pitch Rotation around X-axis (nodding up/down), in degrees
 * @property yaw Rotation around Y-axis (turning left/right), in degrees
 * @property roll Rotation around Z-axis (tilting head), in degrees
 * @property positionX Translation along X-axis (left/right)
 * @property positionY Translation along Y-axis (up/down)
 * @property positionZ Translation along Z-axis (forward/backward)
 * @property transformMatrix Raw 4×4 transformation matrix (column-major order, 16 floats).
 *           Null if the platform does not provide it.
 */
public data class HeadTransform(
    val pitch: Float = 0f,
    val yaw: Float = 0f,
    val roll: Float = 0f,
    val positionX: Float = 0f,
    val positionY: Float = 0f,
    val positionZ: Float = 0f,
    val transformMatrix: FloatArray? = null,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is HeadTransform) return false
        return pitch == other.pitch &&
            yaw == other.yaw &&
            roll == other.roll &&
            positionX == other.positionX &&
            positionY == other.positionY &&
            positionZ == other.positionZ &&
            transformMatrix.contentEquals(other.transformMatrix)
    }

    override fun hashCode(): Int {
        var result = pitch.hashCode()
        result = 31 * result + yaw.hashCode()
        result = 31 * result + roll.hashCode()
        result = 31 * result + positionX.hashCode()
        result = 31 * result + positionY.hashCode()
        result = 31 * result + positionZ.hashCode()
        result = 31 * result + (transformMatrix?.contentHashCode() ?: 0)
        return result
    }
}

private fun FloatArray?.contentEquals(other: FloatArray?): Boolean {
    if (this === other) return true
    if (this == null || other == null) return this == other
    return this.contentEquals(other)
}
