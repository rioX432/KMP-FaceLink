package io.github.kmpfacelink.pipeline.expression

import io.github.kmpfacelink.actions.emotion.Emotion
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TextSentimentAnalyzerTest {

    private val analyzer = TextSentimentAnalyzer()

    @Test
    fun happyKeywordsDetected() {
        val result = analyzer.analyze("I'm so happy and glad to see you!")
        assertEquals(Emotion.HAPPY, result.emotion)
        assertTrue(result.intensity > 0f)
    }

    @Test
    fun sadKeywordsDetected() {
        val result = analyzer.analyze("I feel sad and disappointed")
        assertEquals(Emotion.SAD, result.emotion)
        assertTrue(result.intensity > 0f)
    }

    @Test
    fun angryKeywordsDetected() {
        val result = analyzer.analyze("I'm so angry and frustrated right now")
        assertEquals(Emotion.ANGRY, result.emotion)
        assertTrue(result.intensity > 0f)
    }

    @Test
    fun surprisedKeywordsDetected() {
        val result = analyzer.analyze("Wow, that's amazing!")
        assertEquals(Emotion.SURPRISED, result.emotion)
        assertTrue(result.intensity > 0f)
    }

    @Test
    fun fearKeywordsDetected() {
        val result = analyzer.analyze("I'm scared and worried about this")
        assertEquals(Emotion.FEAR, result.emotion)
        assertTrue(result.intensity > 0f)
    }

    @Test
    fun neutralForNoKeywords() {
        val result = analyzer.analyze("The weather is fine today")
        assertEquals(Emotion.NEUTRAL, result.emotion)
        assertEquals(0f, result.intensity)
    }

    @Test
    fun neutralForBlankText() {
        val result = analyzer.analyze("")
        assertEquals(Emotion.NEUTRAL, result.emotion)
        assertEquals(0f, result.intensity)
    }

    @Test
    fun exclamationMarksBoostIntensity() {
        val calm = analyzer.analyze("I'm happy")
        val excited = analyzer.analyze("I'm happy!!!")
        assertEquals(Emotion.HAPPY, calm.emotion)
        assertEquals(Emotion.HAPPY, excited.emotion)
        assertTrue(excited.intensity > calm.intensity)
    }

    @Test
    fun intensityClampedToOne() {
        val result = analyzer.analyze("happy glad love great wonderful!!!!!!!!!!")
        assertEquals(Emotion.HAPPY, result.emotion)
        assertTrue(result.intensity <= 1.0f)
    }

    @Test
    fun emojiDetected() {
        val result = analyzer.analyze("Hello \uD83D\uDE0A")
        assertEquals(Emotion.HAPPY, result.emotion)
    }

    @Test
    fun caseInsensitive() {
        val result = analyzer.analyze("I am HAPPY and GLAD")
        assertEquals(Emotion.HAPPY, result.emotion)
    }
}
