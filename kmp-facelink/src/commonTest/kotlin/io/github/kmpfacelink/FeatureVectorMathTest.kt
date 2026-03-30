package io.github.kmpfacelink

import io.github.kmpfacelink.util.FeatureVectorMath
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FeatureVectorMathTest {

    @Test
    fun cosineSimilarityOfIdenticalVectors() {
        val a = floatArrayOf(1f, 2f, 3f)
        val similarity = FeatureVectorMath.cosineSimilarity(a, a)
        assertTrue(abs(similarity - 1f) < TOLERANCE)
    }

    @Test
    fun cosineSimilarityOfOppositeVectors() {
        val a = floatArrayOf(1f, 2f, 3f)
        val b = floatArrayOf(-1f, -2f, -3f)
        val similarity = FeatureVectorMath.cosineSimilarity(a, b)
        assertTrue(abs(similarity - (-1f)) < TOLERANCE)
    }

    @Test
    fun cosineSimilarityOfOrthogonalVectors() {
        val a = floatArrayOf(1f, 0f, 0f)
        val b = floatArrayOf(0f, 1f, 0f)
        val similarity = FeatureVectorMath.cosineSimilarity(a, b)
        assertTrue(abs(similarity) < TOLERANCE)
    }

    @Test
    fun cosineSimilarityOfScaledVectors() {
        val a = floatArrayOf(1f, 2f, 3f)
        val b = floatArrayOf(2f, 4f, 6f)
        val similarity = FeatureVectorMath.cosineSimilarity(a, b)
        assertTrue(abs(similarity - 1f) < TOLERANCE)
    }

    @Test
    fun cosineSimilarityOfZeroVectorReturnsZero() {
        val a = floatArrayOf(0f, 0f, 0f)
        val b = floatArrayOf(1f, 2f, 3f)
        assertEquals(0f, FeatureVectorMath.cosineSimilarity(a, b))
    }

    @Test
    fun l2DistanceOfIdenticalVectors() {
        val a = floatArrayOf(1f, 2f, 3f)
        val distance = FeatureVectorMath.l2Distance(a, a)
        assertEquals(0f, distance)
    }

    @Test
    fun l2DistanceOfKnownVectors() {
        val a = floatArrayOf(0f, 0f, 0f)
        val b = floatArrayOf(3f, 4f, 0f)
        val distance = FeatureVectorMath.l2Distance(a, b)
        assertTrue(abs(distance - 5f) < TOLERANCE)
    }

    @Test
    fun l2DistanceIsSymmetric() {
        val a = floatArrayOf(1f, 2f, 3f)
        val b = floatArrayOf(4f, 5f, 6f)
        val distAB = FeatureVectorMath.l2Distance(a, b)
        val distBA = FeatureVectorMath.l2Distance(b, a)
        assertTrue(abs(distAB - distBA) < TOLERANCE)
    }

    companion object {
        private const val TOLERANCE = 1e-5f
    }
}
