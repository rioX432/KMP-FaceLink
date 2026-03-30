package io.github.kmpfacelink.util

import kotlin.math.sqrt

/**
 * Mathematical operations on feature vectors for template matching.
 */
internal object FeatureVectorMath {

    private const val EPSILON = 1e-8f

    /**
     * Compute cosine similarity between two feature vectors.
     *
     * @return Similarity in range [-1.0, 1.0], where 1.0 means identical direction
     */
    fun cosineSimilarity(a: FloatArray, b: FloatArray): Float {
        require(a.size == b.size) { "Vectors must have the same size" }

        var dot = 0f
        var normA = 0f
        var normB = 0f
        for (i in a.indices) {
            dot += a[i] * b[i]
            normA += a[i] * a[i]
            normB += b[i] * b[i]
        }

        val denominator = sqrt(normA) * sqrt(normB)
        return if (denominator < EPSILON) 0f else dot / denominator
    }

    /**
     * Compute L2 (Euclidean) distance between two feature vectors.
     *
     * @return Non-negative distance; 0.0 means identical vectors
     */
    fun l2Distance(a: FloatArray, b: FloatArray): Float {
        require(a.size == b.size) { "Vectors must have the same size" }

        var sum = 0f
        for (i in a.indices) {
            val diff = a[i] - b[i]
            sum += diff * diff
        }
        return sqrt(sum)
    }
}
