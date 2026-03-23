package io.github.kmpfacelink.pipeline.expression

import io.github.kmpfacelink.actions.emotion.Emotion

/**
 * Result of text sentiment analysis.
 *
 * @property emotion The detected emotion from the text.
 * @property intensity Strength of the detected emotion (0.0–1.0).
 */
public data class SentimentResult(
    val emotion: Emotion,
    val intensity: Float,
)
