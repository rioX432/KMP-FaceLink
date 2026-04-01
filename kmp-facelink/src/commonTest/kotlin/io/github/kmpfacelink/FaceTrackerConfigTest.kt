package io.github.kmpfacelink

import io.github.kmpfacelink.model.CalibrationConfig
import io.github.kmpfacelink.model.CameraFacing
import io.github.kmpfacelink.model.FaceTrackerConfig
import io.github.kmpfacelink.model.PowerMode
import io.github.kmpfacelink.model.SmoothingConfig
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.fail

class FaceTrackerConfigTest {

    @Test
    fun defaultValuesAreCorrect() {
        val config = FaceTrackerConfig()
        assertNotNull(config.smoothingConfig)
        assertTrue(config.smoothingConfig is SmoothingConfig.Ema)
        assertFalse(config.calibrationConfig.enabled)
        assertEquals(CameraFacing.FRONT, config.cameraFacing)
        assertEquals("models/face_landmarker_v2_with_blendshapes.task", config.modelAssetPath)
        assertEquals(100L, config.stopDrainDelayMs)
        assertEquals(500L, config.releaseTimeoutMs)
    }

    @Test
    fun customSmoothingNoneAssigned() {
        val config = FaceTrackerConfig(smoothingConfig = SmoothingConfig.None)
        assertTrue(config.smoothingConfig is SmoothingConfig.None)
    }

    @Test
    fun customSmoothingOneEuroAssigned() {
        val config = FaceTrackerConfig(
            smoothingConfig = SmoothingConfig.OneEuro(minCutoff = 0.5f, beta = 0.01f),
        )
        val oe = config.smoothingConfig as SmoothingConfig.OneEuro
        assertEquals(0.5f, oe.minCutoff)
        assertEquals(0.01f, oe.beta)
    }

    @Test
    fun backCameraFacingAssigned() {
        val config = FaceTrackerConfig(cameraFacing = CameraFacing.BACK)
        assertEquals(CameraFacing.BACK, config.cameraFacing)
    }

    @Test
    fun calibrationCanBeEnabled() {
        val config = FaceTrackerConfig(calibrationConfig = CalibrationConfig(enabled = true))
        assertTrue(config.calibrationConfig.enabled)
    }

    @Test
    fun calibrationConfigDefaultValues() {
        val calibrationConfig = CalibrationConfig()
        assertFalse(calibrationConfig.enabled)
        assertEquals(60, calibrationConfig.warmupFrames)
        assertEquals(0.001f, calibrationConfig.rangeDecayRate)
    }

    @Test
    fun copyModifiesSelectedFields() {
        val original = FaceTrackerConfig()
        val copy = original.copy(
            calibrationConfig = CalibrationConfig(enabled = true),
            cameraFacing = CameraFacing.BACK,
        )
        assertTrue(copy.calibrationConfig.enabled)
        assertEquals(CameraFacing.BACK, copy.cameraFacing)
        assertEquals(original.modelAssetPath, copy.modelAssetPath)
    }

    @Test
    fun maxFacesDefaultsToOne() {
        val config = FaceTrackerConfig()
        assertEquals(1, config.maxFaces)
    }

    @Test
    fun maxFacesAcceptsValidRange() {
        for (n in 1..FaceTrackerConfig.MAX_FACES) {
            val config = FaceTrackerConfig(maxFaces = n)
            assertEquals(n, config.maxFaces)
        }
    }

    @Test
    fun maxFacesRejectsZero() {
        try {
            FaceTrackerConfig(maxFaces = 0)
            fail("Expected IllegalArgumentException for maxFaces = 0")
        } catch (e: IllegalArgumentException) {
            assertTrue(checkNotNull(e.message).contains("maxFaces"))
        }
    }

    @Test
    fun maxFacesRejectsValueAboveMax() {
        try {
            FaceTrackerConfig(maxFaces = FaceTrackerConfig.MAX_FACES + 1)
            fail("Expected IllegalArgumentException for maxFaces > MAX_FACES")
        } catch (e: IllegalArgumentException) {
            assertTrue(checkNotNull(e.message).contains("maxFaces"))
        }
    }

    @Test
    fun maxFacesConstantIsFour() {
        assertEquals(4, FaceTrackerConfig.MAX_FACES)
    }

    @Test
    fun powerModeDefaultsToNull() {
        val config = FaceTrackerConfig()
        assertNull(config.powerMode)
    }

    @Test
    fun powerModeCanBeSet() {
        val config = FaceTrackerConfig(powerMode = PowerMode.BALANCED)
        assertEquals(PowerMode.BALANCED, config.powerMode)
    }

    @Test
    fun lowBatteryThresholdDefaults() {
        val config = FaceTrackerConfig()
        assertEquals(0.15f, config.lowBatteryThreshold)
    }

    @Test
    fun lowBatteryThresholdRejectsNegative() {
        try {
            FaceTrackerConfig(lowBatteryThreshold = -0.1f)
            fail("Expected IllegalArgumentException")
        } catch (e: IllegalArgumentException) {
            assertTrue(checkNotNull(e.message).contains("lowBatteryThreshold"))
        }
    }

    @Test
    fun lowBatteryThresholdRejectsAboveOne() {
        try {
            FaceTrackerConfig(lowBatteryThreshold = 1.1f)
            fail("Expected IllegalArgumentException")
        } catch (e: IllegalArgumentException) {
            assertTrue(checkNotNull(e.message).contains("lowBatteryThreshold"))
        }
    }

    @Test
    fun enableIdleDetectionDefaultsToTrue() {
        val config = FaceTrackerConfig()
        assertTrue(config.enableIdleDetection)
    }

    @Test
    fun enableIdleDetectionCanBeDisabled() {
        val config = FaceTrackerConfig(enableIdleDetection = false)
        assertFalse(config.enableIdleDetection)
    }
}

class SmoothingConfigTest {

    @Test
    fun emaDefaultAlphaIsHalf() {
        val ema = SmoothingConfig.Ema()
        assertEquals(0.5f, ema.alpha)
    }

    @Test
    fun emaAcceptsZeroAlpha() {
        val ema = SmoothingConfig.Ema(alpha = 0f)
        assertEquals(0f, ema.alpha)
    }

    @Test
    fun emaAcceptsOneAlpha() {
        val ema = SmoothingConfig.Ema(alpha = 1f)
        assertEquals(1f, ema.alpha)
    }

    @Test
    fun emaRejectsAlphaBelowZero() {
        try {
            SmoothingConfig.Ema(alpha = -0.1f)
            fail("Expected IllegalArgumentException")
        } catch (e: IllegalArgumentException) {
            assertTrue(checkNotNull(e.message).contains("alpha"))
        }
    }

    @Test
    fun emaRejectsAlphaAboveOne() {
        try {
            SmoothingConfig.Ema(alpha = 1.1f)
            fail("Expected IllegalArgumentException")
        } catch (e: IllegalArgumentException) {
            assertTrue(checkNotNull(e.message).contains("alpha"))
        }
    }

    @Test
    fun oneEuroDefaultValues() {
        val oe = SmoothingConfig.OneEuro()
        assertEquals(1.0f, oe.minCutoff)
        assertEquals(0.007f, oe.beta)
        assertEquals(1.0f, oe.dCutoff)
        assertEquals(0f, oe.predictionMs)
    }

    @Test
    fun oneEuroRejectsZeroMinCutoff() {
        try {
            SmoothingConfig.OneEuro(minCutoff = 0f)
            fail("Expected IllegalArgumentException")
        } catch (e: IllegalArgumentException) {
            assertTrue(checkNotNull(e.message).contains("minCutoff"))
        }
    }

    @Test
    fun oneEuroRejectsNegativeBeta() {
        try {
            SmoothingConfig.OneEuro(beta = -0.1f)
            fail("Expected IllegalArgumentException")
        } catch (e: IllegalArgumentException) {
            assertTrue(checkNotNull(e.message).contains("beta"))
        }
    }

    @Test
    fun oneEuroRejectsNegativePrediction() {
        try {
            SmoothingConfig.OneEuro(predictionMs = -1f)
            fail("Expected IllegalArgumentException")
        } catch (e: IllegalArgumentException) {
            assertTrue(checkNotNull(e.message).contains("predictionMs"))
        }
    }

    @Test
    fun oneEuroAcceptsZeroBeta() {
        val oe = SmoothingConfig.OneEuro(beta = 0f)
        assertEquals(0f, oe.beta)
    }

    @Test
    fun noneIsSingleton() {
        val a = SmoothingConfig.None
        val b = SmoothingConfig.None
        assertTrue(a === b)
    }
}

class CameraFacingTest {

    @Test
    fun hasTwoEntries() {
        assertEquals(2, CameraFacing.entries.size)
    }

    @Test
    fun frontAndBackExist() {
        assertEquals(CameraFacing.FRONT, CameraFacing.valueOf("FRONT"))
        assertEquals(CameraFacing.BACK, CameraFacing.valueOf("BACK"))
    }
}
