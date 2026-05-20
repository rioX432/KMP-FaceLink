package io.github.kmpfacelink

import io.github.kmpfacelink.model.BodyTrackerConfig
import io.github.kmpfacelink.model.CameraFacing
import io.github.kmpfacelink.model.HandPoseRevision
import io.github.kmpfacelink.model.HandTrackerConfig
import io.github.kmpfacelink.model.PowerMode
import io.github.kmpfacelink.model.SmoothingConfig
import io.github.kmpfacelink.model.ThermalPolicy
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

/**
 * Tests for Vision tracker configuration validation and defaults.
 *
 * Tracker instantiation (createHandTracker, createBodyTracker) segfaults on
 * the simulator test runner because VNDetect*Request constructors require a
 * live Vision framework context. State transition tests live in
 * [IosTrackerLifecycleTest] and run as part of the full test suite.
 *
 * This class focuses on config data class validation, which is pure Kotlin
 * and does not touch Vision framework internals.
 */
class VisionTrackerStateTest {

    // -- HandTrackerConfig validation --

    @Test
    fun handConfigDefaultValues() {
        val config = HandTrackerConfig()
        assertEquals(2, config.maxHands)
        assertEquals(true, config.enableGestureRecognition)
        assertEquals(CameraFacing.FRONT, config.cameraFacing)
        assertEquals(HandPoseRevision.AUTO, config.handPoseRevision)
        assertEquals(ThermalPolicy.Disabled, config.thermalPolicy)
        assertNull(config.powerMode)
    }

    @Test
    fun handConfigAcceptsMaxHandsOne() {
        val config = HandTrackerConfig(maxHands = 1)
        assertEquals(1, config.maxHands)
    }

    @Test
    fun handConfigAcceptsMaxHandsTwo() {
        val config = HandTrackerConfig(maxHands = 2)
        assertEquals(2, config.maxHands)
    }

    @Test
    fun handConfigRejectsMaxHandsZero() {
        assertFailsWith<IllegalArgumentException> {
            HandTrackerConfig(maxHands = 0)
        }
    }

    @Test
    fun handConfigRejectsMaxHandsThree() {
        assertFailsWith<IllegalArgumentException> {
            HandTrackerConfig(maxHands = 3)
        }
    }

    @Test
    fun handConfigRejectsNegativeDetectionConfidence() {
        assertFailsWith<IllegalArgumentException> {
            HandTrackerConfig(minDetectionConfidence = -0.1f)
        }
    }

    @Test
    fun handConfigRejectsDetectionConfidenceAboveOne() {
        assertFailsWith<IllegalArgumentException> {
            HandTrackerConfig(minDetectionConfidence = 1.1f)
        }
    }

    @Test
    fun handConfigRejectsNegativeTrackingConfidence() {
        assertFailsWith<IllegalArgumentException> {
            HandTrackerConfig(minTrackingConfidence = -0.01f)
        }
    }

    @Test
    fun handConfigRejectsNegativePresenceConfidence() {
        assertFailsWith<IllegalArgumentException> {
            HandTrackerConfig(minPresenceConfidence = -0.5f)
        }
    }

    @Test
    fun handConfigAcceptsBoundaryConfidenceValues() {
        // 0.0 and 1.0 are valid boundary values
        val configZero = HandTrackerConfig(
            minDetectionConfidence = 0f,
            minTrackingConfidence = 0f,
            minPresenceConfidence = 0f,
        )
        assertEquals(0f, configZero.minDetectionConfidence)

        val configOne = HandTrackerConfig(
            minDetectionConfidence = 1f,
            minTrackingConfidence = 1f,
            minPresenceConfidence = 1f,
        )
        assertEquals(1f, configOne.minDetectionConfidence)
    }

    @Test
    fun handConfigRejectsZeroAnalysisWidth() {
        assertFailsWith<IllegalArgumentException> {
            HandTrackerConfig(analysisWidth = 0)
        }
    }

    @Test
    fun handConfigRejectsNegativeAnalysisHeight() {
        assertFailsWith<IllegalArgumentException> {
            HandTrackerConfig(analysisHeight = -1)
        }
    }

    @Test
    fun handConfigWithAllCustomValues() {
        val config = HandTrackerConfig(
            smoothingConfig = SmoothingConfig.None,
            maxHands = 1,
            enableGestureRecognition = false,
            cameraFacing = CameraFacing.BACK,
            minDetectionConfidence = 0.8f,
            minTrackingConfidence = 0.7f,
            minPresenceConfidence = 0.6f,
            thermalPolicy = ThermalPolicy.Adaptive(),
            powerMode = PowerMode.BALANCED,
            handPoseRevision = HandPoseRevision.V1,
        )
        assertEquals(SmoothingConfig.None, config.smoothingConfig)
        assertEquals(1, config.maxHands)
        assertEquals(false, config.enableGestureRecognition)
        assertEquals(CameraFacing.BACK, config.cameraFacing)
        assertEquals(0.8f, config.minDetectionConfidence)
        assertEquals(HandPoseRevision.V1, config.handPoseRevision)
        assertEquals(PowerMode.BALANCED, config.powerMode)
    }

    // -- HandPoseRevision enum --

    @Test
    fun handPoseRevisionHasThreeValues() {
        assertEquals(3, HandPoseRevision.entries.size)
    }

    @Test
    fun handPoseRevisionContainsExpectedValues() {
        val names = HandPoseRevision.entries.map { it.name }.toSet()
        assertEquals(setOf("AUTO", "V1", "V2"), names)
    }

    // -- BodyTrackerConfig validation --

    @Test
    fun bodyConfigDefaultValues() {
        val config = BodyTrackerConfig()
        assertEquals(1, config.maxBodies)
        assertEquals(CameraFacing.FRONT, config.cameraFacing)
        assertEquals(false, config.enable3D)
        assertEquals(ThermalPolicy.Disabled, config.thermalPolicy)
        assertNull(config.powerMode)
    }

    @Test
    fun bodyConfigAcceptsMaxBodiesRange() {
        // 1 to 3 are valid
        for (n in 1..3) {
            val config = BodyTrackerConfig(maxBodies = n)
            assertEquals(n, config.maxBodies)
        }
    }

    @Test
    fun bodyConfigRejectsMaxBodiesZero() {
        assertFailsWith<IllegalArgumentException> {
            BodyTrackerConfig(maxBodies = 0)
        }
    }

    @Test
    fun bodyConfigRejectsMaxBodiesFour() {
        assertFailsWith<IllegalArgumentException> {
            BodyTrackerConfig(maxBodies = 4)
        }
    }

    @Test
    fun bodyConfigRejectsNegativeDetectionConfidence() {
        assertFailsWith<IllegalArgumentException> {
            BodyTrackerConfig(minDetectionConfidence = -0.1f)
        }
    }

    @Test
    fun bodyConfigRejectsDetectionConfidenceAboveOne() {
        assertFailsWith<IllegalArgumentException> {
            BodyTrackerConfig(minDetectionConfidence = 1.1f)
        }
    }

    @Test
    fun bodyConfigWith3DEnabled() {
        val config = BodyTrackerConfig(enable3D = true)
        assertEquals(true, config.enable3D)
    }

    @Test
    fun bodyConfigWithAllCustomValues() {
        val config = BodyTrackerConfig(
            smoothingConfig = SmoothingConfig.None,
            maxBodies = 2,
            cameraFacing = CameraFacing.BACK,
            enable3D = true,
            powerMode = PowerMode.LOW_POWER,
        )
        assertEquals(SmoothingConfig.None, config.smoothingConfig)
        assertEquals(2, config.maxBodies)
        assertEquals(CameraFacing.BACK, config.cameraFacing)
        assertEquals(true, config.enable3D)
        assertEquals(PowerMode.LOW_POWER, config.powerMode)
    }

    // -- ThermalPolicy --

    @Test
    fun thermalPolicyAdaptiveDefaultValues() {
        val policy = ThermalPolicy.Adaptive()
        assertEquals(25, policy.fairTargetFps)
        assertEquals(20, policy.seriousTargetFps)
        assertEquals(15, policy.criticalTargetFps)
        assertEquals(true, policy.disableEnhancerOnSerious)
    }

    @Test
    fun thermalPolicyAdaptiveRejectsZeroFps() {
        assertFailsWith<IllegalArgumentException> {
            ThermalPolicy.Adaptive(fairTargetFps = 0)
        }
    }

    @Test
    fun thermalPolicyAdaptiveCustomValues() {
        val policy = ThermalPolicy.Adaptive(
            fairTargetFps = 20,
            seriousTargetFps = 15,
            criticalTargetFps = 10,
            disableEnhancerOnSerious = false,
        )
        assertEquals(20, policy.fairTargetFps)
        assertEquals(15, policy.seriousTargetFps)
        assertEquals(10, policy.criticalTargetFps)
        assertEquals(false, policy.disableEnhancerOnSerious)
    }

    // -- PowerMode --

    @Test
    fun powerModeHasThreeValues() {
        assertEquals(3, PowerMode.entries.size)
    }

    @Test
    fun powerModeBaseFpsValues() {
        assertEquals(60, PowerMode.HIGH.baseFps)
        assertEquals(30, PowerMode.BALANCED.baseFps)
        assertEquals(15, PowerMode.LOW_POWER.baseFps)
    }

    @Test
    fun powerModeFpsDescendingOrder() {
        // HIGH > BALANCED > LOW_POWER
        val fpsList = PowerMode.entries.map { it.baseFps }
        assertEquals(fpsList, fpsList.sortedDescending())
    }

    // -- CameraFacing --

    @Test
    fun cameraFacingHasTwoValues() {
        assertEquals(2, CameraFacing.entries.size)
    }

    @Test
    fun cameraFacingContainsFrontAndBack() {
        val names = CameraFacing.entries.map { it.name }.toSet()
        assertEquals(setOf("FRONT", "BACK"), names)
    }
}
