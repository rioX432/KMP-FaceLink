package io.github.kmpfacelink

import io.github.kmpfacelink.model.Body3DConfig
import io.github.kmpfacelink.model.BodyJoint
import io.github.kmpfacelink.model.BodyLandmarkPoint
import io.github.kmpfacelink.model.LiftingStrategy
import io.github.kmpfacelink.util.createLiftingStrategy
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Integration test verifying that [Body3DConfig.createLiftingStrategy] produces
 * a strategy that adds non-zero z values to body landmarks — simulating the
 * pipeline path in MediaPipeBodyTracker and VisionBodyTracker.
 */
class PoseLiftingIntegrationTest {

    @Test
    fun statisticalLiftingProducesNonZeroZ() {
        val config = Body3DConfig(enabled = true, strategy = LiftingStrategy.STATISTICAL)
        val strategy = config.createLiftingStrategy()
        assertNotNull(strategy)

        val landmarks = buildForeshortenedPose()
        val result = strategy.estimateDepth(landmarks)

        val hasNonZeroZ = result.any { it.z != 0f }
        assertTrue(hasNonZeroZ, "Expected at least one landmark with non-zero z after lifting")
    }

    @Test
    fun perspectiveLiftingProducesNonZeroZ() {
        val config = Body3DConfig(
            enabled = true,
            strategy = LiftingStrategy.PERSPECTIVE,
            shoulderReferenceWidth = 0.3f,
        )
        val strategy = config.createLiftingStrategy()
        assertNotNull(strategy)

        val landmarks = buildForeshortenedPose()
        val result = strategy.estimateDepth(landmarks)

        val hasNonZeroZ = result.any { it.z != 0f }
        assertTrue(hasNonZeroZ, "Expected at least one landmark with non-zero z after lifting")
    }

    @Test
    fun disabledConfigReturnsNullStrategy() {
        val config = Body3DConfig(enabled = false)
        val strategy = config.createLiftingStrategy()
        assertTrue(strategy == null, "Disabled config should not produce a strategy")
    }

    @Test
    fun liftingAppliedPerBodySimulation() {
        // Simulates the tracker loop: map landmarks, then apply lifting
        val config = Body3DConfig(enabled = true, strategy = LiftingStrategy.STATISTICAL)
        val strategy = config.createLiftingStrategy()
        assertNotNull(strategy)

        val bodiesLandmarks = listOf(buildForeshortenedPose(), buildFrontFacingPose())

        val liftedBodies = bodiesLandmarks.map { landmarks ->
            strategy.estimateDepth(landmarks)
        }

        // At least the foreshortened pose should have non-zero z
        val foreshortenedBody = liftedBodies[0]
        val wrist = foreshortenedBody.first { it.joint == BodyJoint.LEFT_WRIST }
        assertTrue(wrist.z != 0f, "Foreshortened left wrist should have non-zero z: ${wrist.z}")
    }

    /**
     * Builds a pose with foreshortened left arm (pointing toward camera).
     * The projected distance of the left arm segments is much shorter than
     * the anthropometric expectation, so lifting should infer depth.
     */
    private fun buildForeshortenedPose(): List<BodyLandmarkPoint> = listOf(
        point(BodyJoint.LEFT_SHOULDER, 0.4f, 0.4f),
        point(BodyJoint.RIGHT_SHOULDER, 0.6f, 0.4f),
        point(BodyJoint.LEFT_HIP, 0.4f, 0.7f),
        point(BodyJoint.RIGHT_HIP, 0.6f, 0.7f),
        // Foreshortened left arm: elbow and wrist very close to shoulder
        point(BodyJoint.LEFT_ELBOW, 0.39f, 0.42f),
        point(BodyJoint.LEFT_WRIST, 0.38f, 0.43f),
        // Normal right arm
        point(BodyJoint.RIGHT_ELBOW, 0.7f, 0.55f),
        point(BodyJoint.RIGHT_WRIST, 0.75f, 0.7f),
        point(BodyJoint.LEFT_KNEE, 0.4f, 0.85f),
        point(BodyJoint.LEFT_ANKLE, 0.4f, 0.95f),
        point(BodyJoint.RIGHT_KNEE, 0.6f, 0.85f),
        point(BodyJoint.RIGHT_ANKLE, 0.6f, 0.95f),
    )

    /**
     * Builds a standard front-facing pose where arms are extended naturally.
     */
    private fun buildFrontFacingPose(): List<BodyLandmarkPoint> = listOf(
        point(BodyJoint.LEFT_SHOULDER, 0.4f, 0.4f),
        point(BodyJoint.RIGHT_SHOULDER, 0.6f, 0.4f),
        point(BodyJoint.LEFT_HIP, 0.4f, 0.7f),
        point(BodyJoint.RIGHT_HIP, 0.6f, 0.7f),
        point(BodyJoint.LEFT_ELBOW, 0.3f, 0.55f),
        point(BodyJoint.LEFT_WRIST, 0.25f, 0.7f),
        point(BodyJoint.RIGHT_ELBOW, 0.7f, 0.55f),
        point(BodyJoint.RIGHT_WRIST, 0.75f, 0.7f),
        point(BodyJoint.LEFT_KNEE, 0.4f, 0.85f),
        point(BodyJoint.LEFT_ANKLE, 0.4f, 0.95f),
        point(BodyJoint.RIGHT_KNEE, 0.6f, 0.85f),
        point(BodyJoint.RIGHT_ANKLE, 0.6f, 0.95f),
    )

    private companion object {
        fun point(joint: BodyJoint, x: Float, y: Float): BodyLandmarkPoint =
            BodyLandmarkPoint(joint = joint, x = x, y = y, z = 0f, visibility = 1f)
    }
}
