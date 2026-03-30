package io.github.kmpfacelink

import io.github.kmpfacelink.model.HandJoint
import io.github.kmpfacelink.model.HandLandmarkPoint
import io.github.kmpfacelink.util.TemplateMatchingGestureStrategy
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class TemplateMatchingGestureStrategyTest {

    /**
     * Create a synthetic "fist" pose: all joints near wrist.
     */
    private fun createFistLandmarks(
        offsetX: Float = 0f,
        offsetY: Float = 0f,
    ): List<HandLandmarkPoint> =
        HandJoint.entries.map { joint ->
            HandLandmarkPoint(
                joint = joint,
                x = 0.5f + offsetX + joint.index * 0.005f,
                y = 0.7f + offsetY + joint.index * 0.003f,
                z = 0f,
            )
        }

    /**
     * Create a synthetic "spread" pose: fingers spread out.
     */
    private fun createSpreadLandmarks(
        offsetX: Float = 0f,
        offsetY: Float = 0f,
    ): List<HandLandmarkPoint> =
        HandJoint.entries.map { joint ->
            HandLandmarkPoint(
                joint = joint,
                x = 0.3f + offsetX + joint.index * 0.03f,
                y = 0.8f + offsetY - joint.index * 0.025f,
                z = 0f,
            )
        }

    @Test
    fun classifyReturnsNoneWhenNoTemplatesRegistered() {
        val strategy = TemplateMatchingGestureStrategy()
        val landmarks = createFistLandmarks()
        val result = strategy.classify(landmarks)
        assertEquals(0f, result.confidence)
    }

    @Test
    fun registerAndClassifyCustomGesture() {
        val strategy = TemplateMatchingGestureStrategy(confidenceThreshold = 0.8f)

        val samples = listOf(
            createFistLandmarks(offsetX = 0f, offsetY = 0f),
            createFistLandmarks(offsetX = 0.05f, offsetY = 0.02f),
            createFistLandmarks(offsetX = -0.03f, offsetY = 0.01f),
        )

        strategy.registerGesture("custom_fist", samples)

        // Classify a new fist at a different position — should match
        val testLandmarks = createFistLandmarks(offsetX = 0.1f, offsetY = -0.05f)
        val result = strategy.classify(testLandmarks)

        assertEquals("custom_fist", result.gestureName)
        assertTrue(result.confidence >= 0.8f)
    }

    @Test
    fun classifyDistinguishesDifferentGestures() {
        val strategy = TemplateMatchingGestureStrategy(confidenceThreshold = 0.7f)

        strategy.registerGesture(
            "fist",
            listOf(createFistLandmarks(), createFistLandmarks(offsetX = 0.02f)),
        )
        strategy.registerGesture(
            "spread",
            listOf(createSpreadLandmarks(), createSpreadLandmarks(offsetX = 0.02f)),
        )

        val fistResult = strategy.classify(createFistLandmarks(offsetX = 0.05f))
        assertEquals("fist", fistResult.gestureName)

        val spreadResult = strategy.classify(createSpreadLandmarks(offsetX = 0.05f))
        assertEquals("spread", spreadResult.gestureName)
    }

    @Test
    fun classifyReturnsNoneBelowThreshold() {
        val strategy = TemplateMatchingGestureStrategy(confidenceThreshold = 0.99f)

        strategy.registerGesture("fist", listOf(createFistLandmarks()))

        // A very different pose should not match at 0.99 threshold
        val different = createSpreadLandmarks()
        val result = strategy.classify(different)
        assertEquals(0f, result.confidence)
    }

    @Test
    fun unregisterGestureRemovesTemplate() {
        val strategy = TemplateMatchingGestureStrategy()
        strategy.registerGesture("fist", listOf(createFistLandmarks()))
        assertTrue(strategy.registeredGestures().contains("fist"))

        strategy.unregisterGesture("fist")
        assertTrue(strategy.registeredGestures().isEmpty())
    }

    @Test
    fun clearGesturesRemovesAll() {
        val strategy = TemplateMatchingGestureStrategy()
        strategy.registerGesture("fist", listOf(createFistLandmarks()))
        strategy.registerGesture("spread", listOf(createSpreadLandmarks()))
        assertEquals(2, strategy.registeredGestures().size)

        strategy.clearGestures()
        assertTrue(strategy.registeredGestures().isEmpty())
    }

    @Test
    fun reRegisterOverwritesExisting() {
        val strategy = TemplateMatchingGestureStrategy()
        strategy.registerGesture("fist", listOf(createFistLandmarks()))
        strategy.registerGesture("fist", listOf(createSpreadLandmarks()))
        assertEquals(1, strategy.registeredGestures().size)
    }

    @Test
    fun registerGestureRejectsBlankName() {
        val strategy = TemplateMatchingGestureStrategy()
        assertFailsWith<IllegalArgumentException> {
            strategy.registerGesture("", listOf(createFistLandmarks()))
        }
    }

    @Test
    fun registerGestureRejectsEmptySamples() {
        val strategy = TemplateMatchingGestureStrategy()
        assertFailsWith<IllegalArgumentException> {
            strategy.registerGesture("fist", emptyList())
        }
    }

    @Test
    fun invalidConfidenceThresholdThrows() {
        assertFailsWith<IllegalArgumentException> {
            TemplateMatchingGestureStrategy(confidenceThreshold = 1.5f)
        }
        assertFailsWith<IllegalArgumentException> {
            TemplateMatchingGestureStrategy(confidenceThreshold = -0.1f)
        }
    }

    @Test
    fun classifyReturnsNoneForInsufficientLandmarks() {
        val strategy = TemplateMatchingGestureStrategy()
        strategy.registerGesture("fist", listOf(createFistLandmarks()))

        val result = strategy.classify(listOf(HandLandmarkPoint(HandJoint.WRIST, 0.5f, 0.5f)))
        assertEquals(0f, result.confidence)
    }
}
