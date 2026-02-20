package io.github.kmpfacelink.actions.emotion

import io.github.kmpfacelink.model.BlendShape
import io.github.kmpfacelink.model.BlendShapeData
import io.github.kmpfacelink.model.FaceTrackingData
import io.github.kmpfacelink.model.valueOf
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlin.math.sqrt

/**
 * Classifies facial expressions into basic emotions using cosine similarity
 * against predefined blend shape templates.
 *
 * Each frame's blend shape values are compared to emotion templates,
 * and the emotion with the highest similarity score is selected.
 * When no emotion scores above [neutralThreshold], the result is [Emotion.NEUTRAL].
 *
 * Based on the DZNE research approach of cosine similarity classification
 * from ARKit blend shapes.
 *
 * @param neutralThreshold Minimum similarity score required for a non-neutral classification.
 *   If all emotion scores fall below this threshold, the result is [Emotion.NEUTRAL].
 *   Default is 0.15.
 * @param templates Custom emotion templates. Defaults to built-in FACS-based templates.
 */
public class EmotionClassifier(
    private val neutralThreshold: Float = DEFAULT_NEUTRAL_THRESHOLD,
    private val templates: List<EmotionTemplate> = DEFAULT_EMOTION_TEMPLATES,
) {

    /**
     * Classifies the current blend shape data into an emotion.
     *
     * @param blendShapes Current frame's blend shape values.
     * @param timestampMs Frame timestamp in milliseconds.
     * @return Classification result with the dominant emotion, confidence, and all scores.
     */
    public fun classify(blendShapes: BlendShapeData, timestampMs: Long): EmotionResult {
        val scores = mutableMapOf<Emotion, Float>()

        for (template in templates) {
            scores[template.emotion] = cosineSimilarity(blendShapes, template.weights)
        }

        // Neutral is derived from the absence of strong emotion signals
        val maxScore = scores.values.maxOrNull() ?: 0f
        val neutralScore = (1f - maxScore).coerceIn(0f, 1f)
        scores[Emotion.NEUTRAL] = neutralScore

        val dominant = if (maxScore >= neutralThreshold) {
            scores.maxByOrNull { it.value }?.key ?: Emotion.NEUTRAL
        } else {
            Emotion.NEUTRAL
        }

        val confidence = scores[dominant] ?: 0f

        return EmotionResult(
            emotion = dominant,
            confidence = confidence,
            scores = scores.toMap(),
            timestampMs = timestampMs,
        )
    }

    /**
     * Classifies a [FaceTrackingData] frame.
     * Returns [Emotion.NEUTRAL] with zero confidence when face is not tracking.
     */
    public fun classify(data: FaceTrackingData): EmotionResult {
        if (!data.isTracking) {
            return EmotionResult(
                emotion = Emotion.NEUTRAL,
                confidence = 0f,
                scores = Emotion.entries.associateWith { 0f },
                timestampMs = data.timestampMs,
            )
        }
        return classify(data.blendShapes, data.timestampMs)
    }

    internal companion object {
        internal const val DEFAULT_NEUTRAL_THRESHOLD = 0.15f

        /**
         * Computes cosine similarity between the observed blend shapes
         * and an emotion template's expected weights.
         *
         * Only the blend shapes specified in the template are used for comparison.
         */
        internal fun cosineSimilarity(
            observed: BlendShapeData,
            template: Map<BlendShape, Float>,
        ): Float {
            if (template.isEmpty()) return 0f

            var dotProduct = 0f
            var observedMagnitude = 0f
            var templateMagnitude = 0f

            for ((shape, templateWeight) in template) {
                val observedValue = observed.valueOf(shape)
                dotProduct += observedValue * templateWeight
                observedMagnitude += observedValue * observedValue
                templateMagnitude += templateWeight * templateWeight
            }

            val denominator = sqrt(observedMagnitude) * sqrt(templateMagnitude)
            if (denominator == 0f) return 0f

            return (dotProduct / denominator).coerceIn(0f, 1f)
        }
    }
}

/**
 * Classifies each [FaceTrackingData] frame into an [EmotionResult].
 *
 * @param classifier The emotion classifier to use. Defaults to a new instance with default settings.
 * @return A flow of emotion classification results.
 */
public fun Flow<FaceTrackingData>.classifyEmotion(
    classifier: EmotionClassifier = EmotionClassifier(),
): Flow<EmotionResult> = map { classifier.classify(it) }
