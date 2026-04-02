package io.github.kmpfacelink.internal.onnx

import io.github.kmpfacelink.internal.onnx.BlendShapeGeometry.dist
import io.github.kmpfacelink.internal.onnx.BlendShapeGeometry.iod
import io.github.kmpfacelink.internal.onnx.BlendShapeGeometry.remap
import io.github.kmpfacelink.internal.onnx.BlendShapeGeometry.y
import io.github.kmpfacelink.internal.onnx.OpenSeeFaceLandmarkIndices as LM
import io.github.kmpfacelink.model.BlendShape

/**
 * Solves mouth and jaw ARKit blend shapes from OpenSeeFace 66-point landmarks.
 *
 * Handles: jawOpen, mouthSmile, mouthFrown, mouthPucker, mouthFunnel,
 * mouthClose, mouthLeft, mouthRight, mouthUpperUp, mouthLowerDown,
 * mouthStretch, mouthPress, mouthShrugUpper, mouthShrugLower,
 * mouthRollUpper, mouthRollLower, mouthDimple.
 */
@Suppress("TooManyFunctions")
internal object MouthBlendShapeSolver {

    // Jaw open: vertical distance between inner lip top and bottom
    private const val JAW_OPEN_LOW = 0.0f
    private const val JAW_OPEN_HIGH = 0.15f

    // Smile: mouth corner vertical uplift
    private const val SMILE_LOW = 0.0f
    private const val SMILE_HIGH = 0.06f

    // Frown: mouth corner downward displacement
    private const val FROWN_LOW = 0.0f
    private const val FROWN_HIGH = 0.04f

    // Pucker: mouth width narrowing
    private const val PUCKER_NEUTRAL_WIDTH = 0.20f
    private const val PUCKER_LOW = 0.0f
    private const val PUCKER_HIGH = 0.10f

    // Funnel: similar to pucker but with jaw open
    private const val FUNNEL_LOW = 0.0f
    private const val FUNNEL_HIGH = 0.08f

    fun solve(landmarks: FloatArray, output: MutableMap<BlendShape, Float>) {
        val faceIod = iod(landmarks)
        if (faceIod <= 0f) return

        solveJawOpen(landmarks, faceIod, output)
        solveSmile(landmarks, faceIod, output)
        solveFrown(landmarks, faceIod, output)
        solvePucker(landmarks, faceIod, output)
        solveFunnel(landmarks, faceIod, output)
        solveMouthSides(landmarks, faceIod, output)
        solveMouthVertical(landmarks, faceIod, output)
        setApproximatedShapes(output)
    }

    private fun solveJawOpen(landmarks: FloatArray, faceIod: Float, output: MutableMap<BlendShape, Float>) {
        val innerMouthHeight = dist(landmarks, LM.INNER_MOUTH_UPPER, LM.INNER_MOUTH_LOWER) / faceIod
        output[BlendShape.JAW_OPEN] = remap(innerMouthHeight, JAW_OPEN_LOW, JAW_OPEN_HIGH)
        // Mouth close: lips together while jaw is open (opposite of jaw open for simplified model)
        output[BlendShape.MOUTH_CLOSE] = 0f
    }

    private fun solveSmile(landmarks: FloatArray, faceIod: Float, output: MutableMap<BlendShape, Float>) {
        val lipCenterY = (y(landmarks, LM.MOUTH_UPPER_CENTER) + y(landmarks, LM.MOUTH_LOWER_CENTER)) / 2f
        val rightDelta = (lipCenterY - y(landmarks, LM.MOUTH_RIGHT)) / faceIod
        val leftDelta = (lipCenterY - y(landmarks, LM.MOUTH_LEFT)) / faceIod
        output[BlendShape.MOUTH_SMILE_RIGHT] = remap(rightDelta, SMILE_LOW, SMILE_HIGH)
        output[BlendShape.MOUTH_SMILE_LEFT] = remap(leftDelta, SMILE_LOW, SMILE_HIGH)
    }

    private fun solveFrown(landmarks: FloatArray, faceIod: Float, output: MutableMap<BlendShape, Float>) {
        val lipCenterY = (y(landmarks, LM.MOUTH_UPPER_CENTER) + y(landmarks, LM.MOUTH_LOWER_CENTER)) / 2f
        val rightDelta = (y(landmarks, LM.MOUTH_RIGHT) - lipCenterY) / faceIod
        val leftDelta = (y(landmarks, LM.MOUTH_LEFT) - lipCenterY) / faceIod
        output[BlendShape.MOUTH_FROWN_RIGHT] = remap(rightDelta, FROWN_LOW, FROWN_HIGH)
        output[BlendShape.MOUTH_FROWN_LEFT] = remap(leftDelta, FROWN_LOW, FROWN_HIGH)
    }

    private fun solvePucker(landmarks: FloatArray, faceIod: Float, output: MutableMap<BlendShape, Float>) {
        val mouthWidth = dist(landmarks, LM.MOUTH_RIGHT, LM.MOUTH_LEFT) / faceIod
        val narrowing = (PUCKER_NEUTRAL_WIDTH - mouthWidth).coerceAtLeast(0f)
        output[BlendShape.MOUTH_PUCKER] = remap(narrowing, PUCKER_LOW, PUCKER_HIGH)
    }

    private fun solveFunnel(landmarks: FloatArray, faceIod: Float, output: MutableMap<BlendShape, Float>) {
        // Funnel = lips rounded forward with opening (like saying "oh")
        val mouthWidth = dist(landmarks, LM.MOUTH_RIGHT, LM.MOUTH_LEFT) / faceIod
        val mouthHeight = dist(landmarks, LM.MOUTH_UPPER_CENTER, LM.MOUTH_LOWER_CENTER) / faceIod
        val narrowing = (PUCKER_NEUTRAL_WIDTH - mouthWidth).coerceAtLeast(0f)
        val funnelVal = (narrowing * mouthHeight)
        output[BlendShape.MOUTH_FUNNEL] = remap(funnelVal, FUNNEL_LOW, FUNNEL_HIGH)
    }

    private fun solveMouthSides(landmarks: FloatArray, faceIod: Float, output: MutableMap<BlendShape, Float>) {
        // Mouth left/right: horizontal displacement of mouth center from nose
        val mouthCenterX = (BlendShapeGeometry.x(landmarks, LM.MOUTH_RIGHT) +
            BlendShapeGeometry.x(landmarks, LM.MOUTH_LEFT)) / 2f
        val noseCenterX = BlendShapeGeometry.x(landmarks, LM.NOSE_TIP)
        val offset = (mouthCenterX - noseCenterX) / faceIod
        output[BlendShape.MOUTH_LEFT] = remap(-offset, 0f, 0.05f)
        output[BlendShape.MOUTH_RIGHT] = remap(offset, 0f, 0.05f)
        // Jaw sideways
        output[BlendShape.JAW_LEFT] = remap(-offset, 0f, 0.08f)
        output[BlendShape.JAW_RIGHT] = remap(offset, 0f, 0.08f)
        output[BlendShape.JAW_FORWARD] = 0f
    }

    private fun solveMouthVertical(landmarks: FloatArray, faceIod: Float, output: MutableMap<BlendShape, Float>) {
        val upperLipRise = dist(landmarks, LM.MOUTH_UPPER_CENTER, LM.NOSE_BOTTOM) / faceIod
        val lowerLipDrop = dist(landmarks, LM.MOUTH_LOWER_CENTER, LM.JAW_CENTER) / faceIod
        // Upper lip up: small movements of the upper lip
        output[BlendShape.MOUTH_UPPER_UP_LEFT] = remap(upperLipRise, 0.08f, 0.15f)
        output[BlendShape.MOUTH_UPPER_UP_RIGHT] = output.getValue(BlendShape.MOUTH_UPPER_UP_LEFT)
        // Lower lip down
        output[BlendShape.MOUTH_LOWER_DOWN_LEFT] = remap(lowerLipDrop, 0.15f, 0.25f)
        output[BlendShape.MOUTH_LOWER_DOWN_RIGHT] = output.getValue(BlendShape.MOUTH_LOWER_DOWN_LEFT)
    }

    /**
     * Set blend shapes that cannot be accurately derived from 66 landmarks
     * to reasonable defaults or approximate values.
     */
    private fun setApproximatedShapes(output: MutableMap<BlendShape, Float>) {
        // These shapes require either more landmarks or 3D depth info
        output.putIfAbsent(BlendShape.MOUTH_STRETCH_LEFT, 0f)
        output.putIfAbsent(BlendShape.MOUTH_STRETCH_RIGHT, 0f)
        output.putIfAbsent(BlendShape.MOUTH_PRESS_LEFT, 0f)
        output.putIfAbsent(BlendShape.MOUTH_PRESS_RIGHT, 0f)
        output.putIfAbsent(BlendShape.MOUTH_SHRUG_UPPER, 0f)
        output.putIfAbsent(BlendShape.MOUTH_SHRUG_LOWER, 0f)
        output.putIfAbsent(BlendShape.MOUTH_ROLL_UPPER, 0f)
        output.putIfAbsent(BlendShape.MOUTH_ROLL_LOWER, 0f)
        output.putIfAbsent(BlendShape.MOUTH_DIMPLE_LEFT, 0f)
        output.putIfAbsent(BlendShape.MOUTH_DIMPLE_RIGHT, 0f)
    }
}
