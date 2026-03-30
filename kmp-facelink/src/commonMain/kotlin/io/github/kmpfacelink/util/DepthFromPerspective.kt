package io.github.kmpfacelink.util

import io.github.kmpfacelink.model.Body3DConfig
import io.github.kmpfacelink.model.BodyJoint
import io.github.kmpfacelink.model.BodyLandmarkPoint

/**
 * Estimates depth from perspective projection cues.
 *
 * Combines two signals:
 * 1. **Global distance**: Compares observed shoulder width to a reference width to estimate
 *    how far the person is from the camera. When shoulders appear narrower, the person is farther.
 * 2. **Per-limb foreshortening**: Uses the same Pythagorean approach as [StatisticalPoseLifter]
 *    but scales expected limb lengths by the estimated distance factor.
 *
 * @property referenceShoulderWidth Expected shoulder width (normalized) when the subject
 *           is at the reference distance from the camera.
 */
public class DepthFromPerspective(
    private val referenceShoulderWidth: Float = Body3DConfig.DEFAULT_SHOULDER_REFERENCE_WIDTH,
) : PoseLiftingStrategy {

    override fun estimateDepth(landmarks: List<BodyLandmarkPoint>): List<BodyLandmarkPoint> {
        val jointMap = landmarks.associateBy { it.joint }
        val torsoLength = StatisticalPoseLifter.computeTorsoLength(jointMap) ?: return landmarks
        val distanceFactor = computeDistanceFactor(jointMap)
        val depthMap = mutableMapOf<BodyJoint, Float>()
        val globalZ = (1f - distanceFactor) * GLOBAL_DEPTH_SCALE
        val ctx = LimbContext(jointMap, torsoLength, distanceFactor, depthMap)
        estimateWithPerspective(ctx, StatisticalPoseLifter.LEFT_ARM_CHAIN, StatisticalPoseLifter.LEFT_ARM_RATIOS)
        estimateWithPerspective(ctx, StatisticalPoseLifter.RIGHT_ARM_CHAIN, StatisticalPoseLifter.RIGHT_ARM_RATIOS)
        estimateWithPerspective(ctx, StatisticalPoseLifter.LEFT_LEG_CHAIN, StatisticalPoseLifter.LEFT_LEG_RATIOS)
        estimateWithPerspective(ctx, StatisticalPoseLifter.RIGHT_LEG_CHAIN, StatisticalPoseLifter.RIGHT_LEG_RATIOS)
        return landmarks.map { point ->
            val limbZ = depthMap[point.joint] ?: 0f
            point.copy(z = globalZ + limbZ)
        }
    }

    /**
     * Compute how far the subject is relative to reference distance.
     * Returns a factor where 1.0 = at reference distance, <1.0 = farther, >1.0 = closer.
     */
    internal fun computeDistanceFactor(jointMap: Map<BodyJoint, BodyLandmarkPoint>): Float {
        val ls = jointMap[BodyJoint.LEFT_SHOULDER]
        val rs = jointMap[BodyJoint.RIGHT_SHOULDER]
        if (ls == null || rs == null) return 1f
        val observedWidth = StatisticalPoseLifter.distance2D(ls, rs)
        if (observedWidth <= 0f || referenceShoulderWidth <= 0f) return 1f
        return (observedWidth / referenceShoulderWidth).coerceIn(
            MIN_DISTANCE_FACTOR,
            MAX_DISTANCE_FACTOR,
        )
    }

    /**
     * Context for perspective-based limb depth estimation.
     */
    private class LimbContext(
        val jointMap: Map<BodyJoint, BodyLandmarkPoint>,
        val torsoLength: Float,
        val distanceFactor: Float,
        val depthMap: MutableMap<BodyJoint, Float>,
    )

    private companion object {
        const val GLOBAL_DEPTH_SCALE = 0.5f
        const val MIN_VISIBILITY = 0.3f
        const val MIN_DISTANCE_FACTOR = 0.2f
        const val MAX_DISTANCE_FACTOR = 3f
        const val SIGN_THRESHOLD = 0.15f

        fun estimateWithPerspective(
            ctx: LimbContext,
            chain: List<BodyJoint>,
            ratios: FloatArray,
        ) {
            var accumulatedZ = 0f
            for (i in 0 until chain.size - 1) {
                val parent = ctx.jointMap[chain[i]] ?: return
                val child = ctx.jointMap[chain[i + 1]] ?: return
                if (parent.visibility < MIN_VISIBILITY || child.visibility < MIN_VISIBILITY) return
                val expectedLength = ctx.torsoLength * ratios[i] * ctx.distanceFactor
                val projectedLength = StatisticalPoseLifter.distance2D(parent, child)
                val zDelta = StatisticalPoseLifter.estimateSegmentDepth(
                    expectedLength,
                    projectedLength,
                )
                val sign = if (child.y < parent.y - expectedLength * SIGN_THRESHOLD) -1f else 1f
                accumulatedZ += zDelta * sign
                ctx.depthMap[chain[i + 1]] = accumulatedZ
            }
        }
    }
}
