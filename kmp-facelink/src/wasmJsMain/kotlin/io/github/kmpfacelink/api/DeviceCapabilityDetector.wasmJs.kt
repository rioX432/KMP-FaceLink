package io.github.kmpfacelink.api

import io.github.kmpfacelink.model.CapabilityAssessment
import io.github.kmpfacelink.model.CapabilitySource
import io.github.kmpfacelink.model.CapabilityState
import io.github.kmpfacelink.model.DeviceCapabilityProfile
import io.github.kmpfacelink.model.DeviceTier
import io.github.kmpfacelink.model.DeviceTierRecommendation

/**
 * Wasm / Web assessment of ML-inference capabilities.
 *
 * Nothing here inspects the browser. WebGPU and WebNN probing is tracked separately, so every value
 * below is an assumption about the class of device the MediaPipe Web SDK targets, not a statement
 * about the machine actually running this code.
 */
public actual fun assessDeviceCapabilities(
    @Suppress("UNUSED_PARAMETER") context: PlatformContext,
): DeviceCapabilityProfile = DeviceCapabilityProfile(
    gpuInference = CapabilityAssessment(
        state = CapabilityState.UNKNOWN,
        source = CapabilitySource.ASSUMED,
        basis = "WebGPU availability is not probed",
    ),
    npuInference = CapabilityAssessment(
        state = CapabilityState.UNKNOWN,
        source = CapabilitySource.ASSUMED,
        basis = "WebNN availability is not probed",
    ),
    npuVendor = null,
    qualityRecommendation = DeviceTierRecommendation(
        tier = DeviceTier.MID,
        source = CapabilitySource.ASSUMED,
        basis = "the MediaPipe Web SDK targets this class of device; not measured here",
    ),
)
