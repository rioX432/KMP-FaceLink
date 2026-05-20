package io.github.kmpfacelink

import io.github.kmpfacelink.api.PlatformContext
import io.github.kmpfacelink.api.detectDeviceCapabilities
import io.github.kmpfacelink.model.DeviceTier
import io.github.kmpfacelink.model.NpuVendor
import io.github.kmpfacelink.model.QualityPreset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Tests for iOS [detectDeviceCapabilities] and [QualityPreset.autoSelect]
 * behavior with the iOS device profile.
 */
class IosDeviceCapabilityTest {

    private val context = PlatformContext()

    @Test
    fun detectDeviceCapabilitiesReturnsValidProfile() {
        val profile = detectDeviceCapabilities(context)
        assertNotNull(profile)
    }

    @Test
    fun iosProfileReportsGpuAcceleration() {
        val profile = detectDeviceCapabilities(context)
        assertTrue(profile.hasGpuAcceleration)
    }

    @Test
    fun iosProfileReportsHighTier() {
        val profile = detectDeviceCapabilities(context)
        assertEquals(DeviceTier.HIGH, profile.tier)
    }

    @Test
    fun iosProfileReportsNoExplicitNpu() {
        // iOS Neural Engine is consumed transparently by ARKit,
        // so hasNpu is false and npuVendor is NONE
        val profile = detectDeviceCapabilities(context)
        assertEquals(false, profile.hasNpu)
        assertEquals(NpuVendor.NONE, profile.npuVendor)
    }

    @Test
    fun autoSelectReturnsHighForIosProfile() {
        // iOS profile has tier=HIGH, so autoSelect should pick High preset
        val profile = detectDeviceCapabilities(context)
        val preset = QualityPreset.autoSelect(profile)
        assertEquals(QualityPreset.High, preset)
    }

    @Test
    fun highPresetAppliesCorrectPowerMode() {
        val profile = detectDeviceCapabilities(context)
        val preset = QualityPreset.autoSelect(profile)
        val config = preset.applyTo()
        assertNotNull(config.powerConfig.powerMode)
    }
}
