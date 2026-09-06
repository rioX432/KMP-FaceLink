package io.github.kmpfacelink.internal.onnx

import io.github.kmpfacelink.internal.onnx.BlendShapeGeometry.dist
import io.github.kmpfacelink.internal.onnx.BlendShapeGeometry.iod
import io.github.kmpfacelink.internal.onnx.BlendShapeGeometry.remap
import io.github.kmpfacelink.internal.onnx.BlendShapeGeometry.x
import io.github.kmpfacelink.internal.onnx.BlendShapeGeometry.y
import io.github.kmpfacelink.internal.onnx.OpenSeeFaceLandmarkIndices as LM
import io.github.kmpfacelink.model.BlendShape
import kotlin.math.abs

/**
 * Solves mouth and jaw ARKit blend shapes from OpenSeeFace 66-point landmarks.
 *
 * Every shape written here is derived from an actual landmark measurement. A shape this solver
 * cannot measure is left out of [output] entirely rather than written as `0f`, so a caller can
 * tell an observed zero from a shape this estimator does not emit.
 *
 * `jawForward` is not emitted: protrusion along the camera axis is not observable in the 2D
 * contour, and the head-pose estimate describes the whole head rather than the jaw.
 *
 * The neutral constants below are population estimates, not per-user measurements. They set where
 * each remap starts responding, so a face whose resting geometry differs from the estimate sees an
 * offset. Calibration against a captured neutral is the intended correction.
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

    // Resting geometry, every value normalized by inter-ocular distance. These are provisional
    // population estimates: they fix where each remap starts responding, so a face whose resting
    // geometry differs sees an offset. `MouthBlendShapeSolverTest` pins a synthetic neutral built
    // to these exact values, so a change here has to be made together with that fixture.
    private const val NEUTRAL_HALF_WIDTH = 0.27f
    private const val STRETCH_SPAN = 0.06f
    private const val DIMPLE_SPAN = 0.05f

    private const val NEUTRAL_UPPER_THICKNESS = 0.033f
    private const val NEUTRAL_LOWER_THICKNESS = 0.040f
    private const val ROLL_SPAN = 0.020f
    private const val SHRUG_SPAN = 0.020f

    private const val NEUTRAL_SIDE_THICKNESS = 0.050f
    private const val PRESS_SPAN = 0.030f

    private const val CHIN_NEUTRAL = 0.73f
    private const val CHIN_OPEN_SPAN = 0.20f

    // Inner lip gap above which the lips are no longer treated as closed.
    private const val LIPS_APART_SPAN = 0.05f

    // Per-side vertical lip travel, measured toward the nose and toward the chin respectively.
    private const val NEUTRAL_UPPER_LIP_TO_NOSE = 0.20f
    private const val UPPER_UP_SPAN = 0.08f
    private const val NEUTRAL_LOWER_LIP_TO_CHIN = 0.41f
    private const val LOWER_DOWN_SPAN = 0.10f

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
        solveMouthClose(landmarks, faceIod, output)
        solveCornerWidth(landmarks, faceIod, output)
        solveLipRoll(landmarks, faceIod, output)
        solveLipPress(landmarks, faceIod, output)
    }

    private fun solveJawOpen(landmarks: FloatArray, faceIod: Float, output: MutableMap<BlendShape, Float>) {
        val innerMouthHeight = dist(landmarks, LM.INNER_MOUTH_UPPER, LM.INNER_MOUTH_LOWER) / faceIod
        output[BlendShape.JAW_OPEN] = remap(innerMouthHeight, JAW_OPEN_LOW, JAW_OPEN_HIGH)
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
        val mouthCenterX = (x(landmarks, LM.MOUTH_RIGHT) + x(landmarks, LM.MOUTH_LEFT)) / 2f
        val noseCenterX = x(landmarks, LM.NOSE_TIP)
        val offset = (mouthCenterX - noseCenterX) / faceIod
        output[BlendShape.MOUTH_LEFT] = remap(-offset, 0f, 0.05f)
        output[BlendShape.MOUTH_RIGHT] = remap(offset, 0f, 0.05f)
        // Jaw sideways
        output[BlendShape.JAW_LEFT] = remap(-offset, 0f, 0.08f)
        output[BlendShape.JAW_RIGHT] = remap(offset, 0f, 0.08f)
    }

    /**
     * Upper and lower lip travel, measured separately on each side so a one-sided expression does
     * not produce a symmetric result.
     *
     * Raising the upper lip moves it toward the nose, so the nose-to-lip distance shrinks; dropping
     * the lower lip moves it toward the chin, so the chin-to-lip distance shrinks. Both are
     * therefore measured as a reduction from the resting distance.
     */
    private fun solveMouthVertical(landmarks: FloatArray, faceIod: Float, output: MutableMap<BlendShape, Float>) {
        val noseBottomY = y(landmarks, LM.NOSE_BOTTOM)
        val chinY = y(landmarks, LM.JAW_CENTER)

        val upperUp = { idx: Int ->
            val gap = abs(y(landmarks, idx) - noseBottomY) / faceIod
            remap(NEUTRAL_UPPER_LIP_TO_NOSE - gap, 0f, UPPER_UP_SPAN)
        }
        output[BlendShape.MOUTH_UPPER_UP_RIGHT] = upperUp(LM.MOUTH_UPPER_CENTER_RIGHT)
        output[BlendShape.MOUTH_UPPER_UP_LEFT] = upperUp(LM.MOUTH_UPPER_CENTER_LEFT)

        val lowerDown = { idx: Int ->
            val gap = abs(chinY - y(landmarks, idx)) / faceIod
            remap(NEUTRAL_LOWER_LIP_TO_CHIN - gap, 0f, LOWER_DOWN_SPAN)
        }
        output[BlendShape.MOUTH_LOWER_DOWN_RIGHT] = lowerDown(LM.MOUTH_LOWER_CENTER_RIGHT)
        output[BlendShape.MOUTH_LOWER_DOWN_LEFT] = lowerDown(LM.MOUTH_LOWER_CENTER_LEFT)
    }

    /**
     * Lips held together while the jaw drops.
     *
     * The jaw signal is the chin-to-nose distance and the lip signal is the inner lip gap, so a
     * dropped jaw with parted lips and a dropped jaw with closed lips are distinguishable.
     * Deriving this from `jawOpen` alone would only invert one signal.
     */
    private fun solveMouthClose(landmarks: FloatArray, faceIod: Float, output: MutableMap<BlendShape, Float>) {
        val chinDrop = dist(landmarks, LM.NOSE_BOTTOM, LM.JAW_CENTER) / faceIod
        val jawDropped = remap(chinDrop, CHIN_NEUTRAL, CHIN_NEUTRAL + CHIN_OPEN_SPAN)
        output[BlendShape.MOUTH_CLOSE] = jawDropped * lipsClosed(landmarks, faceIod)
    }

    /**
     * Corner travel away from and toward the facial midline, measured per side.
     *
     * Drawing a corner inward reads as a dimple only while the lips stay closed; the same
     * narrowing with the lips apart is a pucker or a funnel, which are solved separately.
     */
    private fun solveCornerWidth(landmarks: FloatArray, faceIod: Float, output: MutableMap<BlendShape, Float>) {
        val midlineX = x(landmarks, LM.NOSE_TIP)
        val closed = lipsClosed(landmarks, faceIod)
        val rightHalf = abs(x(landmarks, LM.MOUTH_RIGHT) - midlineX) / faceIod
        val leftHalf = abs(x(landmarks, LM.MOUTH_LEFT) - midlineX) / faceIod

        output[BlendShape.MOUTH_STRETCH_RIGHT] = remap(rightHalf - NEUTRAL_HALF_WIDTH, 0f, STRETCH_SPAN)
        output[BlendShape.MOUTH_STRETCH_LEFT] = remap(leftHalf - NEUTRAL_HALF_WIDTH, 0f, STRETCH_SPAN)
        output[BlendShape.MOUTH_DIMPLE_RIGHT] = remap(NEUTRAL_HALF_WIDTH - rightHalf, 0f, DIMPLE_SPAN) * closed
        output[BlendShape.MOUTH_DIMPLE_LEFT] = remap(NEUTRAL_HALF_WIDTH - leftHalf, 0f, DIMPLE_SPAN) * closed
    }

    /**
     * Visible vermilion thickness, measured outer lip edge to inner lip edge.
     *
     * Rolling a lip inward hides vermilion and pushing it outward exposes more, so one measurement
     * per lip yields both shapes with opposite signs. They are mutually exclusive by construction,
     * which matches the physical movement.
     */
    private fun solveLipRoll(landmarks: FloatArray, faceIod: Float, output: MutableMap<BlendShape, Float>) {
        val upper = dist(landmarks, LM.MOUTH_UPPER_CENTER, LM.INNER_MOUTH_UPPER) / faceIod
        val lower = dist(landmarks, LM.MOUTH_LOWER_CENTER, LM.INNER_MOUTH_LOWER) / faceIod

        output[BlendShape.MOUTH_ROLL_UPPER] = remap(NEUTRAL_UPPER_THICKNESS - upper, 0f, ROLL_SPAN)
        output[BlendShape.MOUTH_ROLL_LOWER] = remap(NEUTRAL_LOWER_THICKNESS - lower, 0f, ROLL_SPAN)
        output[BlendShape.MOUTH_SHRUG_UPPER] = remap(upper - NEUTRAL_UPPER_THICKNESS, 0f, SHRUG_SPAN)
        output[BlendShape.MOUTH_SHRUG_LOWER] = remap(lower - NEUTRAL_LOWER_THICKNESS, 0f, SHRUG_SPAN)
    }

    /**
     * Lips pressed together, measured as thinning of the corner vermilion while the lips are closed.
     *
     * The closed factor keeps an open mouth, whose corners also thin, from reading as a press.
     */
    private fun solveLipPress(landmarks: FloatArray, faceIod: Float, output: MutableMap<BlendShape, Float>) {
        val closed = lipsClosed(landmarks, faceIod)
        val right = dist(landmarks, LM.MOUTH_UPPER_RIGHT, LM.MOUTH_LOWER_RIGHT) / faceIod
        val left = dist(landmarks, LM.MOUTH_UPPER_LEFT, LM.MOUTH_LOWER_LEFT) / faceIod

        output[BlendShape.MOUTH_PRESS_RIGHT] = remap(NEUTRAL_SIDE_THICKNESS - right, 0f, PRESS_SPAN) * closed
        output[BlendShape.MOUTH_PRESS_LEFT] = remap(NEUTRAL_SIDE_THICKNESS - left, 0f, PRESS_SPAN) * closed
    }

    /** 1 when the inner lip edges touch, falling to 0 as they part. */
    private fun lipsClosed(landmarks: FloatArray, faceIod: Float): Float {
        val innerGap = dist(landmarks, LM.INNER_MOUTH_UPPER, LM.INNER_MOUTH_LOWER) / faceIod
        return 1f - remap(innerGap, 0f, LIPS_APART_SPAN)
    }
}
