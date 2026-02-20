package io.github.kmpfacelink.actions.emotion

import io.github.kmpfacelink.model.BlendShape
import io.github.kmpfacelink.model.FaceTrackingData
import io.github.kmpfacelink.model.HeadTransform
import io.github.kmpfacelink.model.emptyBlendShapeData
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class EmotionClassifierTest {

    private val classifier = EmotionClassifier()

    // --- Happy ---

    @Test
    fun happyDetectedWhenSmiling() {
        val data = faceData(
            BlendShape.MOUTH_SMILE_LEFT to 0.8f,
            BlendShape.MOUTH_SMILE_RIGHT to 0.8f,
            BlendShape.CHEEK_SQUINT_LEFT to 0.5f,
            BlendShape.CHEEK_SQUINT_RIGHT to 0.5f,
        )
        val result = classifier.classify(data)
        assertEquals(Emotion.HAPPY, result.emotion)
        assertTrue(result.confidence > 0f)
    }

    // --- Sad ---

    @Test
    fun sadDetectedWhenFrowning() {
        val data = faceData(
            BlendShape.MOUTH_FROWN_LEFT to 0.6f,
            BlendShape.MOUTH_FROWN_RIGHT to 0.6f,
            BlendShape.BROW_INNER_UP to 0.5f,
            BlendShape.MOUTH_PRESS_LEFT to 0.4f,
            BlendShape.MOUTH_PRESS_RIGHT to 0.4f,
        )
        val result = classifier.classify(data)
        assertEquals(Emotion.SAD, result.emotion)
    }

    // --- Angry ---

    @Test
    fun angryDetectedWhenBrowsDownAndSneer() {
        val data = faceData(
            BlendShape.BROW_DOWN_LEFT to 0.8f,
            BlendShape.BROW_DOWN_RIGHT to 0.8f,
            BlendShape.NOSE_SNEER_LEFT to 0.5f,
            BlendShape.NOSE_SNEER_RIGHT to 0.5f,
            BlendShape.MOUTH_PRESS_LEFT to 0.5f,
            BlendShape.MOUTH_PRESS_RIGHT to 0.5f,
        )
        val result = classifier.classify(data)
        assertEquals(Emotion.ANGRY, result.emotion)
    }

    // --- Surprised ---

    @Test
    fun surprisedDetectedWhenEyesWideAndJawOpen() {
        val data = faceData(
            BlendShape.EYE_WIDE_LEFT to 0.8f,
            BlendShape.EYE_WIDE_RIGHT to 0.8f,
            BlendShape.BROW_INNER_UP to 0.7f,
            BlendShape.BROW_OUTER_UP_LEFT to 0.6f,
            BlendShape.BROW_OUTER_UP_RIGHT to 0.6f,
            BlendShape.JAW_OPEN to 0.7f,
        )
        val result = classifier.classify(data)
        assertEquals(Emotion.SURPRISED, result.emotion)
    }

    // --- Disgusted ---

    @Test
    fun disgustedDetectedWhenNoseSneerAndLipUp() {
        val data = faceData(
            BlendShape.NOSE_SNEER_LEFT to 0.8f,
            BlendShape.NOSE_SNEER_RIGHT to 0.8f,
            BlendShape.MOUTH_UPPER_UP_LEFT to 0.6f,
            BlendShape.MOUTH_UPPER_UP_RIGHT to 0.6f,
            BlendShape.MOUTH_FROWN_LEFT to 0.4f,
            BlendShape.MOUTH_FROWN_RIGHT to 0.4f,
        )
        val result = classifier.classify(data)
        assertEquals(Emotion.DISGUSTED, result.emotion)
    }

    // --- Fear ---

    @Test
    fun fearDetectedWhenEyesWideAndMouthStretch() {
        val data = faceData(
            BlendShape.EYE_WIDE_LEFT to 0.8f,
            BlendShape.EYE_WIDE_RIGHT to 0.8f,
            BlendShape.BROW_INNER_UP to 0.7f,
            BlendShape.BROW_DOWN_LEFT to 0.4f,
            BlendShape.BROW_DOWN_RIGHT to 0.4f,
            BlendShape.MOUTH_STRETCH_LEFT to 0.6f,
            BlendShape.MOUTH_STRETCH_RIGHT to 0.6f,
        )
        val result = classifier.classify(data)
        assertEquals(Emotion.FEAR, result.emotion)
    }

    // --- Neutral ---

    @Test
    fun neutralWhenNoStrongActivation() {
        val data = faceData() // all zeros
        val result = classifier.classify(data)
        assertEquals(Emotion.NEUTRAL, result.emotion)
    }

    @Test
    fun neutralWhenNotTracking() {
        val data = FaceTrackingData.notTracking()
        val result = classifier.classify(data)
        assertEquals(Emotion.NEUTRAL, result.emotion)
        assertEquals(0f, result.confidence)
    }

    // --- Scores ---

    @Test
    fun resultContainsAllEmotionScores() {
        val data = faceData(BlendShape.MOUTH_SMILE_LEFT to 0.5f)
        val result = classifier.classify(data)
        assertEquals(Emotion.entries.size, result.scores.size)
        for (emotion in Emotion.entries) {
            assertTrue(result.scores.containsKey(emotion))
        }
    }

    @Test
    fun timestampIsPreserved() {
        val data = faceData(timestampMs = 12345L)
        val result = classifier.classify(data)
        assertEquals(12345L, result.timestampMs)
    }

    // --- Cosine Similarity ---

    @Test
    fun cosineSimilarityPerfectMatch() {
        val observed = mapOf(
            BlendShape.MOUTH_SMILE_LEFT to 0.7f,
            BlendShape.MOUTH_SMILE_RIGHT to 0.7f,
        )
        val template = mapOf(
            BlendShape.MOUTH_SMILE_LEFT to 0.7f,
            BlendShape.MOUTH_SMILE_RIGHT to 0.7f,
        )
        val similarity = EmotionClassifier.cosineSimilarity(observed, template)
        assertEquals(1f, similarity, 0.001f)
    }

    @Test
    fun cosineSimilarityOrthogonal() {
        val observed = mapOf(
            BlendShape.MOUTH_SMILE_LEFT to 1f,
            BlendShape.MOUTH_SMILE_RIGHT to 0f,
        )
        val template = mapOf(
            BlendShape.MOUTH_SMILE_LEFT to 0f,
            BlendShape.MOUTH_SMILE_RIGHT to 1f,
        )
        val similarity = EmotionClassifier.cosineSimilarity(observed, template)
        assertEquals(0f, similarity, 0.001f)
    }

    @Test
    fun cosineSimilarityEmptyTemplate() {
        val observed = mapOf(BlendShape.MOUTH_SMILE_LEFT to 0.5f)
        val similarity = EmotionClassifier.cosineSimilarity(observed, emptyMap())
        assertEquals(0f, similarity)
    }

    @Test
    fun cosineSimilarityZeroObserved() {
        val observed = emptyBlendShapeData()
        val template = mapOf(BlendShape.MOUTH_SMILE_LEFT to 0.7f)
        val similarity = EmotionClassifier.cosineSimilarity(observed, template)
        assertEquals(0f, similarity)
    }

    // --- Custom threshold ---

    @Test
    fun customNeutralThresholdAffectsClassification() {
        val highThreshold = EmotionClassifier(neutralThreshold = 0.99f)
        val data = faceData(
            BlendShape.MOUTH_SMILE_LEFT to 0.3f,
            BlendShape.MOUTH_SMILE_RIGHT to 0.3f,
        )
        val result = highThreshold.classify(data)
        assertEquals(Emotion.NEUTRAL, result.emotion)
    }

    // --- Helpers ---

    private fun faceData(
        vararg blendShapes: Pair<BlendShape, Float>,
        timestampMs: Long = 0L,
    ): FaceTrackingData {
        val data = emptyBlendShapeData().toMutableMap()
        blendShapes.forEach { (shape, value) -> data[shape] = value }
        return FaceTrackingData(
            blendShapes = data,
            headTransform = HeadTransform(),
            timestampMs = timestampMs,
            isTracking = true,
        )
    }
}
