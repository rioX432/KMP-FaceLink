package io.github.kmpfacelink

import io.github.kmpfacelink.model.BlendShape
import io.github.kmpfacelink.model.BlendShapeData
import io.github.kmpfacelink.model.GazeData
import io.github.kmpfacelink.util.GazeEstimator
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class GazeEstimatorTest {

    /**
     * The eight eye-look shapes, all measured, with the given overrides applied.
     *
     * Built explicitly rather than from a fully populated map so that "measured as zero" and
     * "not measured" stay distinguishable in these tests.
     */
    private fun gazeInput(vararg overrides: Pair<BlendShape, Float>): BlendShapeData =
        GAZE_SHAPES.associateWith { 0f } + overrides

    private fun estimate(blendShapes: BlendShapeData): GazeData =
        assertNotNull(GazeEstimator.estimate(blendShapes), "expected a gaze estimate")

    @Test
    fun neutralGazeWhenAllZero() {
        val gaze = estimate(gazeInput())
        assertEquals(0f, gaze.leftEyeYaw)
        assertEquals(0f, gaze.leftEyePitch)
        assertEquals(0f, gaze.rightEyeYaw)
        assertEquals(0f, gaze.rightEyePitch)
        assertEquals(0f, gaze.combinedYaw)
        assertEquals(0f, gaze.combinedPitch)
    }

    @Test
    fun lookingRightBothEyes() {
        // Left eye lookIn and right eye lookOut both point away from the nose's left side.
        val gaze = estimate(
            gazeInput(
                BlendShape.EYE_LOOK_IN_LEFT to 1f,
                BlendShape.EYE_LOOK_OUT_RIGHT to 1f,
            ),
        )
        assertEquals(30f, gaze.leftEyeYaw, 0.01f)
        assertEquals(30f, gaze.rightEyeYaw, 0.01f)
        assertEquals(30f, gaze.combinedYaw, 0.01f)
    }

    @Test
    fun lookingLeftBothEyes() {
        val gaze = estimate(
            gazeInput(
                BlendShape.EYE_LOOK_OUT_LEFT to 1f,
                BlendShape.EYE_LOOK_IN_RIGHT to 1f,
            ),
        )
        assertEquals(-30f, gaze.leftEyeYaw, 0.01f)
        assertEquals(-30f, gaze.rightEyeYaw, 0.01f)
        assertEquals(-30f, gaze.combinedYaw, 0.01f)
    }

    @Test
    fun lookingUpBothEyes() {
        val gaze = estimate(
            gazeInput(
                BlendShape.EYE_LOOK_UP_LEFT to 1f,
                BlendShape.EYE_LOOK_UP_RIGHT to 1f,
            ),
        )
        assertEquals(30f, gaze.leftEyePitch, 0.01f)
        assertEquals(30f, gaze.rightEyePitch, 0.01f)
        assertEquals(30f, gaze.combinedPitch, 0.01f)
    }

    @Test
    fun lookingDownBothEyes() {
        val gaze = estimate(
            gazeInput(
                BlendShape.EYE_LOOK_DOWN_LEFT to 1f,
                BlendShape.EYE_LOOK_DOWN_RIGHT to 1f,
            ),
        )
        assertEquals(-30f, gaze.leftEyePitch, 0.01f)
        assertEquals(-30f, gaze.rightEyePitch, 0.01f)
        assertEquals(-30f, gaze.combinedPitch, 0.01f)
    }

    @Test
    fun partialGazeProducesProportionalAngle() {
        val gaze = estimate(gazeInput(BlendShape.EYE_LOOK_IN_LEFT to 0.5f))
        assertEquals(15f, gaze.leftEyeYaw, 0.01f)
    }

    @Test
    fun combinedGazeIsAverageOfBothEyes() {
        val gaze = estimate(gazeInput(BlendShape.EYE_LOOK_IN_LEFT to 1f))
        assertEquals(15f, gaze.combinedYaw, 0.01f)
    }

    // region the estimate is withheld when its inputs were never measured

    @Test
    fun noEstimateWhenNoGazeShapesWereMeasured() {
        assertNull(GazeEstimator.estimate(emptyMap()))
    }

    @Test
    fun noEstimateWhenASingleGazeShapeIsMissing() {
        for (missing in GAZE_SHAPES) {
            assertNull(
                GazeEstimator.estimate(gazeInput() - missing),
                "a gaze estimate was produced without $missing",
            )
        }
    }

    @Test
    fun anEstimatorThatMeasuresNoEyesDoesNotReportForwardGaze() {
        // Reading absent shapes as zero would cancel all eight terms and produce a confident
        // forward gaze. That is the answer this contract exists to prevent.
        val unrelatedShapesOnly = mapOf(BlendShape.JAW_OPEN to 0.4f, BlendShape.MOUTH_PUCKER to 0.2f)
        assertNull(GazeEstimator.estimate(unrelatedShapesOnly))
    }

    // endregion

    companion object {
        private val GAZE_SHAPES = listOf(
            BlendShape.EYE_LOOK_IN_LEFT,
            BlendShape.EYE_LOOK_OUT_LEFT,
            BlendShape.EYE_LOOK_UP_LEFT,
            BlendShape.EYE_LOOK_DOWN_LEFT,
            BlendShape.EYE_LOOK_IN_RIGHT,
            BlendShape.EYE_LOOK_OUT_RIGHT,
            BlendShape.EYE_LOOK_UP_RIGHT,
            BlendShape.EYE_LOOK_DOWN_RIGHT,
        )
    }
}
