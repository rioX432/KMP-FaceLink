package io.github.kmpfacelink

import io.github.kmpfacelink.model.ALL_BLEND_SHAPES
import io.github.kmpfacelink.model.BlendShape
import io.github.kmpfacelink.model.CalibrationConfig
import io.github.kmpfacelink.model.CameraFacing
import io.github.kmpfacelink.model.DetectionConfig
import io.github.kmpfacelink.model.FaceTrackerConfig
import io.github.kmpfacelink.model.PerformanceConfig
import io.github.kmpfacelink.model.PowerConfig
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
            val config = FaceTrackerConfig(
                detectionConfig = DetectionConfig(maxFaces = n),
            )
            assertEquals(n, config.maxFaces)
        }
    }

    @Test
    fun maxFacesRejectsZero() {
        try {
            DetectionConfig(maxFaces = 0)
            fail("Expected IllegalArgumentException for maxFaces = 0")
        } catch (e: IllegalArgumentException) {
            assertTrue(checkNotNull(e.message).contains("maxFaces"))
        }
    }

    @Test
    fun maxFacesRejectsValueAboveMax() {
        try {
            DetectionConfig(maxFaces = FaceTrackerConfig.MAX_FACES + 1)
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
        val config = FaceTrackerConfig(
            powerConfig = PowerConfig(powerMode = PowerMode.BALANCED),
        )
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
            PowerConfig(lowBatteryThreshold = -0.1f)
            fail("Expected IllegalArgumentException")
        } catch (e: IllegalArgumentException) {
            assertTrue(checkNotNull(e.message).contains("lowBatteryThreshold"))
        }
    }

    @Test
    fun lowBatteryThresholdRejectsAboveOne() {
        try {
            PowerConfig(lowBatteryThreshold = 1.1f)
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
        val config = FaceTrackerConfig(
            powerConfig = PowerConfig(enableIdleDetection = false),
        )
        assertFalse(config.enableIdleDetection)
    }

    @Test
    fun activeBlendShapesDefaultsToAll() {
        val config = FaceTrackerConfig()
        assertEquals(ALL_BLEND_SHAPES, config.activeBlendShapes)
    }

    @Test
    fun activeBlendShapesAcceptsSubset() {
        val subset = setOf(BlendShape.EYE_BLINK_LEFT, BlendShape.JAW_OPEN)
        val config = FaceTrackerConfig(activeBlendShapes = subset)
        assertEquals(subset, config.activeBlendShapes)
    }

    @Test
    fun activeBlendShapesRejectsEmpty() {
        try {
            FaceTrackerConfig(activeBlendShapes = emptySet())
            fail("Expected IllegalArgumentException for empty activeBlendShapes")
        } catch (e: IllegalArgumentException) {
            assertTrue(checkNotNull(e.message).contains("activeBlendShapes"))
        }
    }

    @Test
    fun nestedDetectionConfigAccessedViaConvenience() {
        val detection = DetectionConfig(
            minDetectionConfidence = 0.8f,
            maxFaces = 3,
        )
        val config = FaceTrackerConfig(detectionConfig = detection)
        assertEquals(0.8f, config.minDetectionConfidence)
        assertEquals(3, config.maxFaces)
    }

    @Test
    fun nestedPowerConfigAccessedViaConvenience() {
        val power = PowerConfig(
            powerMode = PowerMode.HIGH,
            lowBatteryThreshold = 0.2f,
        )
        val config = FaceTrackerConfig(powerConfig = power)
        assertEquals(PowerMode.HIGH, config.powerMode)
        assertEquals(0.2f, config.lowBatteryThreshold)
    }

    @Test
    fun nestedPerformanceConfigAccessedViaConvenience() {
        val perf = PerformanceConfig(
            analysisWidth = 320,
            analysisHeight = 240,
            stopDrainDelayMs = 200L,
            releaseTimeoutMs = 1000L,
        )
        val config = FaceTrackerConfig(performanceConfig = perf)
        assertEquals(320, config.analysisWidth)
        assertEquals(240, config.analysisHeight)
        assertEquals(200L, config.stopDrainDelayMs)
        assertEquals(1000L, config.releaseTimeoutMs)
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

    @Test
    fun neuroPredictiveDefaultWindowSize() {
        val np = SmoothingConfig.NeuroPredictive()
        assertEquals(SmoothingConfig.NeuroPredictive.DEFAULT_NEURO_WINDOW_SIZE, np.windowSize)
    }

    @Test
    fun neuroPredictiveAcceptsCustomWindowSize() {
        val np = SmoothingConfig.NeuroPredictive(windowSize = 12)
        assertEquals(12, np.windowSize)
    }

    @Test
    fun neuroPredictiveRejectsTooSmallWindowSize() {
        try {
            SmoothingConfig.NeuroPredictive(windowSize = 1)
            fail("Expected IllegalArgumentException")
        } catch (e: IllegalArgumentException) {
            assertTrue(checkNotNull(e.message).contains("windowSize"))
        }
    }

    @Test
    fun neuroPredictiveConfigAssignedToTracker() {
        val config = FaceTrackerConfig(
            smoothingConfig = SmoothingConfig.NeuroPredictive(windowSize = 6),
        )
        val np = config.smoothingConfig as SmoothingConfig.NeuroPredictive
        assertEquals(6, np.windowSize)
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
