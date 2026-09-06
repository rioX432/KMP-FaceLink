package io.github.kmpfacelink

import io.github.kmpfacelink.model.CapabilityAssessment
import io.github.kmpfacelink.model.CapabilitySource
import io.github.kmpfacelink.model.CapabilityState
import io.github.kmpfacelink.model.DetectionConfig
import io.github.kmpfacelink.model.DeviceCapabilityProfile
import io.github.kmpfacelink.model.DeviceTier
import io.github.kmpfacelink.model.DeviceTierRecommendation
import io.github.kmpfacelink.model.FaceTrackerConfig
import io.github.kmpfacelink.model.NpuVendor
import io.github.kmpfacelink.model.PowerMode
import io.github.kmpfacelink.model.QualityPreset
import kotlin.test.Test
import kotlin.test.assertEquals

class QualityPresetTest {

    // ── Preset bundles (resolution + PowerMode → baseFps) ──

    @Test
    fun lowPresetHasExpectedBundle() {
        assertEquals(LOW_WIDTH, QualityPreset.Low.analysisWidth)
        assertEquals(LOW_HEIGHT, QualityPreset.Low.analysisHeight)
        assertEquals(PowerMode.LOW_POWER, QualityPreset.Low.powerMode)
        assertEquals(LOW_FPS, QualityPreset.Low.powerMode.baseFps)
    }

    @Test
    fun mediumPresetHasExpectedBundle() {
        assertEquals(MEDIUM_WIDTH, QualityPreset.Medium.analysisWidth)
        assertEquals(MEDIUM_HEIGHT, QualityPreset.Medium.analysisHeight)
        assertEquals(PowerMode.BALANCED, QualityPreset.Medium.powerMode)
        assertEquals(MEDIUM_FPS, QualityPreset.Medium.powerMode.baseFps)
    }

    @Test
    fun highPresetHasExpectedBundle() {
        assertEquals(HIGH_WIDTH, QualityPreset.High.analysisWidth)
        assertEquals(HIGH_HEIGHT, QualityPreset.High.analysisHeight)
        assertEquals(PowerMode.HIGH, QualityPreset.High.powerMode)
        assertEquals(HIGH_FPS, QualityPreset.High.powerMode.baseFps)
    }

    // ── applyTo produces a FaceTrackerConfig with the preset fields set ──

    @Test
    fun applyToOverridesPowerModeAndResolution() {
        val config = QualityPreset.Medium.applyTo()
        assertEquals(PowerMode.BALANCED, config.powerConfig.powerMode)
        assertEquals(MEDIUM_WIDTH, config.performanceConfig.analysisWidth)
        assertEquals(MEDIUM_HEIGHT, config.performanceConfig.analysisHeight)
    }

    @Test
    fun applyToPreservesNonOverriddenFields() {
        val baseFloat = 0.9f
        val base = FaceTrackerConfig(
            detectionConfig = DetectionConfig(
                maxFaces = MAX_FACES_TEST,
                minDetectionConfidence = baseFloat,
            ),
        )
        val result = QualityPreset.Low.applyTo(base)
        assertEquals(MAX_FACES_TEST, result.detectionConfig.maxFaces)
        assertEquals(baseFloat, result.detectionConfig.minDetectionConfidence)
        assertEquals(LOW_WIDTH, result.performanceConfig.analysisWidth)
        assertEquals(PowerMode.LOW_POWER, result.powerConfig.powerMode)
    }

    // ── autoSelect rules ──

    @Test
    fun autoSelectReturnsHighWhenNpuPresent() {
        val profile = probedProfile(
            CapabilityState.AVAILABLE,
            CapabilityState.AVAILABLE,
            DeviceTier.HIGH,
            NpuVendor.QUALCOMM,
        )
        assertEquals(QualityPreset.High, QualityPreset.autoSelect(profile))
    }

    @Test
    fun autoSelectReturnsHighWhenTierIsHighEvenWithoutNpu() {
        val profile = probedProfile(
            CapabilityState.AVAILABLE,
            CapabilityState.UNAVAILABLE,
            DeviceTier.HIGH,
        )
        assertEquals(QualityPreset.High, QualityPreset.autoSelect(profile))
    }

    @Test
    fun autoSelectReturnsMediumWhenGpuAvailableButNoNpu() {
        val profile = probedProfile(
            CapabilityState.AVAILABLE,
            CapabilityState.UNAVAILABLE,
            DeviceTier.MID,
        )
        assertEquals(QualityPreset.Medium, QualityPreset.autoSelect(profile))
    }

    @Test
    fun autoSelectReturnsMediumWhenTierIsMidWithoutGpu() {
        val profile = probedProfile(
            CapabilityState.UNAVAILABLE,
            CapabilityState.UNAVAILABLE,
            DeviceTier.MID,
        )
        assertEquals(QualityPreset.Medium, QualityPreset.autoSelect(profile))
    }

    @Test
    fun autoSelectDoesNotPromoteOnAnUnprobedPath() {
        // UNKNOWN means nothing looked. Treating it as available would upgrade a device on the
        // strength of a check that never ran.
        val unknown = CapabilityAssessment(
            CapabilityState.UNKNOWN,
            CapabilitySource.ASSUMED,
            "test",
        )
        val unprobed = DeviceCapabilityProfile(
            gpuInference = unknown,
            npuInference = unknown,
            npuVendor = null,
            qualityRecommendation = DeviceTierRecommendation(
                DeviceTier.LOW,
                CapabilitySource.ASSUMED,
                "test",
            ),
        )
        assertEquals(QualityPreset.Low, QualityPreset.autoSelect(unprobed))
    }

    @Test
    fun autoSelectReturnsLowForCpuOnlyLowTierDevice() {
        val profile = probedProfile(
            CapabilityState.UNAVAILABLE,
            CapabilityState.UNAVAILABLE,
            DeviceTier.LOW,
        )
        assertEquals(QualityPreset.Low, QualityPreset.autoSelect(profile))
    }

    @Test
    fun autoSelectReturnsMediumWhenGpuAvailableButTierIsLow() {
        // GPU rule fires before the LOW-tier fallthrough — locks the
        // ordering of the autoSelect cases.
        val profile = probedProfile(
            CapabilityState.AVAILABLE,
            CapabilityState.UNAVAILABLE,
            DeviceTier.LOW,
        )
        assertEquals(QualityPreset.Medium, QualityPreset.autoSelect(profile))
    }

    @Test
    fun autoSelectNpuShortCircuitsEvenOnLowTier() {
        // An NPU-equipped device that somehow reported LOW tier should still
        // be treated as High, because the NPU is the strongest signal we have.
        val profile = probedProfile(
            CapabilityState.UNAVAILABLE,
            CapabilityState.AVAILABLE,
            DeviceTier.LOW,
            NpuVendor.MEDIATEK,
        )
        assertEquals(QualityPreset.High, QualityPreset.autoSelect(profile))
    }

    companion object {
        private const val LOW_FPS = 15
        private const val LOW_WIDTH = 320
        private const val LOW_HEIGHT = 240

        private const val MEDIUM_FPS = 30
        private const val MEDIUM_WIDTH = 480
        private const val MEDIUM_HEIGHT = 360

        private const val HIGH_FPS = 60
        private const val HIGH_WIDTH = 640
        private const val HIGH_HEIGHT = 480

        private const val MAX_FACES_TEST = 2
    }

    /** Builds a profile as a probing target would report it. */
    private fun probedProfile(
        gpu: CapabilityState,
        npu: CapabilityState,
        tier: DeviceTier,
        vendor: NpuVendor? = null,
    ) = DeviceCapabilityProfile(
        gpuInference = CapabilityAssessment(
            gpu,
            CapabilitySource.PROBED,
            "test",
        ),
        npuInference = CapabilityAssessment(
            npu,
            CapabilitySource.PROBED,
            "test",
        ),
        npuVendor = vendor,
        qualityRecommendation = DeviceTierRecommendation(
            tier,
            CapabilitySource.DERIVED,
            "test",
        ),
    )
}
