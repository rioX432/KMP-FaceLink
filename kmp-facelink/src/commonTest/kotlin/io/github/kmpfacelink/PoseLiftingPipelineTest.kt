package io.github.kmpfacelink

import io.github.kmpfacelink.model.Body3DConfig
import io.github.kmpfacelink.model.BodyJoint
import io.github.kmpfacelink.model.BodyLandmarkPoint
import io.github.kmpfacelink.model.LiftingStrategy
import io.github.kmpfacelink.util.createLiftingStrategy
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Contract-style tests verifying the pose lifting strategy integration pattern.
 *
 * These tests verify the contract that body trackers (MediaPipeBodyTracker,
 * VisionBodyTracker) rely on: the factory creates a strategy from config, and
 * the strategy correctly transforms landmarks without corrupting x/y data.
 */
class PoseLiftingPipelineTest {

    @Test
    fun statisticalStrategyProducesNonZeroZForForeshortenedPose() {
        val config = Body3DConfig(enabled = true, strategy = LiftingStrategy.STATISTICAL)
        val strategy = config.createLiftingStrategy()
        assertNotNull(strategy)

        val result = strategy.estimateDepth(buildForeshortenedPose())

        assertTrue(result.any { it.z != 0f }, "Statistical strategy should produce non-zero z")
    }

    @Test
    fun perspectiveStrategyProducesNonZeroZForForeshortenedPose() {
        val config = Body3DConfig(
            enabled = true,
            strategy = LiftingStrategy.PERSPECTIVE,
            shoulderReferenceWidth = 0.3f,
        )
        val strategy = config.createLiftingStrategy()
        assertNotNull(strategy)

        val result = strategy.estimateDepth(buildForeshortenedPose())

        assertTrue(result.any { it.z != 0f }, "Perspective strategy should produce non-zero z")
    }

    @Test
    fun disabledConfigReturnsNullStrategy() {
        val config = Body3DConfig(enabled = false)

        val strategy = config.createLiftingStrategy()

        assertNull(strategy)
    }

    @Test
    fun strategyHandlesEmptyLandmarkList() {
        val config = Body3DConfig(enabled = true, strategy = LiftingStrategy.STATISTICAL)
        val strategy = config.createLiftingStrategy()
        assertNotNull(strategy)

        val result = strategy.estimateDepth(emptyList())

        assertTrue(result.isEmpty(), "Empty input should produce empty output")
    }

    @Test
    fun strategyHandlesAllZeroVisibilityLandmarks() {
        val config = Body3DConfig(enabled = true, strategy = LiftingStrategy.STATISTICAL)
        val strategy = config.createLiftingStrategy()
        assertNotNull(strategy)

        val landmarks = buildPoseWithZeroVisibility()
        val result = strategy.estimateDepth(landmarks)

        // With zero visibility, the strategy should not modify z values
        result.forEach { point ->
            assertEquals(0f, point.z, "Zero-visibility landmarks should remain unchanged")
        }
    }

    @Test
    fun strategyPreservesXYValues() {
        val config = Body3DConfig(enabled = true, strategy = LiftingStrategy.STATISTICAL)
        val strategy = config.createLiftingStrategy()
        assertNotNull(strategy)

        val landmarks = buildForeshortenedPose()
        val result = strategy.estimateDepth(landmarks)

        assertEquals(landmarks.size, result.size)
        landmarks.forEachIndexed { index, original ->
            assertEquals(original.x, result[index].x, "x should be preserved for ${original.joint}")
            assertEquals(original.y, result[index].y, "y should be preserved for ${original.joint}")
        }
    }

    @Test
    fun strategyProducesConsistentResultsOnRepeatedCalls() {
        val config = Body3DConfig(enabled = true, strategy = LiftingStrategy.STATISTICAL)
        val strategy = config.createLiftingStrategy()
        assertNotNull(strategy)

        val landmarks = buildForeshortenedPose()
        val firstResult = strategy.estimateDepth(landmarks)
        val secondResult = strategy.estimateDepth(landmarks)

        assertEquals(firstResult.size, secondResult.size)
        firstResult.forEachIndexed { index, first ->
            assertEquals(
                first.z,
                secondResult[index].z,
                "Repeated calls should produce identical z for ${first.joint}",
            )
        }
    }

    @Test
    fun perspectiveStrategyPreservesXYValues() {
        val config = Body3DConfig(
            enabled = true,
            strategy = LiftingStrategy.PERSPECTIVE,
            shoulderReferenceWidth = 0.3f,
        )
        val strategy = config.createLiftingStrategy()
        assertNotNull(strategy)

        val landmarks = buildForeshortenedPose()
        val result = strategy.estimateDepth(landmarks)

        landmarks.forEachIndexed { index, original ->
            assertEquals(original.x, result[index].x, "x should be preserved for ${original.joint}")
            assertEquals(original.y, result[index].y, "y should be preserved for ${original.joint}")
        }
    }

    // --- Helpers ---

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

    private fun buildPoseWithZeroVisibility(): List<BodyLandmarkPoint> = listOf(
        point(BodyJoint.LEFT_SHOULDER, 0.4f, 0.4f, visibility = 0f),
        point(BodyJoint.RIGHT_SHOULDER, 0.6f, 0.4f, visibility = 0f),
        point(BodyJoint.LEFT_HIP, 0.4f, 0.7f, visibility = 0f),
        point(BodyJoint.RIGHT_HIP, 0.6f, 0.7f, visibility = 0f),
        point(BodyJoint.LEFT_ELBOW, 0.3f, 0.55f, visibility = 0f),
        point(BodyJoint.LEFT_WRIST, 0.25f, 0.7f, visibility = 0f),
        point(BodyJoint.RIGHT_ELBOW, 0.7f, 0.55f, visibility = 0f),
        point(BodyJoint.RIGHT_WRIST, 0.75f, 0.7f, visibility = 0f),
    )

    private companion object {
        fun point(
            joint: BodyJoint,
            x: Float,
            y: Float,
            visibility: Float = 1f,
        ): BodyLandmarkPoint = BodyLandmarkPoint(
            joint = joint,
            x = x,
            y = y,
            z = 0f,
            visibility = visibility,
        )
    }
}
