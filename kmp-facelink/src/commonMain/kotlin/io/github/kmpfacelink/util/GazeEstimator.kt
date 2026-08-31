package io.github.kmpfacelink.util

import io.github.kmpfacelink.model.BlendShape
import io.github.kmpfacelink.model.BlendShapeData
import io.github.kmpfacelink.model.GazeData

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

    /** The eight shapes this estimate is built from. */
    private val REQUIRED_SHAPES = setOf(
        BlendShape.EYE_LOOK_IN_LEFT,
        BlendShape.EYE_LOOK_OUT_LEFT,
        BlendShape.EYE_LOOK_UP_LEFT,
        BlendShape.EYE_LOOK_DOWN_LEFT,
        BlendShape.EYE_LOOK_IN_RIGHT,
        BlendShape.EYE_LOOK_OUT_RIGHT,
        BlendShape.EYE_LOOK_UP_RIGHT,
        BlendShape.EYE_LOOK_DOWN_RIGHT,
    )

    /**
     * Estimate gaze data from blend shapes.
     *
     * Returns null unless every shape in [REQUIRED_SHAPES] was measured. Reading an absent shape as
     * zero would make all eight cancel out and report a confident forward gaze, which is the one
     * answer an estimator that never looked at the eyes must not give.
     *
     * @param blendShapes Current frame blend shape values.
     * @return Estimated gaze direction for both eyes, or null when the inputs were not measured.
     */
    fun estimate(blendShapes: BlendShapeData): GazeData? {
        if (!blendShapes.keys.containsAll(REQUIRED_SHAPES)) return null
        // Left eye yaw: lookOut = looking left (negative yaw), lookIn = looking right (positive yaw)
        val leftEyeYaw = (
            blendShapes.getValue(BlendShape.EYE_LOOK_IN_LEFT) -
                blendShapes.getValue(BlendShape.EYE_LOOK_OUT_LEFT)
            ) * MAX_GAZE_ANGLE_DEGREES

        // Left eye pitch: lookUp = positive, lookDown = negative
        val leftEyePitch = (
            blendShapes.getValue(BlendShape.EYE_LOOK_UP_LEFT) -
                blendShapes.getValue(BlendShape.EYE_LOOK_DOWN_LEFT)
            ) * MAX_GAZE_ANGLE_DEGREES

        // Right eye yaw: lookIn = looking left (negative yaw), lookOut = looking right (positive yaw)
        val rightEyeYaw = (
            blendShapes.getValue(BlendShape.EYE_LOOK_OUT_RIGHT) -
                blendShapes.getValue(BlendShape.EYE_LOOK_IN_RIGHT)
            ) * MAX_GAZE_ANGLE_DEGREES

        // Right eye pitch: lookUp = positive, lookDown = negative
        val rightEyePitch = (
            blendShapes.getValue(BlendShape.EYE_LOOK_UP_RIGHT) -
                blendShapes.getValue(BlendShape.EYE_LOOK_DOWN_RIGHT)
            ) * MAX_GAZE_ANGLE_DEGREES

        return GazeData(
            leftEyeYaw = leftEyeYaw,
            leftEyePitch = leftEyePitch,
            rightEyeYaw = rightEyeYaw,
            rightEyePitch = rightEyePitch,
        )
    }
}
