package io.github.kmpfacelink

import io.github.kmpfacelink.model.BodyTrackerConfig
import io.github.kmpfacelink.model.CameraFacing
import io.github.kmpfacelink.model.SmoothingConfig
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail

class BodyTrackerConfigTest {

    @Test
    fun defaultValuesAreCorrect() {
        val config = BodyTrackerConfig()
        assertTrue(config.smoothingConfig is SmoothingConfig.Ema)
        assertEquals(1, config.maxBodies)
        assertEquals(CameraFacing.FRONT, config.cameraFacing)
    }

    @Test
    fun maxBodiesOneIsValid() {
        val config = BodyTrackerConfig(maxBodies = 1)
        assertEquals(1, config.maxBodies)
    }

    @Test
    fun maxBodiesTwoIsValid() {
        val config = BodyTrackerConfig(maxBodies = 2)
        assertEquals(2, config.maxBodies)
    }

    @Test
    fun maxBodiesThreeIsValid() {
        val config = BodyTrackerConfig(maxBodies = 3)
        assertEquals(3, config.maxBodies)
    }

    @Test
    fun maxBodiesZeroIsRejected() {
        try {
            BodyTrackerConfig(maxBodies = 0)
            fail("Expected IllegalArgumentException")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message!!.contains("maxBodies"))
        }
    }

    @Test
    fun maxBodiesFourIsRejected() {
        try {
            BodyTrackerConfig(maxBodies = 4)
            fail("Expected IllegalArgumentException")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message!!.contains("maxBodies"))
        }
    }

    @Test
    fun backCameraAssigned() {
        val config = BodyTrackerConfig(cameraFacing = CameraFacing.BACK)
        assertEquals(CameraFacing.BACK, config.cameraFacing)
    }

    @Test
    fun customSmoothingNoneAssigned() {
        val config = BodyTrackerConfig(smoothingConfig = SmoothingConfig.None)
        assertTrue(config.smoothingConfig is SmoothingConfig.None)
    }

    @Test
    fun copyModifiesSelectedFields() {
        val original = BodyTrackerConfig()
        val copy = original.copy(maxBodies = 2, cameraFacing = CameraFacing.BACK)
        assertEquals(2, copy.maxBodies)
        assertEquals(CameraFacing.BACK, copy.cameraFacing)
        assertEquals(original.smoothingConfig, copy.smoothingConfig)
    }
}
