package io.github.kmpfacelink

import io.github.kmpfacelink.model.Body3DConfig
import io.github.kmpfacelink.model.BodyTrackerConfig
import io.github.kmpfacelink.model.DetectionConfig
import io.github.kmpfacelink.model.FaceTrackerConfig
import io.github.kmpfacelink.model.HandTrackerConfig
import io.github.kmpfacelink.model.PerformanceConfig
import io.github.kmpfacelink.model.PowerConfig
import io.github.kmpfacelink.model.SmoothingConfig
import io.github.kmpfacelink.model.ThermalPolicy
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * Tests for config validation boundary values.
 *
 * Verifies that config classes reject invalid parameters via require() checks,
 * and accept valid boundary values without throwing.
 */
class ConfigValidationTest {

    // -- DetectionConfig.maxFaces boundaries --

    @Test
    fun detectionConfigRejectsMaxFacesZero() {
        val e = assertFailsWith<IllegalArgumentException> {
            DetectionConfig(maxFaces = 0)
        }
        assertTrue(checkNotNull(e.message).contains("maxFaces"))
    }

    @Test
    fun detectionConfigRejectsMaxFacesAboveMax() {
        val e = assertFailsWith<IllegalArgumentException> {
            DetectionConfig(maxFaces = FaceTrackerConfig.MAX_FACES + 1)
        }
        assertTrue(checkNotNull(e.message).contains("maxFaces"))
    }

    @Test
    fun detectionConfigRejectsNegativeMaxFaces() {
        val e = assertFailsWith<IllegalArgumentException> {
            DetectionConfig(maxFaces = -1)
        }
        assertTrue(checkNotNull(e.message).contains("maxFaces"))
    }

    // -- DetectionConfig confidence boundaries --

    @Test
    fun detectionConfigRejectsNegativeDetectionConfidence() {
        val e = assertFailsWith<IllegalArgumentException> {
            DetectionConfig(minDetectionConfidence = -0.1f)
        }
        assertTrue(checkNotNull(e.message).contains("minDetectionConfidence"))
    }

    @Test
    fun detectionConfigRejectsDetectionConfidenceAboveOne() {
        val e = assertFailsWith<IllegalArgumentException> {
            DetectionConfig(minDetectionConfidence = 1.1f)
        }
        assertTrue(checkNotNull(e.message).contains("minDetectionConfidence"))
    }

    @Test
    fun detectionConfigAcceptsBoundaryConfidenceValues() {
        // 0.0 and 1.0 should both be valid
        val zeroConf = DetectionConfig(
            minDetectionConfidence = 0f,
            minTrackingConfidence = 0f,
            minPresenceConfidence = 0f,
        )
        val oneConf = DetectionConfig(
            minDetectionConfidence = 1f,
            minTrackingConfidence = 1f,
            minPresenceConfidence = 1f,
        )
        assertTrue(zeroConf.minDetectionConfidence == 0f)
        assertTrue(oneConf.minDetectionConfidence == 1f)
    }

    @Test
    fun detectionConfigRejectsNegativeTrackingConfidence() {
        assertFailsWith<IllegalArgumentException> {
            DetectionConfig(minTrackingConfidence = -0.1f)
        }
    }

    @Test
    fun detectionConfigRejectsPresenceConfidenceAboveOne() {
        assertFailsWith<IllegalArgumentException> {
            DetectionConfig(minPresenceConfidence = 1.1f)
        }
    }

    // -- SmoothingConfig.Ema boundaries --

    @Test
    fun emaAcceptsBoundaryAlphaZero() {
        val ema = SmoothingConfig.Ema(alpha = 0f)
        assertTrue(ema.alpha == 0f)
    }

    @Test
    fun emaAcceptsBoundaryAlphaOne() {
        val ema = SmoothingConfig.Ema(alpha = 1f)
        assertTrue(ema.alpha == 1f)
    }

    @Test
    fun emaRejectsAlphaBelowZero() {
        assertFailsWith<IllegalArgumentException> {
            SmoothingConfig.Ema(alpha = -0.1f)
        }
    }

    @Test
    fun emaRejectsAlphaAboveOne() {
        assertFailsWith<IllegalArgumentException> {
            SmoothingConfig.Ema(alpha = 1.1f)
        }
    }

    // -- SmoothingConfig.OneEuro boundaries --

    @Test
    fun oneEuroRejectsZeroMinCutoff() {
        assertFailsWith<IllegalArgumentException> {
            SmoothingConfig.OneEuro(minCutoff = 0f)
        }
    }

    @Test
    fun oneEuroRejectsNegativeMinCutoff() {
        assertFailsWith<IllegalArgumentException> {
            SmoothingConfig.OneEuro(minCutoff = -1f)
        }
    }

    @Test
    fun oneEuroRejectsNegativeBeta() {
        assertFailsWith<IllegalArgumentException> {
            SmoothingConfig.OneEuro(beta = -0.1f)
        }
    }

    @Test
    fun oneEuroAcceptsZeroBeta() {
        val oe = SmoothingConfig.OneEuro(beta = 0f)
        assertTrue(oe.beta == 0f)
    }

    @Test
    fun oneEuroRejectsZeroDCutoff() {
        assertFailsWith<IllegalArgumentException> {
            SmoothingConfig.OneEuro(dCutoff = 0f)
        }
    }

    @Test
    fun oneEuroRejectsNegativePredictionMs() {
        assertFailsWith<IllegalArgumentException> {
            SmoothingConfig.OneEuro(predictionMs = -1f)
        }
    }

    @Test
    fun oneEuroAcceptsZeroPredictionMs() {
        val oe = SmoothingConfig.OneEuro(predictionMs = 0f)
        assertTrue(oe.predictionMs == 0f)
    }

    // -- SmoothingConfig.Kalman boundaries --

    @Test
    fun kalmanRejectsZeroProcessNoise() {
        assertFailsWith<IllegalArgumentException> {
            SmoothingConfig.Kalman(processNoise = 0f)
        }
    }

    @Test
    fun kalmanRejectsNegativeProcessNoise() {
        assertFailsWith<IllegalArgumentException> {
            SmoothingConfig.Kalman(processNoise = -0.01f)
        }
    }

    @Test
    fun kalmanRejectsZeroMeasurementNoise() {
        assertFailsWith<IllegalArgumentException> {
            SmoothingConfig.Kalman(measurementNoise = 0f)
        }
    }

    @Test
    fun kalmanRejectsNegativePredictionFrames() {
        assertFailsWith<IllegalArgumentException> {
            SmoothingConfig.Kalman(predictionFrames = -1)
        }
    }

    @Test
    fun kalmanAcceptsZeroPredictionFrames() {
        val k = SmoothingConfig.Kalman(predictionFrames = 0)
        assertTrue(k.predictionFrames == 0)
    }

    // -- SmoothingConfig.NeuroPredictive boundaries --

    @Test
    fun neuroPredictiveRejectsWindowSizeBelowMinimum() {
        assertFailsWith<IllegalArgumentException> {
            SmoothingConfig.NeuroPredictive(windowSize = 1)
        }
    }

    @Test
    fun neuroPredictiveAcceptsMinimumWindowSize() {
        val np = SmoothingConfig.NeuroPredictive(
            windowSize = SmoothingConfig.NeuroPredictive.MIN_NEURO_WINDOW_SIZE,
        )
        assertTrue(
            np.windowSize == SmoothingConfig.NeuroPredictive.MIN_NEURO_WINDOW_SIZE,
        )
    }

    // -- BodyTrackerConfig.maxBodies boundaries --

    @Test
    fun bodyTrackerRejectsMaxBodiesZero() {
        assertFailsWith<IllegalArgumentException> {
            BodyTrackerConfig(maxBodies = 0)
        }
    }

    @Test
    fun bodyTrackerRejectsMaxBodiesAboveMax() {
        assertFailsWith<IllegalArgumentException> {
            BodyTrackerConfig(maxBodies = 4)
        }
    }

    @Test
    fun bodyTrackerAcceptsBoundaryMaxBodies() {
        val config1 = BodyTrackerConfig(maxBodies = 1)
        val config3 = BodyTrackerConfig(maxBodies = 3)
        assertTrue(config1.maxBodies == 1)
        assertTrue(config3.maxBodies == 3)
    }

    // -- HandTrackerConfig boundaries --

    @Test
    fun handTrackerRejectsMaxHandsZero() {
        assertFailsWith<IllegalArgumentException> {
            HandTrackerConfig(maxHands = 0)
        }
    }

    @Test
    fun handTrackerRejectsMaxHandsAboveTwo() {
        assertFailsWith<IllegalArgumentException> {
            HandTrackerConfig(maxHands = 3)
        }
    }

    @Test
    fun handTrackerAcceptsBoundaryMaxHands() {
        val config1 = HandTrackerConfig(maxHands = 1)
        val config2 = HandTrackerConfig(maxHands = 2)
        assertTrue(config1.maxHands == 1)
        assertTrue(config2.maxHands == 2)
    }

    // -- PowerConfig boundaries --

    @Test
    fun powerConfigRejectsNegativeBatteryThreshold() {
        assertFailsWith<IllegalArgumentException> {
            PowerConfig(lowBatteryThreshold = -0.1f)
        }
    }

    @Test
    fun powerConfigRejectsBatteryThresholdAboveOne() {
        assertFailsWith<IllegalArgumentException> {
            PowerConfig(lowBatteryThreshold = 1.1f)
        }
    }

    @Test
    fun powerConfigAcceptsBoundaryBatteryThreshold() {
        val configZero = PowerConfig(lowBatteryThreshold = 0f)
        val configOne = PowerConfig(lowBatteryThreshold = 1f)
        assertTrue(configZero.lowBatteryThreshold == 0f)
        assertTrue(configOne.lowBatteryThreshold == 1f)
    }

    // -- PerformanceConfig boundaries --

    @Test
    fun performanceConfigRejectsZeroAnalysisWidth() {
        assertFailsWith<IllegalArgumentException> {
            PerformanceConfig(analysisWidth = 0)
        }
    }

    @Test
    fun performanceConfigRejectsNegativeAnalysisHeight() {
        assertFailsWith<IllegalArgumentException> {
            PerformanceConfig(analysisHeight = -1)
        }
    }

    // -- ThermalPolicy.Adaptive boundaries --

    @Test
    fun thermalPolicyRejectsZeroFairTargetFps() {
        assertFailsWith<IllegalArgumentException> {
            ThermalPolicy.Adaptive(fairTargetFps = 0)
        }
    }

    @Test
    fun thermalPolicyRejectsNegativeSeriousTargetFps() {
        assertFailsWith<IllegalArgumentException> {
            ThermalPolicy.Adaptive(seriousTargetFps = -1)
        }
    }

    @Test
    fun thermalPolicyRejectsZeroCriticalTargetFps() {
        assertFailsWith<IllegalArgumentException> {
            ThermalPolicy.Adaptive(criticalTargetFps = 0)
        }
    }

    // -- Body3DConfig boundaries --

    @Test
    fun body3DConfigRejectsZeroShoulderWidth() {
        assertFailsWith<IllegalArgumentException> {
            Body3DConfig(shoulderReferenceWidth = 0f)
        }
    }

    @Test
    fun body3DConfigRejectsNegativeShoulderWidth() {
        assertFailsWith<IllegalArgumentException> {
            Body3DConfig(shoulderReferenceWidth = -0.1f)
        }
    }

    // -- FaceTrackerConfig.activeBlendShapes --

    @Test
    fun faceTrackerConfigRejectsEmptyActiveBlendShapes() {
        assertFailsWith<IllegalArgumentException> {
            FaceTrackerConfig(activeBlendShapes = emptySet())
        }
    }
}
