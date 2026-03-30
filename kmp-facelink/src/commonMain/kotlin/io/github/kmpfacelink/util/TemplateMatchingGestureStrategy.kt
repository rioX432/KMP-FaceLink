package io.github.kmpfacelink.util

import io.github.kmpfacelink.api.GestureClassificationStrategy
import io.github.kmpfacelink.model.GestureClassificationResult
import io.github.kmpfacelink.model.HandLandmarkPoint

/**
 * ML-based gesture classification using template matching.
 *
 * Users register custom gestures by providing sample landmark sets. The strategy
 * extracts normalized feature vectors, computes a mean template per gesture, and
 * classifies new inputs by finding the closest template using cosine similarity.
 *
 * @property confidenceThreshold Minimum cosine similarity (0.0–1.0) required to
 *           accept a classification. Higher values require closer matches.
 */
public class TemplateMatchingGestureStrategy(
    private val confidenceThreshold: Float = DEFAULT_CONFIDENCE_THRESHOLD,
) : GestureClassificationStrategy {

    private val templates = mutableListOf<GestureTemplate>()

    init {
        require(confidenceThreshold in 0f..1f) {
            "confidenceThreshold must be in 0.0..1.0, was $confidenceThreshold"
        }
    }

    /**
     * Register a custom gesture from sample landmark sets.
     *
     * Each sample should represent the same gesture pose captured under slightly
     * different conditions. More samples (5–20) produce more robust recognition.
     *
     * @param name Unique name for this gesture
     * @param samples List of landmark sets, each containing 21 [HandLandmarkPoint]s
     * @throws IllegalArgumentException if name is blank, samples is empty, or any
     *         sample has fewer than 21 landmarks
     */
    public fun registerGesture(name: String, samples: List<List<HandLandmarkPoint>>) {
        require(name.isNotBlank()) { "Gesture name must not be blank" }
        require(samples.isNotEmpty()) { "At least one sample is required" }

        val features = extractFeatures(samples)
        val mean = LandmarkFeatureExtractor.computeMean(features)
            ?: error("Failed to compute mean from samples")

        // Remove existing template with the same name (re-register)
        templates.removeAll { it.name == name }
        templates.add(GestureTemplate(name = name, meanFeature = mean, sampleCount = samples.size))
    }

    /**
     * Remove a registered gesture template.
     *
     * @param name The gesture name to remove
     * @return true if the gesture was found and removed
     */
    public fun unregisterGesture(name: String): Boolean =
        templates.removeAll { it.name == name }

    /**
     * Get the names of all registered gestures.
     */
    public fun registeredGestures(): List<String> = templates.map { it.name }

    /**
     * Remove all registered gesture templates.
     */
    public fun clearGestures() {
        templates.clear()
    }

    override fun classify(landmarks: List<HandLandmarkPoint>): GestureClassificationResult {
        if (templates.isEmpty()) return GestureClassificationResult.NONE

        val feature = LandmarkFeatureExtractor.extract(landmarks)
            ?: return GestureClassificationResult.NONE

        return findBestMatch(feature)
    }

    private fun extractFeatures(samples: List<List<HandLandmarkPoint>>): List<FloatArray> =
        samples.mapNotNull { sample -> LandmarkFeatureExtractor.extract(sample) }
            .also { extracted ->
                require(extracted.isNotEmpty()) {
                    "No valid samples: all samples must contain at least 21 landmarks"
                }
            }

    private fun findBestMatch(feature: FloatArray): GestureClassificationResult {
        var bestSimilarity = -1f
        var bestTemplate: GestureTemplate? = null

        for (template in templates) {
            val similarity = FeatureVectorMath.cosineSimilarity(feature, template.meanFeature)
            if (similarity > bestSimilarity) {
                bestSimilarity = similarity
                bestTemplate = template
            }
        }

        return if (bestTemplate != null && bestSimilarity >= confidenceThreshold) {
            GestureClassificationResult(
                gestureName = bestTemplate.name,
                confidence = bestSimilarity,
            )
        } else {
            GestureClassificationResult.NONE
        }
    }

    public companion object {
        /** Default cosine similarity threshold for accepting a match. */
        public const val DEFAULT_CONFIDENCE_THRESHOLD: Float = 0.85f
    }
}
