package io.github.kmpfacelink.pipeline.expression

import io.github.kmpfacelink.actions.emotion.Emotion

private const val EXCLAMATION_INTENSITY_BOOST = 0.1f
private const val BASE_INTENSITY = 0.5f
private const val MAX_INTENSITY = 1.0f
private const val MIN_INTENSITY = 0.0f

/**
 * Analyzes text to determine emotional sentiment using keyword-based heuristics.
 *
 * Scans the input text for emotion-associated keywords and emoji,
 * then computes an intensity score based on keyword density and punctuation emphasis.
 * Returns [Emotion.NEUTRAL] when no keywords match.
 */
public class TextSentimentAnalyzer {

    /**
     * Analyzes the given text and returns the dominant sentiment.
     *
     * @param text Input text to analyze.
     * @return Sentiment result with the detected emotion and intensity.
     */
    public fun analyze(text: String): SentimentResult {
        if (text.isBlank()) {
            return SentimentResult(emotion = Emotion.NEUTRAL, intensity = MIN_INTENSITY)
        }

        val lowerText = text.lowercase()
        val emotionCounts = countEmotionKeywords(lowerText)
        val dominant = emotionCounts.maxByOrNull { it.value }

        if (dominant == null || dominant.value == 0) {
            return SentimentResult(emotion = Emotion.NEUTRAL, intensity = MIN_INTENSITY)
        }

        val intensity = computeIntensity(
            matchCount = dominant.value,
            textLength = lowerText.length,
            exclamationCount = text.count { it == '!' },
        )

        return SentimentResult(emotion = dominant.key, intensity = intensity)
    }

    private fun countEmotionKeywords(lowerText: String): Map<Emotion, Int> {
        val counts = mutableMapOf<Emotion, Int>()
        for ((emotion, keywords) in EMOTION_KEYWORDS) {
            val count = keywords.count { keyword -> lowerText.contains(keyword) }
            if (count > 0) {
                counts[emotion] = count
            }
        }
        return counts
    }

    internal companion object {
        internal val EMOTION_KEYWORDS: Map<Emotion, List<String>> = mapOf(
            Emotion.HAPPY to listOf(
                "happy", "glad", "love", "great", "wonderful", "haha", "lol",
                "\uD83D\uDE0A", "exciting",
            ),
            Emotion.SAD to listOf(
                "sad", "sorry", "miss", "unfortunately", "disappointed", "\uD83D\uDE22",
            ),
            Emotion.ANGRY to listOf(
                "angry", "furious", "hate", "annoying", "frustrated",
            ),
            Emotion.SURPRISED to listOf(
                "wow", "amazing", "incredible", "no way", "!?",
            ),
            Emotion.FEAR to listOf(
                "scared", "afraid", "terrified", "worried",
            ),
        )

        internal fun computeIntensity(
            matchCount: Int,
            textLength: Int,
            exclamationCount: Int,
        ): Float {
            val densityBoost = if (textLength > 0) {
                (matchCount.toFloat() / textLength) * textLength.coerceAtMost(100)
            } else {
                MIN_INTENSITY
            }
            val exclamationBoost = (exclamationCount * EXCLAMATION_INTENSITY_BOOST)
                .coerceAtMost(BASE_INTENSITY)
            return (BASE_INTENSITY + densityBoost + exclamationBoost)
                .coerceIn(MIN_INTENSITY, MAX_INTENSITY)
        }
    }
}
