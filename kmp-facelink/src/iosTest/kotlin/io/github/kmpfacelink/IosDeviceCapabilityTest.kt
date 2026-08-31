package io.github.kmpfacelink

import io.github.kmpfacelink.api.PlatformContext
import io.github.kmpfacelink.api.assessDeviceCapabilities
import io.github.kmpfacelink.model.CapabilitySource
import io.github.kmpfacelink.model.CapabilityState
import io.github.kmpfacelink.model.DeviceTier
import io.github.kmpfacelink.model.QualityPreset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * The iOS assessment reports what follows from the platform, and says so.
 *
 * These tests are as much about the source of each answer as about the answer itself: iOS never
 * queries this device, and a profile that did not say so would be indistinguishable from Android's,
 * which does.
 */
class IosDeviceCapabilityTest {

    private val context = PlatformContext()

    @Test
    fun assessmentReturnsAProfile() {
        assertNotNull(assessDeviceCapabilities(context))
    }

    @Test
    fun gpuPathIsDeclaredByThePlatformRatherThanProbed() {
        val gpu = assessDeviceCapabilities(context).gpuInference
        assertEquals(CapabilityState.AVAILABLE, gpu.state)
        assertEquals(
            CapabilitySource.PLATFORM_DECLARED,
            gpu.source,
            "nothing on iOS queries this device, so this must not claim to be a probe",
        )
    }

    @Test
    fun npuPathDoesNotApplyBecauseArkitOwnsIt() {
        val profile = assessDeviceCapabilities(context)
        assertEquals(CapabilityState.NOT_APPLICABLE, profile.npuInference.state)
        assertNull(profile.npuVendor)
    }

    @Test
    fun qualityRecommendationIsDeclaredNotMeasured() {
        val recommendation = assessDeviceCapabilities(context).qualityRecommendation
        assertEquals(DeviceTier.HIGH, recommendation.tier)
        assertEquals(CapabilitySource.PLATFORM_DECLARED, recommendation.source)
    }

    @Test
    fun autoSelectReturnsHighForIosProfile() {
        val preset = QualityPreset.autoSelect(assessDeviceCapabilities(context))
        assertEquals(QualityPreset.High, preset)
    }

    @Test
    fun highPresetAppliesCorrectPowerMode() {
        val preset = QualityPreset.autoSelect(assessDeviceCapabilities(context))
        assertNotNull(preset.applyTo().powerConfig.powerMode)
    }
}
