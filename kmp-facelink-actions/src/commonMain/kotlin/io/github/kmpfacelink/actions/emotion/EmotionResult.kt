package io.github.kmpfacelink.actions.emotion

/**
 * Result of emotion classification for a single frame.
 *
 * @property emotion The dominant detected emotion.
 * @property confidence Confidence score for the dominant emotion (0.0–1.0).
 * @property scores Raw similarity scores for all emotions, sorted by confidence descending.
 * @property timestampMs Frame timestamp from the source tracking data.
 */
public data class EmotionResult(
    val emotion: Emotion,
    val confidence: Float,
    val scores: Map<Emotion, Float>,
    val timestampMs: Long,
)
