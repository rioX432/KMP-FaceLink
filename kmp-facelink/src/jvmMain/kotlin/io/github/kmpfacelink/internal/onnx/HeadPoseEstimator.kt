package io.github.kmpfacelink.internal.onnx

import io.github.kmpfacelink.model.HeadTransform
import kotlin.math.atan2
import kotlin.math.sqrt

/**
 * Estimates head pose (pitch, yaw, roll) from 66 facial landmarks
 * using a geometric approach based on key reference points.
 *
 * Uses the nose tip, jaw center, and eye corners to compute Euler angles
 * without requiring OpenCV's solvePnP. This is a lightweight approximation
 * sufficient for avatar animation.
 *
 * Landmark layout (OpenSeeFace 66-point):
 * - 0-16: Jaw contour
 * - 27-35: Nose
 * - 36-41: Right eye
 * - 42-47: Left eye
 */
internal object HeadPoseEstimator {

    /**
     * Estimate head pose from normalized landmark coordinates.
     *
     * @param landmarks Flat array of 66 (x, y) pairs in [0,1] normalized coords.
     * @return HeadTransform with estimated pitch, yaw, roll in degrees.
     */
    fun estimate(landmarks: FloatArray): HeadTransform {
        // Key reference points
        val noseTipX = landmarks[NOSE_TIP * 2]
        val noseTipY = landmarks[NOSE_TIP * 2 + 1]

        val chinX = landmarks[CHIN * 2]
        val chinY = landmarks[CHIN * 2 + 1]

        val noseBridgeX = landmarks[NOSE_BRIDGE * 2]
        val noseBridgeY = landmarks[NOSE_BRIDGE * 2 + 1]

        val rightEyeOuterX = landmarks[RIGHT_EYE_OUTER * 2]
        val rightEyeOuterY = landmarks[RIGHT_EYE_OUTER * 2 + 1]

        val leftEyeOuterX = landmarks[LEFT_EYE_OUTER * 2]
        val leftEyeOuterY = landmarks[LEFT_EYE_OUTER * 2 + 1]

        val jawLeftX = landmarks[JAW_LEFT * 2]
        val jawRightX = landmarks[JAW_RIGHT * 2]

        // Face width for normalization
        val faceWidth = dist(rightEyeOuterX, rightEyeOuterY, leftEyeOuterX, leftEyeOuterY)
        if (faceWidth < MIN_FACE_WIDTH) return HeadTransform()

        // Yaw: horizontal displacement of nose tip from face center
        val faceCenterX = (rightEyeOuterX + leftEyeOuterX) / 2f
        val yawRatio = (noseTipX - faceCenterX) / faceWidth
        val yaw = yawRatio * MAX_YAW_DEGREES

        // Pitch: vertical ratio of nose-bridge-to-chin vs nose-bridge-to-nose-tip
        val bridgeToChin = dist(noseBridgeX, noseBridgeY, chinX, chinY)
        val bridgeToNose = dist(noseBridgeX, noseBridgeY, noseTipX, noseTipY)
        val pitchRatio = if (bridgeToChin > 0f) bridgeToNose / bridgeToChin else 0.5f
        val pitch = (pitchRatio - NEUTRAL_PITCH_RATIO) * PITCH_SCALE

        // Roll: angle of the line connecting eye corners
        val rollRad = atan2(
            leftEyeOuterY - rightEyeOuterY,
            leftEyeOuterX - rightEyeOuterX,
        )
        val roll = Math.toDegrees(rollRad.toDouble()).toFloat()

        // Position: face center normalized, Z from face width (smaller = further)
        val posX = faceCenterX - 0.5f
        val posY = (rightEyeOuterY + leftEyeOuterY) / 2f - 0.5f
        val jawWidth = dist(jawLeftX, 0f, jawRightX, 0f)
        val posZ = if (jawWidth > 0f) REFERENCE_FACE_WIDTH / jawWidth - 1f else 0f

        return HeadTransform(
            pitch = pitch,
            yaw = yaw,
            roll = roll,
            positionX = posX,
            positionY = posY,
            positionZ = posZ,
        )
    }

    private fun dist(x1: Float, y1: Float, x2: Float, y2: Float): Float {
        val dx = x1 - x2
        val dy = y1 - y2
        return sqrt(dx * dx + dy * dy)
    }

    // OpenSeeFace 66-point landmark indices
    private const val NOSE_TIP = 30
    private const val CHIN = 8
    private const val NOSE_BRIDGE = 27
    private const val RIGHT_EYE_OUTER = 36
    private const val LEFT_EYE_OUTER = 45
    private const val JAW_LEFT = 0
    private const val JAW_RIGHT = 16

    private const val MIN_FACE_WIDTH = 0.01f
    private const val MAX_YAW_DEGREES = 90f
    private const val NEUTRAL_PITCH_RATIO = 0.45f
    private const val PITCH_SCALE = 180f
    private const val REFERENCE_FACE_WIDTH = 0.35f
}
