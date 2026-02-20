package io.github.kmpfacelink

import io.github.kmpfacelink.model.BlendShape
import io.github.kmpfacelink.model.emptyBlendShapeData
import io.github.kmpfacelink.util.GazeEstimator
import kotlin.test.Test
import kotlin.test.assertEquals

class GazeEstimatorTest {

    @Test
    fun neutralGazeWhenAllZero() {
        val blendShapes = emptyBlendShapeData()
        val gaze = GazeEstimator.estimate(blendShapes)
        assertEquals(0f, gaze.leftEyeYaw)
        assertEquals(0f, gaze.leftEyePitch)
        assertEquals(0f, gaze.rightEyeYaw)
        assertEquals(0f, gaze.rightEyePitch)
        assertEquals(0f, gaze.combinedYaw)
        assertEquals(0f, gaze.combinedPitch)
    }

    @Test
    fun lookingRightBothEyes() {
        val blendShapes = emptyBlendShapeData().toMutableMap().apply {
            // Left eye: lookIn = looking towards nose = looking right for left eye
            this[BlendShape.EYE_LOOK_IN_LEFT] = 1f
            // Right eye: lookOut = looking away from nose = looking right for right eye
            this[BlendShape.EYE_LOOK_OUT_RIGHT] = 1f
        }
        val gaze = GazeEstimator.estimate(blendShapes)
        assertEquals(30f, gaze.leftEyeYaw, 0.01f)
        assertEquals(30f, gaze.rightEyeYaw, 0.01f)
        assertEquals(30f, gaze.combinedYaw, 0.01f)
    }

    @Test
    fun lookingLeftBothEyes() {
        val blendShapes = emptyBlendShapeData().toMutableMap().apply {
            // Left eye: lookOut = looking away from nose = looking left for left eye
            this[BlendShape.EYE_LOOK_OUT_LEFT] = 1f
            // Right eye: lookIn = looking towards nose = looking left for right eye
            this[BlendShape.EYE_LOOK_IN_RIGHT] = 1f
        }
        val gaze = GazeEstimator.estimate(blendShapes)
        assertEquals(-30f, gaze.leftEyeYaw, 0.01f)
        assertEquals(-30f, gaze.rightEyeYaw, 0.01f)
        assertEquals(-30f, gaze.combinedYaw, 0.01f)
    }

    @Test
    fun lookingUpBothEyes() {
        val blendShapes = emptyBlendShapeData().toMutableMap().apply {
            this[BlendShape.EYE_LOOK_UP_LEFT] = 1f
            this[BlendShape.EYE_LOOK_UP_RIGHT] = 1f
        }
        val gaze = GazeEstimator.estimate(blendShapes)
        assertEquals(30f, gaze.leftEyePitch, 0.01f)
        assertEquals(30f, gaze.rightEyePitch, 0.01f)
        assertEquals(30f, gaze.combinedPitch, 0.01f)
    }

    @Test
    fun lookingDownBothEyes() {
        val blendShapes = emptyBlendShapeData().toMutableMap().apply {
            this[BlendShape.EYE_LOOK_DOWN_LEFT] = 1f
            this[BlendShape.EYE_LOOK_DOWN_RIGHT] = 1f
        }
        val gaze = GazeEstimator.estimate(blendShapes)
        assertEquals(-30f, gaze.leftEyePitch, 0.01f)
        assertEquals(-30f, gaze.rightEyePitch, 0.01f)
        assertEquals(-30f, gaze.combinedPitch, 0.01f)
    }

    @Test
    fun partialGazeProducesProportionalAngle() {
        val blendShapes = emptyBlendShapeData().toMutableMap().apply {
            this[BlendShape.EYE_LOOK_IN_LEFT] = 0.5f
        }
        val gaze = GazeEstimator.estimate(blendShapes)
        assertEquals(15f, gaze.leftEyeYaw, 0.01f)
    }

    @Test
    fun combinedGazeIsAverageOfBothEyes() {
        val blendShapes = emptyBlendShapeData().toMutableMap().apply {
            this[BlendShape.EYE_LOOK_IN_LEFT] = 1f // left eye yaw = +30
            // right eye yaw = 0 (no activation)
        }
        val gaze = GazeEstimator.estimate(blendShapes)
        assertEquals(15f, gaze.combinedYaw, 0.01f) // (30 + 0) / 2
    }
}
