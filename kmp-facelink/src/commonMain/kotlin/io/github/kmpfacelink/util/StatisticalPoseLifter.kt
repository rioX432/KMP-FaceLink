package io.github.kmpfacelink.util

import io.github.kmpfacelink.model.BodyJoint
import io.github.kmpfacelink.model.BodyLandmarkPoint
import kotlin.math.sqrt

/**
 * Estimates z-depth from 2D body poses using anthropometric proportions.
 *
 * Uses known human body segment ratios to infer depth from limb foreshortening:
 * when a projected limb appears shorter than its expected length in 2D,
 * the depth difference is estimated via the Pythagorean theorem.
 *
 * Expected 3D limb length = reference ratio * torso length.
 * If projected 2D length < expected 3D length, the "missing" length is depth:
 *   z_delta = sqrt(expected^2 - projected^2)
 *
 * The sign is determined by comparing child joint y-position to parent joint —
 * heuristic: if the child joint is higher than expected, it points toward the camera (negative z).
 */
public class StatisticalPoseLifter : PoseLiftingStrategy {

    override fun estimateDepth(landmarks: List<BodyLandmarkPoint>): List<BodyLandmarkPoint> {
        val jointMap = landmarks.associateBy { it.joint }
        val torsoLength = computeTorsoLength(jointMap) ?: return landmarks
        val depthMap = mutableMapOf<BodyJoint, Float>()
        estimateLimbChainDepth(jointMap, torsoLength, depthMap, LEFT_ARM_CHAIN, LEFT_ARM_RATIOS)
        estimateLimbChainDepth(jointMap, torsoLength, depthMap, RIGHT_ARM_CHAIN, RIGHT_ARM_RATIOS)
        estimateLimbChainDepth(jointMap, torsoLength, depthMap, LEFT_LEG_CHAIN, LEFT_LEG_RATIOS)
        estimateLimbChainDepth(jointMap, torsoLength, depthMap, RIGHT_LEG_CHAIN, RIGHT_LEG_RATIOS)
        return landmarks.map { point ->
            val estimatedZ = depthMap[point.joint]
            if (estimatedZ != null) point.copy(z = estimatedZ) else point
        }
    }

    internal companion object {
        // Limb chains: parent -> child joint sequences
        internal val LEFT_ARM_CHAIN = listOf(
            BodyJoint.LEFT_SHOULDER,
            BodyJoint.LEFT_ELBOW,
            BodyJoint.LEFT_WRIST,
        )
        internal val RIGHT_ARM_CHAIN = listOf(
            BodyJoint.RIGHT_SHOULDER,
            BodyJoint.RIGHT_ELBOW,
            BodyJoint.RIGHT_WRIST,
        )
        internal val LEFT_LEG_CHAIN = listOf(
            BodyJoint.LEFT_HIP,
            BodyJoint.LEFT_KNEE,
            BodyJoint.LEFT_ANKLE,
        )
        internal val RIGHT_LEG_CHAIN = listOf(
            BodyJoint.RIGHT_HIP,
            BodyJoint.RIGHT_KNEE,
            BodyJoint.RIGHT_ANKLE,
        )

        // Anthropometric ratios: expected segment length relative to torso
        // (shoulder-to-hip distance). Based on average adult proportions.
        // Upper arm ≈ 0.8 * torso, forearm ≈ 0.75 * torso
        // Thigh ≈ 1.0 * torso, shin ≈ 0.95 * torso
        internal val LEFT_ARM_RATIOS = floatArrayOf(0.8f, 0.75f)
        internal val RIGHT_ARM_RATIOS = floatArrayOf(0.8f, 0.75f)
        internal val LEFT_LEG_RATIOS = floatArrayOf(1.0f, 0.95f)
        internal val RIGHT_LEG_RATIOS = floatArrayOf(1.0f, 0.95f)

        /**
         * Compute torso length as the average of left and right shoulder-to-hip distances.
         */
        internal fun computeTorsoLength(jointMap: Map<BodyJoint, BodyLandmarkPoint>): Float? {
            val ls = jointMap[BodyJoint.LEFT_SHOULDER]
            val rs = jointMap[BodyJoint.RIGHT_SHOULDER]
            val lh = jointMap[BodyJoint.LEFT_HIP]
            val rh = jointMap[BodyJoint.RIGHT_HIP]
            val leftTorso = if (ls != null && lh != null) distance2D(ls, lh) else null
            val rightTorso = if (rs != null && rh != null) distance2D(rs, rh) else null
            return when {
                leftTorso != null && rightTorso != null -> (leftTorso + rightTorso) / 2f
                leftTorso != null -> leftTorso
                rightTorso != null -> rightTorso
                else -> null
            }
        }

        /**
         * Estimate depth along a joint chain using foreshortening analysis.
         */
        internal fun estimateLimbChainDepth(
            jointMap: Map<BodyJoint, BodyLandmarkPoint>,
            torsoLength: Float,
            depthMap: MutableMap<BodyJoint, Float>,
            chain: List<BodyJoint>,
            ratios: FloatArray,
        ) {
            var accumulatedZ = 0f
            for (i in 0 until chain.size - 1) {
                val parent = jointMap[chain[i]] ?: return
                val child = jointMap[chain[i + 1]] ?: return
                if (parent.visibility < MIN_VISIBILITY || child.visibility < MIN_VISIBILITY) return
                val expectedLength = torsoLength * ratios[i]
                val projectedLength = distance2D(parent, child)
                val zDelta = estimateSegmentDepth(expectedLength, projectedLength)
                // Sign heuristic: if child is above parent by more than expected,
                // the limb is coming toward the camera (negative z)
                val sign = if (child.y < parent.y - expectedLength * SIGN_THRESHOLD) -1f else 1f
                accumulatedZ += zDelta * sign
                depthMap[chain[i + 1]] = accumulatedZ
            }
        }

        /**
         * Estimate the depth component of a single limb segment.
         *
         * Uses Pythagorean theorem: if 3D length is known and 2D projection is shorter,
         * the depth difference accounts for the "missing" length.
         *
         * @return Absolute depth delta (always >= 0)
         */
        internal fun estimateSegmentDepth(expectedLength: Float, projectedLength: Float): Float {
            if (expectedLength <= 0f || projectedLength >= expectedLength) return 0f
            val ratio = projectedLength / expectedLength
            val depthSquared = 1f - ratio * ratio
            return expectedLength * sqrt(depthSquared.coerceAtLeast(0f))
        }

        internal fun distance2D(a: BodyLandmarkPoint, b: BodyLandmarkPoint): Float {
            val dx = a.x - b.x
            val dy = a.y - b.y
            return sqrt(dx * dx + dy * dy)
        }

        /** Minimum visibility to consider a joint for depth estimation. */
        private const val MIN_VISIBILITY = 0.3f

        /** Threshold factor for sign heuristic. */
        private const val SIGN_THRESHOLD = 0.15f
    }
}
