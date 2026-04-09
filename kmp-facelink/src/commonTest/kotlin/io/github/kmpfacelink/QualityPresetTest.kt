package io.github.kmpfacelink

import io.github.kmpfacelink.model.DetectionConfig
import io.github.kmpfacelink.model.DeviceCapabilityProfile
import io.github.kmpfacelink.model.DeviceTier
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
        assertEquals(PowerMode.BALANCED, config.powerMode)
        assertEquals(MEDIUM_WIDTH, config.analysisWidth)
        assertEquals(MEDIUM_HEIGHT, config.analysisHeight)
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
        assertEquals(MAX_FACES_TEST, result.maxFaces)
        assertEquals(baseFloat, result.minDetectionConfidence)
        assertEquals(LOW_WIDTH, result.analysisWidth)
        assertEquals(PowerMode.LOW_POWER, result.powerMode)
    }

    // ── autoSelect rules ──

    @Test
    fun autoSelectReturnsHighWhenNpuPresent() {
        val profile = DeviceCapabilityProfile(
            hasGpuAcceleration = true,
            hasNpu = true,
            tier = DeviceTier.HIGH,
            npuVendor = NpuVendor.QUALCOMM,
        )
        assertEquals(QualityPreset.High, QualityPreset.autoSelect(profile))
    }

    @Test
    fun autoSelectReturnsHighWhenTierIsHighEvenWithoutNpu() {
        val profile = DeviceCapabilityProfile(
            hasGpuAcceleration = true,
            hasNpu = false,
            tier = DeviceTier.HIGH,
        )
        assertEquals(QualityPreset.High, QualityPreset.autoSelect(profile))
    }

    @Test
    fun autoSelectReturnsMediumWhenGpuAvailableButNoNpu() {
        val profile = DeviceCapabilityProfile(
            hasGpuAcceleration = true,
            hasNpu = false,
            tier = DeviceTier.MID,
        )
        assertEquals(QualityPreset.Medium, QualityPreset.autoSelect(profile))
    }

    @Test
    fun autoSelectReturnsMediumWhenTierIsMidWithoutGpu() {
        val profile = DeviceCapabilityProfile(
            hasGpuAcceleration = false,
            hasNpu = false,
            tier = DeviceTier.MID,
        )
        assertEquals(QualityPreset.Medium, QualityPreset.autoSelect(profile))
    }

    @Test
    fun autoSelectReturnsLowForCpuOnlyLowTierDevice() {
        val profile = DeviceCapabilityProfile(
            hasGpuAcceleration = false,
            hasNpu = false,
            tier = DeviceTier.LOW,
        )
        assertEquals(QualityPreset.Low, QualityPreset.autoSelect(profile))
    }

    @Test
    fun autoSelectReturnsMediumWhenGpuAvailableButTierIsLow() {
        // GPU rule fires before the LOW-tier fallthrough — locks the
        // ordering of the autoSelect cases.
        val profile = DeviceCapabilityProfile(
            hasGpuAcceleration = true,
            hasNpu = false,
            tier = DeviceTier.LOW,
        )
        assertEquals(QualityPreset.Medium, QualityPreset.autoSelect(profile))
    }

    @Test
    fun autoSelectNpuShortCircuitsEvenOnLowTier() {
        // An NPU-equipped device that somehow reported LOW tier should still
        // be treated as High, because the NPU is the strongest signal we have.
        val profile = DeviceCapabilityProfile(
            hasGpuAcceleration = false,
            hasNpu = true,
            tier = DeviceTier.LOW,
            npuVendor = NpuVendor.MEDIATEK,
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
}
