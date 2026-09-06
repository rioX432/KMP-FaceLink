package io.github.kmpfacelink.internal.onnx

import io.github.kmpfacelink.internal.onnx.BlendShapeGeometry.dist
import io.github.kmpfacelink.internal.onnx.BlendShapeGeometry.eyeAspectRatio
import io.github.kmpfacelink.internal.onnx.BlendShapeGeometry.iod
import io.github.kmpfacelink.internal.onnx.BlendShapeGeometry.remap
import io.github.kmpfacelink.internal.onnx.BlendShapeGeometry.x
import io.github.kmpfacelink.internal.onnx.BlendShapeGeometry.y
import io.github.kmpfacelink.internal.onnx.OpenSeeFaceLandmarkIndices as LM
import io.github.kmpfacelink.model.BlendShape

/**
 * Solves eye-related ARKit blend shapes from OpenSeeFace 66-point landmarks.
 *
 * Handles: eyeBlink, eyeWide, eyeSquint, eyeLook (in/out/up/down).
 */
internal object EyeBlendShapeSolver {

    // EAR thresholds for blink detection
    private const val EAR_BLINK_LOW = 0.15f
    private const val EAR_BLINK_HIGH = 0.30f

    // EAR thresholds for wide-eye detection
    private const val EAR_WIDE_LOW = 0.35f
    private const val EAR_WIDE_HIGH = 0.50f

    // Eye squint: under-eye compression threshold
    private const val SQUINT_LOW = 0.0f
    private const val SQUINT_HIGH = 0.04f

    fun solve(landmarks: FloatArray, output: MutableMap<BlendShape, Float>) {
        val faceIod = iod(landmarks)
        if (faceIod <= 0f) return

        solveEyeBlink(landmarks, faceIod, output)
        solveEyeWide(landmarks, output)
        solveEyeSquint(landmarks, faceIod, output)
    }

    private fun solveEyeBlink(landmarks: FloatArray, faceIod: Float, output: MutableMap<BlendShape, Float>) {
        val rightEar = rightEyeEar(landmarks)
        val leftEar = leftEyeEar(landmarks)
        // Invert EAR: lower EAR = more blink
        output[BlendShape.EYE_BLINK_RIGHT] = 1f - remap(rightEar, EAR_BLINK_LOW, EAR_BLINK_HIGH)
        output[BlendShape.EYE_BLINK_LEFT] = 1f - remap(leftEar, EAR_BLINK_LOW, EAR_BLINK_HIGH)
    }

    private fun solveEyeWide(landmarks: FloatArray, output: MutableMap<BlendShape, Float>) {
        val rightEar = rightEyeEar(landmarks)
        val leftEar = leftEyeEar(landmarks)
        output[BlendShape.EYE_WIDE_RIGHT] = remap(rightEar, EAR_WIDE_LOW, EAR_WIDE_HIGH)
        output[BlendShape.EYE_WIDE_LEFT] = remap(leftEar, EAR_WIDE_LOW, EAR_WIDE_HIGH)
    }

    private fun solveEyeSquint(landmarks: FloatArray, faceIod: Float, output: MutableMap<BlendShape, Float>) {
        // Squint = lower eyelid moving up (narrowing the eye from below)
        val rightLowerDist = dist(landmarks, LM.RIGHT_EYE_LOWER_1, LM.RIGHT_EYE_LOWER_2) / faceIod
        val leftLowerDist = dist(landmarks, LM.LEFT_EYE_LOWER_1, LM.LEFT_EYE_LOWER_2) / faceIod
        // Smaller distance = more squint
        output[BlendShape.EYE_SQUINT_RIGHT] = 1f - remap(rightLowerDist, SQUINT_LOW, SQUINT_HIGH)
        output[BlendShape.EYE_SQUINT_LEFT] = 1f - remap(leftLowerDist, SQUINT_LOW, SQUINT_HIGH)
    }

    private fun rightEyeEar(landmarks: FloatArray): Float = eyeAspectRatio(
        landmarks,
        outer = LM.RIGHT_EYE_OUTER,
        upper1 = LM.RIGHT_EYE_UPPER_1,
        upper2 = LM.RIGHT_EYE_UPPER_2,
        inner = LM.RIGHT_EYE_INNER,
        lower2 = LM.RIGHT_EYE_LOWER_2,
        lower1 = LM.RIGHT_EYE_LOWER_1,
    )

    private fun leftEyeEar(landmarks: FloatArray): Float = eyeAspectRatio(
        landmarks,
        outer = LM.LEFT_EYE_OUTER,
        upper1 = LM.LEFT_EYE_UPPER_1,
        upper2 = LM.LEFT_EYE_UPPER_2,
        inner = LM.LEFT_EYE_INNER,
        lower2 = LM.LEFT_EYE_LOWER_2,
        lower1 = LM.LEFT_EYE_LOWER_1,
    )
}
