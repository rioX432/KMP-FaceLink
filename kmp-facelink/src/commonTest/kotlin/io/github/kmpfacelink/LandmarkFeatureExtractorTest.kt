package io.github.kmpfacelink

import io.github.kmpfacelink.model.HandJoint
import io.github.kmpfacelink.model.HandLandmarkPoint
import io.github.kmpfacelink.util.LandmarkFeatureExtractor
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LandmarkFeatureExtractorTest {

    private fun createLandmarksAtOrigin(): List<HandLandmarkPoint> =
        HandJoint.entries.map { joint ->
            HandLandmarkPoint(joint, 0.5f, 0.5f, 0f)
        }

    private fun createSpreadLandmarks(
        offsetX: Float = 0f,
        offsetY: Float = 0f,
    ): List<HandLandmarkPoint> =
        HandJoint.entries.map { joint ->
            HandLandmarkPoint(
                joint = joint,
                x = offsetX + joint.index * 0.02f,
                y = offsetY + joint.index * 0.03f,
                z = 0f,
            )
        }

    @Test
    fun extractReturnsNullForInsufficientLandmarks() {
        val landmarks = listOf(
            HandLandmarkPoint(HandJoint.WRIST, 0.5f, 0.5f),
        )
        assertNull(LandmarkFeatureExtractor.extract(landmarks))
    }

    @Test
    fun extractReturnsCorrectDimension() {
        val landmarks = createSpreadLandmarks()
        val features = LandmarkFeatureExtractor.extract(landmarks)
        assertNotNull(features)
        assertEquals(LandmarkFeatureExtractor.FEATURE_DIMENSION, features.size)
    }

    @Test
    fun extractIsTranslationInvariant() {
        val landmarks1 = createSpreadLandmarks(offsetX = 0.1f, offsetY = 0.1f)
        val landmarks2 = createSpreadLandmarks(offsetX = 0.5f, offsetY = 0.7f)

        val features1 = LandmarkFeatureExtractor.extract(landmarks1)!!
        val features2 = LandmarkFeatureExtractor.extract(landmarks2)!!

        for (i in features1.indices) {
            assertTrue(
                abs(features1[i] - features2[i]) < TOLERANCE,
                "Feature at index $i differs: ${features1[i]} vs ${features2[i]}",
            )
        }
    }

    @Test
    fun extractIsScaleInvariant() {
        val landmarks1 = HandJoint.entries.map { joint ->
            HandLandmarkPoint(joint, joint.index * 0.01f, joint.index * 0.015f, 0f)
        }
        // Same shape, but 2x scale
        val landmarks2 = HandJoint.entries.map { joint ->
            HandLandmarkPoint(joint, joint.index * 0.02f, joint.index * 0.03f, 0f)
        }

        val features1 = LandmarkFeatureExtractor.extract(landmarks1)!!
        val features2 = LandmarkFeatureExtractor.extract(landmarks2)!!

        for (i in features1.indices) {
            assertTrue(
                abs(features1[i] - features2[i]) < TOLERANCE,
                "Feature at index $i differs: ${features1[i]} vs ${features2[i]}",
            )
        }
    }

    @Test
    fun wristFeatureIsZero() {
        val landmarks = createSpreadLandmarks()
        val features = LandmarkFeatureExtractor.extract(landmarks)!!
        // Wrist is at index 0, so first 3 values should be 0
        assertEquals(0f, features[0])
        assertEquals(0f, features[1])
        assertEquals(0f, features[2])
    }

    @Test
    fun computeMeanReturnsNullForEmptyList() {
        assertNull(LandmarkFeatureExtractor.computeMean(emptyList()))
    }

    @Test
    fun computeMeanOfSingleSample() {
        val sample = FloatArray(LandmarkFeatureExtractor.FEATURE_DIMENSION) { it.toFloat() }
        val mean = LandmarkFeatureExtractor.computeMean(listOf(sample))!!
        for (i in sample.indices) {
            assertEquals(sample[i], mean[i])
        }
    }

    @Test
    fun computeMeanAveragesCorrectly() {
        val sample1 = FloatArray(LandmarkFeatureExtractor.FEATURE_DIMENSION) { 2f }
        val sample2 = FloatArray(LandmarkFeatureExtractor.FEATURE_DIMENSION) { 4f }
        val mean = LandmarkFeatureExtractor.computeMean(listOf(sample1, sample2))!!
        for (value in mean) {
            assertTrue(abs(value - 3f) < TOLERANCE)
        }
    }

    @Test
    fun allLandmarksAtSamePointProducesZeroVector() {
        val landmarks = createLandmarksAtOrigin()
        val features = LandmarkFeatureExtractor.extract(landmarks)!!
        for (value in features) {
            assertEquals(0f, value)
        }
    }

    companion object {
        private const val TOLERANCE = 1e-5f
    }
}
