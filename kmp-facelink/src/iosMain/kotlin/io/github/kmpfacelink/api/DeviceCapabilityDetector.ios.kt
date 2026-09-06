package io.github.kmpfacelink.api

import io.github.kmpfacelink.model.CapabilityAssessment
import io.github.kmpfacelink.model.CapabilitySource
import io.github.kmpfacelink.model.CapabilityState
import io.github.kmpfacelink.model.DeviceCapabilityProfile
import io.github.kmpfacelink.model.DeviceTier
import io.github.kmpfacelink.model.DeviceTierRecommendation

/**
 * iOS assessment of ML-inference capabilities.
 *
 * Nothing here queries the device. ARKit owns its vision pipeline and is not asked what hardware it
 * runs on, so every value below follows from the platform rather than from this device — which is
 * why each one is reported as declared rather than probed.
 */
public actual fun assessDeviceCapabilities(
    @Suppress("UNUSED_PARAMETER") context: PlatformContext,
): DeviceCapabilityProfile = DeviceCapabilityProfile(
    gpuInference = CapabilityAssessment(
        state = CapabilityState.AVAILABLE,
        source = CapabilitySource.PLATFORM_DECLARED,
        basis = "ARKit runs its own accelerated vision pipeline on every supported device",
    ),
    npuInference = CapabilityAssessment(
        state = CapabilityState.NOT_APPLICABLE,
        source = CapabilitySource.PLATFORM_DECLARED,
        basis = "the Neural Engine is consumed inside ARKit, not selected as a separate path",
    ),
    npuVendor = null,
    qualityRecommendation = DeviceTierRecommendation(
        tier = DeviceTier.HIGH,
        source = CapabilitySource.PLATFORM_DECLARED,
        basis = "ARKit face tracking requires hardware that sustains the high preset",
    ),
)
