package io.github.kmpfacelink

import io.github.kmpfacelink.model.Body3DConfig
import io.github.kmpfacelink.model.BodyJoint
import io.github.kmpfacelink.model.BodyLandmarkPoint
import io.github.kmpfacelink.model.LiftingStrategy
import io.github.kmpfacelink.util.DepthFromPerspective
import io.github.kmpfacelink.util.StatisticalPoseLifter
import io.github.kmpfacelink.util.createLiftingStrategy
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PoseLiftingTest {

    // Helper: build a minimal set of body landmarks for a front-facing pose
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

    // Helper: build a pose where left arm is foreshortened (pointing toward camera)
    private fun buildForeshortenedArmPose(): List<BodyLandmarkPoint> = listOf(
        point(BodyJoint.LEFT_SHOULDER, 0.4f, 0.4f),
        point(BodyJoint.RIGHT_SHOULDER, 0.6f, 0.4f),
        point(BodyJoint.LEFT_HIP, 0.4f, 0.7f),
        point(BodyJoint.RIGHT_HIP, 0.6f, 0.7f),
        // Left arm is foreshortened: elbow and wrist are very close to shoulder in 2D
        point(BodyJoint.LEFT_ELBOW, 0.39f, 0.42f),
        point(BodyJoint.LEFT_WRIST, 0.38f, 0.43f),
        // Right arm is normal
        point(BodyJoint.RIGHT_ELBOW, 0.7f, 0.55f),
        point(BodyJoint.RIGHT_WRIST, 0.75f, 0.7f),
        point(BodyJoint.LEFT_KNEE, 0.4f, 0.85f),
        point(BodyJoint.LEFT_ANKLE, 0.4f, 0.95f),
        point(BodyJoint.RIGHT_KNEE, 0.6f, 0.85f),
        point(BodyJoint.RIGHT_ANKLE, 0.6f, 0.95f),
    )

    @Test
    fun statisticalLifterReturnsLandmarksUnchangedWhenNoTorso() {
        val lifter = StatisticalPoseLifter()
        val landmarks = listOf(
            point(BodyJoint.NOSE, 0.5f, 0.3f),
        )
        val result = lifter.estimateDepth(landmarks)
        assertEquals(landmarks, result)
    }

    @Test
    fun statisticalLifterEstimatesZForLimbs() {
        val lifter = StatisticalPoseLifter()
        val landmarks = buildFrontFacingPose()
        val result = lifter.estimateDepth(landmarks)

        // Shoulder joints should not have estimated depth (they are chain roots)
        val leftShoulder = result.first { it.joint == BodyJoint.LEFT_SHOULDER }
        assertEquals(0f, leftShoulder.z)
    }

    @Test
    fun statisticalLifterDetectsForeshortening() {
        val lifter = StatisticalPoseLifter()
        val landmarks = buildForeshortenedArmPose()
        val result = lifter.estimateDepth(landmarks)

        val leftWrist = result.first { it.joint == BodyJoint.LEFT_WRIST }
        val rightWrist = result.first { it.joint == BodyJoint.RIGHT_WRIST }

        // Foreshortened left arm should have larger absolute z than normal right arm
        assertTrue(
            abs(leftWrist.z) > abs(rightWrist.z),
            "Foreshortened arm should have larger depth estimate: " +
                "left=${leftWrist.z}, right=${rightWrist.z}",
        )
    }

    @Test
    fun estimateSegmentDepthReturnsZeroWhenProjectedLongerThanExpected() {
        val depth = StatisticalPoseLifter.estimateSegmentDepth(
            expectedLength = 0.1f,
            projectedLength = 0.15f,
        )
        assertEquals(0f, depth)
    }

    @Test
    fun estimateSegmentDepthReturnsPositiveWhenForeshortened() {
        val depth = StatisticalPoseLifter.estimateSegmentDepth(
            expectedLength = 0.2f,
            projectedLength = 0.1f,
        )
        assertTrue(depth > 0f, "Expected positive depth for foreshortened segment, got $depth")
    }

    @Test
    fun estimateSegmentDepthWithZeroExpected() {
        val depth = StatisticalPoseLifter.estimateSegmentDepth(
            expectedLength = 0f,
            projectedLength = 0.1f,
        )
        assertEquals(0f, depth)
    }

    @Test
    fun distance2DComputesCorrectly() {
        val a = point(BodyJoint.LEFT_SHOULDER, 0f, 0f)
        val b = point(BodyJoint.RIGHT_SHOULDER, 0.3f, 0.4f)
        val dist = StatisticalPoseLifter.distance2D(a, b)
        assertTrue(abs(dist - 0.5f) < EPSILON, "Expected ~0.5, got $dist")
    }

    @Test
    fun computeTorsoLengthWithBothSides() {
        val jointMap = mapOf(
            BodyJoint.LEFT_SHOULDER to point(BodyJoint.LEFT_SHOULDER, 0.4f, 0.4f),
            BodyJoint.RIGHT_SHOULDER to point(BodyJoint.RIGHT_SHOULDER, 0.6f, 0.4f),
            BodyJoint.LEFT_HIP to point(BodyJoint.LEFT_HIP, 0.4f, 0.7f),
            BodyJoint.RIGHT_HIP to point(BodyJoint.RIGHT_HIP, 0.6f, 0.7f),
        )
        val torso = StatisticalPoseLifter.computeTorsoLength(jointMap)
        assertTrue(torso != null && torso > 0f)
        // Both sides are 0.3f (vertical distance), so average is 0.3f
        assertTrue(abs(torso - 0.3f) < EPSILON, "Expected ~0.3, got $torso")
    }

    @Test
    fun computeTorsoLengthReturnsNullWithNoData() {
        val torso = StatisticalPoseLifter.computeTorsoLength(emptyMap())
        assertNull(torso)
    }

    @Test
    fun perspectiveLifterComputesDistanceFactor() {
        val lifter = DepthFromPerspective(referenceShoulderWidth = 0.2f)
        val jointMap = mapOf(
            BodyJoint.LEFT_SHOULDER to point(BodyJoint.LEFT_SHOULDER, 0.4f, 0.5f),
            BodyJoint.RIGHT_SHOULDER to point(BodyJoint.RIGHT_SHOULDER, 0.6f, 0.5f),
        )
        val factor = lifter.computeDistanceFactor(jointMap)
        // Observed width = 0.2, reference = 0.2, so factor = 1.0
        assertTrue(abs(factor - 1f) < EPSILON, "Expected ~1.0, got $factor")
    }

    @Test
    fun perspectiveLifterFartherSubjectSmallFactor() {
        val lifter = DepthFromPerspective(referenceShoulderWidth = 0.3f)
        val jointMap = mapOf(
            BodyJoint.LEFT_SHOULDER to point(BodyJoint.LEFT_SHOULDER, 0.45f, 0.5f),
            BodyJoint.RIGHT_SHOULDER to point(BodyJoint.RIGHT_SHOULDER, 0.55f, 0.5f),
        )
        val factor = lifter.computeDistanceFactor(jointMap)
        // Observed width = 0.1, reference = 0.3, factor < 1.0 (farther)
        assertTrue(factor < 1f, "Expected factor < 1 for farther subject, got $factor")
    }

    @Test
    fun perspectiveLifterAddsGlobalDepth() {
        val lifter = DepthFromPerspective(referenceShoulderWidth = 0.3f)
        val landmarks = buildFrontFacingPose()
        val result = lifter.estimateDepth(landmarks)

        // Since observed shoulder width (0.2) < reference (0.3), person is farther,
        // so all joints should have positive global z offset
        val nose = result.firstOrNull { it.joint == BodyJoint.LEFT_SHOULDER }
        assertTrue(
            nose != null && nose.z != 0f,
            "Expected non-zero z from perspective lifting",
        )
    }

    @Test
    fun body3DConfigDisabledReturnsNullStrategy() {
        val config = Body3DConfig(enabled = false)
        assertNull(config.createLiftingStrategy())
    }

    @Test
    fun body3DConfigStatisticalCreatesCorrectStrategy() {
        val config = Body3DConfig(enabled = true, strategy = LiftingStrategy.STATISTICAL)
        val strategy = config.createLiftingStrategy()
        assertIs<StatisticalPoseLifter>(strategy)
    }

    @Test
    fun body3DConfigPerspectiveCreatesCorrectStrategy() {
        val config = Body3DConfig(enabled = true, strategy = LiftingStrategy.PERSPECTIVE)
        val strategy = config.createLiftingStrategy()
        assertIs<DepthFromPerspective>(strategy)
    }

    @Test
    fun lowVisibilityJointsAreSkipped() {
        val lifter = StatisticalPoseLifter()
        val landmarks = listOf(
            point(BodyJoint.LEFT_SHOULDER, 0.4f, 0.4f),
            point(BodyJoint.RIGHT_SHOULDER, 0.6f, 0.4f),
            point(BodyJoint.LEFT_HIP, 0.4f, 0.7f),
            point(BodyJoint.RIGHT_HIP, 0.6f, 0.7f),
            // Low visibility elbow — chain should stop here
            BodyLandmarkPoint(BodyJoint.LEFT_ELBOW, 0.39f, 0.42f, 0f, 0.1f),
            point(BodyJoint.LEFT_WRIST, 0.38f, 0.43f),
        )
        val result = lifter.estimateDepth(landmarks)
        val leftElbow = result.first { it.joint == BodyJoint.LEFT_ELBOW }
        val leftWrist = result.first { it.joint == BodyJoint.LEFT_WRIST }
        // Both should keep original z since chain was interrupted
        assertEquals(0f, leftElbow.z)
        assertEquals(0f, leftWrist.z)
    }

    @Test
    fun symmetricPoseProducesSymmetricDepth() {
        val lifter = StatisticalPoseLifter()
        val landmarks = buildFrontFacingPose()
        val result = lifter.estimateDepth(landmarks)

        val leftElbow = result.first { it.joint == BodyJoint.LEFT_ELBOW }
        val rightElbow = result.first { it.joint == BodyJoint.RIGHT_ELBOW }
        // For a symmetric pose, depth estimates should be similar in magnitude
        assertTrue(
            abs(abs(leftElbow.z) - abs(rightElbow.z)) < SYMMETRY_TOLERANCE,
            "Symmetric pose should produce similar depth: left=${leftElbow.z}, right=${rightElbow.z}",
        )
    }

    @Test
    fun body3DConfigRequiresPositiveShoulderWidth() {
        try {
            Body3DConfig(shoulderReferenceWidth = 0f)
            assertTrue(false, "Should have thrown IllegalArgumentException")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message?.contains("shoulderReferenceWidth") == true)
        }
    }

    @Test
    fun body3DConfigDefaults() {
        val config = Body3DConfig()
        assertEquals(false, config.enabled)
        assertEquals(LiftingStrategy.STATISTICAL, config.strategy)
        assertEquals(Body3DConfig.DEFAULT_SHOULDER_REFERENCE_WIDTH, config.shoulderReferenceWidth)
    }

    private companion object {
        const val EPSILON = 0.01f
        const val SYMMETRY_TOLERANCE = 0.05f

        fun point(joint: BodyJoint, x: Float, y: Float): BodyLandmarkPoint =
            BodyLandmarkPoint(joint = joint, x = x, y = y, z = 0f, visibility = 1f)
    }
}
