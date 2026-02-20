package io.github.kmpfacelink.util

import io.github.kmpfacelink.model.BlendShape
import io.github.kmpfacelink.model.BlendShapeData
import io.github.kmpfacelink.model.GazeData
import io.github.kmpfacelink.model.valueOf

/**
 * Estimates eye gaze direction from ARKit blend shape values.
 *
 * Converts the 8 eye look blend shapes (eyeLookIn/Out/Up/Down for each eye)
 * into yaw and pitch angles in degrees.
 *
 * ARKit blend shape semantics (from the user's perspective):
 * - eyeLookInLeft: left eye looking towards the nose (medial)
 * - eyeLookOutLeft: left eye looking away from the nose (lateral)
 * - eyeLookInRight: right eye looking towards the nose (medial)
 * - eyeLookOutRight: right eye looking away from the nose (lateral)
 *
 * Gaze output convention (right-handed, user-facing):
 * - Positive yaw = looking right
 * - Positive pitch = looking up
 */
internal object GazeEstimator {

    /**
     * Maximum gaze angle in degrees when a blend shape is fully activated (1.0).
     * ARKit eye look blend shapes typically saturate around 30-35 degrees.
     */
    private const val MAX_GAZE_ANGLE_DEGREES = 30f

    /**
     * Estimate gaze data from blend shapes.
     *
     * @param blendShapes Current frame blend shape values.
     * @return Estimated gaze direction for both eyes.
     */
    fun estimate(blendShapes: BlendShapeData): GazeData {
        // Left eye yaw: lookOut = looking left (negative yaw), lookIn = looking right (positive yaw)
        val leftEyeYaw = (
            blendShapes.valueOf(BlendShape.EYE_LOOK_IN_LEFT) -
                blendShapes.valueOf(BlendShape.EYE_LOOK_OUT_LEFT)
            ) * MAX_GAZE_ANGLE_DEGREES

        // Left eye pitch: lookUp = positive, lookDown = negative
        val leftEyePitch = (
            blendShapes.valueOf(BlendShape.EYE_LOOK_UP_LEFT) -
                blendShapes.valueOf(BlendShape.EYE_LOOK_DOWN_LEFT)
            ) * MAX_GAZE_ANGLE_DEGREES

        // Right eye yaw: lookIn = looking left (negative yaw), lookOut = looking right (positive yaw)
        val rightEyeYaw = (
            blendShapes.valueOf(BlendShape.EYE_LOOK_OUT_RIGHT) -
                blendShapes.valueOf(BlendShape.EYE_LOOK_IN_RIGHT)
            ) * MAX_GAZE_ANGLE_DEGREES

        // Right eye pitch: lookUp = positive, lookDown = negative
        val rightEyePitch = (
            blendShapes.valueOf(BlendShape.EYE_LOOK_UP_RIGHT) -
                blendShapes.valueOf(BlendShape.EYE_LOOK_DOWN_RIGHT)
            ) * MAX_GAZE_ANGLE_DEGREES

        return GazeData(
            leftEyeYaw = leftEyeYaw,
            leftEyePitch = leftEyePitch,
            rightEyeYaw = rightEyeYaw,
            rightEyePitch = rightEyePitch,
        )
    }
}
