package io.github.kmpfacelink.internal.onnx

import io.github.kmpfacelink.internal.onnx.BlendShapeGeometry.dist
import io.github.kmpfacelink.internal.onnx.BlendShapeGeometry.iod
import io.github.kmpfacelink.internal.onnx.BlendShapeGeometry.remap
import io.github.kmpfacelink.internal.onnx.OpenSeeFaceLandmarkIndices as LM
import io.github.kmpfacelink.model.BlendShape

/**
 * Solves brow, cheek, nose, and tongue ARKit blend shapes
 * from OpenSeeFace 66-point landmarks.
 */
internal object BrowBlendShapeSolver {

    // Brow down: brow-to-eye gap decrease
    private const val BROW_DOWN_NEUTRAL = 0.12f
    private const val BROW_DOWN_LOW = 0.0f
    private const val BROW_DOWN_HIGH = 0.06f

    // Brow up: brow-to-eye gap increase
    private const val BROW_UP_LOW = 0.0f
    private const val BROW_UP_HIGH = 0.05f

    // Brow inner up: inner brow points rising
    private const val BROW_INNER_NEUTRAL = 0.10f
    private const val BROW_INNER_UP_LOW = 0.0f
    private const val BROW_INNER_UP_HIGH = 0.04f

    // Nose sneer: nose wing displacement
    private const val SNEER_LOW = 0.0f
    private const val SNEER_HIGH = 0.03f

    fun solve(landmarks: FloatArray, output: MutableMap<BlendShape, Float>) {
        val faceIod = iod(landmarks)
        if (faceIod <= 0f) return

        solveBrowDown(landmarks, faceIod, output)
        solveBrowOuterUp(landmarks, faceIod, output)
        solveBrowInnerUp(landmarks, faceIod, output)
        solveNoseSneer(landmarks, faceIod, output)
    }

    private fun solveBrowDown(landmarks: FloatArray, faceIod: Float, output: MutableMap<BlendShape, Float>) {
        // Distance from brow mid to upper eyelid
        val rightBrowEyeGap = dist(landmarks, LM.RIGHT_BROW_MID, LM.RIGHT_EYE_UPPER_2) / faceIod
        val leftBrowEyeGap = dist(landmarks, LM.LEFT_BROW_MID, LM.LEFT_EYE_UPPER_2) / faceIod
        // Gap decreases when brow is pushed down
        val rightDown = (BROW_DOWN_NEUTRAL - rightBrowEyeGap).coerceAtLeast(0f)
        val leftDown = (BROW_DOWN_NEUTRAL - leftBrowEyeGap).coerceAtLeast(0f)
        output[BlendShape.BROW_DOWN_RIGHT] = remap(rightDown, BROW_DOWN_LOW, BROW_DOWN_HIGH)
        output[BlendShape.BROW_DOWN_LEFT] = remap(leftDown, BROW_DOWN_LOW, BROW_DOWN_HIGH)
    }

    private fun solveBrowOuterUp(landmarks: FloatArray, faceIod: Float, output: MutableMap<BlendShape, Float>) {
        val rightBrowEyeGap = dist(landmarks, LM.RIGHT_BROW_OUTER, LM.RIGHT_EYE_OUTER) / faceIod
        val leftBrowEyeGap = dist(landmarks, LM.LEFT_BROW_OUTER, LM.LEFT_EYE_OUTER) / faceIod
        // Gap increases when outer brow is raised
        val rightUp = (rightBrowEyeGap - BROW_DOWN_NEUTRAL).coerceAtLeast(0f)
        val leftUp = (leftBrowEyeGap - BROW_DOWN_NEUTRAL).coerceAtLeast(0f)
        output[BlendShape.BROW_OUTER_UP_RIGHT] = remap(rightUp, BROW_UP_LOW, BROW_UP_HIGH)
        output[BlendShape.BROW_OUTER_UP_LEFT] = remap(leftUp, BROW_UP_LOW, BROW_UP_HIGH)
    }

    private fun solveBrowInnerUp(landmarks: FloatArray, faceIod: Float, output: MutableMap<BlendShape, Float>) {
        // Inner brow points distance to nose bridge
        val rightInnerGap = dist(landmarks, LM.RIGHT_BROW_INNER, LM.NOSE_BRIDGE_TOP) / faceIod
        val leftInnerGap = dist(landmarks, LM.LEFT_BROW_INNER, LM.NOSE_BRIDGE_TOP) / faceIod
        val avgGap = (rightInnerGap + leftInnerGap) / 2f
        val innerUp = (avgGap - BROW_INNER_NEUTRAL).coerceAtLeast(0f)
        output[BlendShape.BROW_INNER_UP] = remap(innerUp, BROW_INNER_UP_LOW, BROW_INNER_UP_HIGH)
    }

    private fun solveNoseSneer(landmarks: FloatArray, faceIod: Float, output: MutableMap<BlendShape, Float>) {
        // Nose wing displacement relative to nose center
        val noseWidth = dist(landmarks, LM.NOSE_RIGHT_WING, LM.NOSE_LEFT_WING) / faceIod
        // Wider nose = sneer active (both sides approximated equally for now)
        val sneerVal = (noseWidth - 0.15f).coerceAtLeast(0f)
        output[BlendShape.NOSE_SNEER_RIGHT] = remap(sneerVal, SNEER_LOW, SNEER_HIGH)
        output[BlendShape.NOSE_SNEER_LEFT] = remap(sneerVal, SNEER_LOW, SNEER_HIGH)
    }

}
