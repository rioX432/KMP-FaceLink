package io.github.kmpfacelink.model

/**
 * Eye gaze direction data derived from blend shapes.
 *
 * Gaze angles are in degrees relative to the head's forward direction:
 * - **yaw**: Horizontal gaze angle. Positive = looking right, negative = looking left.
 * - **pitch**: Vertical gaze angle. Positive = looking up, negative = looking down.
 *
 * @property leftEyeYaw Left eye horizontal gaze angle in degrees.
 * @property leftEyePitch Left eye vertical gaze angle in degrees.
 * @property rightEyeYaw Right eye horizontal gaze angle in degrees.
 * @property rightEyePitch Right eye vertical gaze angle in degrees.
 * @property combinedYaw Average horizontal gaze angle (both eyes).
 * @property combinedPitch Average vertical gaze angle (both eyes).
 */
public data class GazeData(
    val leftEyeYaw: Float = 0f,
    val leftEyePitch: Float = 0f,
    val rightEyeYaw: Float = 0f,
    val rightEyePitch: Float = 0f,
) {
    /** Average horizontal gaze angle (both eyes). */
    val combinedYaw: Float get() = (leftEyeYaw + rightEyeYaw) / 2f

    /** Average vertical gaze angle (both eyes). */
    val combinedPitch: Float get() = (leftEyePitch + rightEyePitch) / 2f
}
