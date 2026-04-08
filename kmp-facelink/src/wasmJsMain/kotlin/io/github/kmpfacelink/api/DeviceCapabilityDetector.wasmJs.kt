package io.github.kmpfacelink.api

import io.github.kmpfacelink.model.DeviceCapabilityProfile
import io.github.kmpfacelink.model.DeviceTier
import io.github.kmpfacelink.model.NpuVendor

/**
 * Wasm / Web implementation of [detectDeviceCapabilities].
 *
 * Real WebGPU / WebNN capability probing is tracked separately under the
 * Wasm hand/body tracking issue. Until that lands we report a conservative
 * [DeviceTier.MID] profile — the MediaPipe Web SDK already targets that
 * class of device and the [io.github.kmpfacelink.model.QualityPreset.Medium]
 * preset sustains 30 FPS across the vast majority of desktop browsers.
 */
public actual fun detectDeviceCapabilities(
    @Suppress("UNUSED_PARAMETER") context: PlatformContext,
): DeviceCapabilityProfile = DeviceCapabilityProfile(
    hasGpuAcceleration = false,
    hasNpu = false,
    tier = DeviceTier.MID,
    npuVendor = NpuVendor.NONE,
)
