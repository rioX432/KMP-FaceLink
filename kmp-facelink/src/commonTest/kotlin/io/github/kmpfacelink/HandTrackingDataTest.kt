package io.github.kmpfacelink

import io.github.kmpfacelink.model.HandGesture
import io.github.kmpfacelink.model.HandJoint
import io.github.kmpfacelink.model.HandLandmarkPoint
import io.github.kmpfacelink.model.HandTrackingData
import io.github.kmpfacelink.model.Handedness
import io.github.kmpfacelink.model.TrackedHand
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class HandTrackingDataTest {

    private fun makeHand(handedness: Handedness = Handedness.RIGHT): TrackedHand =
        TrackedHand(
            handedness = handedness,
            landmarks = HandJoint.entries.map { j ->
                HandLandmarkPoint(joint = j, x = 0.5f, y = 0.5f)
            },
        )

    @Test
    fun notTrackingHasCorrectDefaults() {
        val data = HandTrackingData.notTracking()
        assertTrue(data.hands.isEmpty())
        assertFalse(data.isTracking)
        assertEquals(0L, data.timestampMs)
        assertEquals(0, data.sourceImageWidth)
        assertEquals(0, data.sourceImageHeight)
    }

    @Test
    fun notTrackingPreservesTimestamp() {
        val data = HandTrackingData.notTracking(timestampMs = 9999L)
        assertEquals(9999L, data.timestampMs)
    }

    @Test
    fun constructorAssignsAllProperties() {
        val hands = listOf(makeHand(Handedness.LEFT), makeHand(Handedness.RIGHT))
        val data = HandTrackingData(
            hands = hands,
            sourceImageWidth = 1920,
            sourceImageHeight = 1080,
            timestampMs = 500L,
            isTracking = true,
        )
        assertEquals(2, data.hands.size)
        assertEquals(Handedness.LEFT, data.hands[0].handedness)
        assertEquals(Handedness.RIGHT, data.hands[1].handedness)
        assertEquals(1920, data.sourceImageWidth)
        assertEquals(1080, data.sourceImageHeight)
        assertEquals(500L, data.timestampMs)
        assertTrue(data.isTracking)
    }

    @Test
    fun singleHandTrackedCorrectly() {
        val hand = makeHand(Handedness.RIGHT).copy(
            gesture = HandGesture.THUMB_UP,
            gestureConfidence = 0.95f,
        )
        val data = HandTrackingData(
            hands = listOf(hand),
            timestampMs = 100L,
            isTracking = true,
        )
        assertEquals(1, data.hands.size)
        assertEquals(HandGesture.THUMB_UP, data.hands[0].gesture)
        assertEquals(0.95f, data.hands[0].gestureConfidence)
    }

    @Test
    fun isTrackingFalseWhenNoHands() {
        val data = HandTrackingData(
            hands = emptyList(),
            timestampMs = 0L,
            isTracking = false,
        )
        assertFalse(data.isTracking)
    }
}
