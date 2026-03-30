package io.github.kmpfacelink.util

import io.github.kmpfacelink.model.HandJoint
import io.github.kmpfacelink.model.HandLandmarkPoint
import kotlin.math.sqrt

/**
 * Extracts translation-invariant and scale-invariant feature vectors from hand landmarks.
 *
 * Normalization steps:
 * 1. Translate all landmarks so the wrist is at the origin
 * 2. Scale all landmarks so the maximum distance from the wrist is 1.0
 * 3. Flatten (x, y, z) coordinates into a single float array
 *
 * This makes the feature vector robust to hand position in the frame and distance
 * from the camera, enabling reliable template matching across different conditions.
 */
public object LandmarkFeatureExtractor {

    /** Number of coordinates per landmark (x, y, z). */
    private const val COORDS_PER_LANDMARK = 3

    /** Number of expected hand landmarks. */
    private const val EXPECTED_LANDMARK_COUNT = 21

    /** Feature vector dimension: 21 landmarks * 3 coordinates. */
    public const val FEATURE_DIMENSION: Int = EXPECTED_LANDMARK_COUNT * COORDS_PER_LANDMARK

    /** Minimum scale to avoid division by near-zero values. */
    private const val MIN_SCALE = 1e-6f

    /**
     * Extract a normalized feature vector from hand landmarks.
     *
     * @param landmarks The 21 hand landmark points
     * @return Normalized feature vector of size [FEATURE_DIMENSION], or null if
     *         the input has fewer than 21 landmarks
     */
    public fun extract(landmarks: List<HandLandmarkPoint>): FloatArray? {
        if (landmarks.size < HandJoint.entries.size) return null

        val sorted = sortByJointIndex(landmarks)
        val wrist = sorted[HandJoint.WRIST.index]

        // Step 1: Translate to wrist-centered coordinates
        val centered = translateToOrigin(sorted, wrist)

        // Step 2: Compute scale (max distance from origin)
        val scale = computeScale(centered)

        // Step 3: Normalize and flatten
        return flattenNormalized(centered, scale)
    }

    /**
     * Compute the average feature vector from multiple samples.
     *
     * @param samples List of feature vectors (all must have size [FEATURE_DIMENSION])
     * @return Averaged feature vector, or null if the list is empty
     */
    public fun computeMean(samples: List<FloatArray>): FloatArray? {
        if (samples.isEmpty()) return null
        val mean = FloatArray(FEATURE_DIMENSION)
        for (sample in samples) {
            for (i in mean.indices) {
                mean[i] += sample[i]
            }
        }
        val count = samples.size.toFloat()
        for (i in mean.indices) {
            mean[i] /= count
        }
        return mean
    }

    private fun sortByJointIndex(landmarks: List<HandLandmarkPoint>): List<HandLandmarkPoint> {
        val byJoint = landmarks.associateBy { it.joint }
        return HandJoint.entries.map { joint ->
            byJoint[joint] ?: HandLandmarkPoint(joint, 0f, 0f, 0f)
        }
    }

    private fun translateToOrigin(
        sorted: List<HandLandmarkPoint>,
        wrist: HandLandmarkPoint,
    ): List<Triple<Float, Float, Float>> =
        sorted.map { point ->
            Triple(point.x - wrist.x, point.y - wrist.y, point.z - wrist.z)
        }

    private fun computeScale(centered: List<Triple<Float, Float, Float>>): Float {
        var maxDist = 0f
        for ((x, y, z) in centered) {
            val dist = sqrt(x * x + y * y + z * z)
            if (dist > maxDist) maxDist = dist
        }
        return if (maxDist < MIN_SCALE) MIN_SCALE else maxDist
    }

    private fun flattenNormalized(
        centered: List<Triple<Float, Float, Float>>,
        scale: Float,
    ): FloatArray {
        val result = FloatArray(FEATURE_DIMENSION)
        for (i in centered.indices) {
            val (x, y, z) = centered[i]
            val offset = i * COORDS_PER_LANDMARK
            result[offset] = x / scale
            result[offset + 1] = y / scale
            result[offset + 2] = z / scale
        }
        return result
    }
}
