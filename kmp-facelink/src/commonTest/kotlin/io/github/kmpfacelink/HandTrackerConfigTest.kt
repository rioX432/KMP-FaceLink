package io.github.kmpfacelink

import io.github.kmpfacelink.model.CameraFacing
import io.github.kmpfacelink.model.HandTrackerConfig
import io.github.kmpfacelink.model.SmoothingConfig
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail

class HandTrackerConfigTest {

    @Test
    fun defaultValuesAreCorrect() {
        val config = HandTrackerConfig()
        assertTrue(config.smoothingConfig is SmoothingConfig.Ema)
        assertEquals(2, config.maxHands)
        assertTrue(config.enableGestureRecognition)
        assertEquals(CameraFacing.FRONT, config.cameraFacing)
    }

    @Test
    fun maxHandsOneIsValid() {
        val config = HandTrackerConfig(maxHands = 1)
        assertEquals(1, config.maxHands)
    }

    @Test
    fun maxHandsTwoIsValid() {
        val config = HandTrackerConfig(maxHands = 2)
        assertEquals(2, config.maxHands)
    }

    @Test
    fun maxHandsZeroIsRejected() {
        try {
            HandTrackerConfig(maxHands = 0)
            fail("Expected IllegalArgumentException")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message!!.contains("maxHands"))
        }
    }

    @Test
    fun maxHandsThreeIsRejected() {
        try {
            HandTrackerConfig(maxHands = 3)
            fail("Expected IllegalArgumentException")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message!!.contains("maxHands"))
        }
    }

    @Test
    fun gestureRecognitionCanBeDisabled() {
        val config = HandTrackerConfig(enableGestureRecognition = false)
        assertEquals(false, config.enableGestureRecognition)
    }

    @Test
    fun backCameraAssigned() {
        val config = HandTrackerConfig(cameraFacing = CameraFacing.BACK)
        assertEquals(CameraFacing.BACK, config.cameraFacing)
    }

    @Test
    fun customSmoothingNoneAssigned() {
        val config = HandTrackerConfig(smoothingConfig = SmoothingConfig.None)
        assertTrue(config.smoothingConfig is SmoothingConfig.None)
    }

    @Test
    fun copyModifiesSelectedFields() {
        val original = HandTrackerConfig()
        val copy = original.copy(maxHands = 1, enableGestureRecognition = false)
        assertEquals(1, copy.maxHands)
        assertEquals(false, copy.enableGestureRecognition)
        assertEquals(original.cameraFacing, copy.cameraFacing)
    }
}
