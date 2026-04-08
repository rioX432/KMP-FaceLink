package io.github.kmpfacelink.api

import io.github.kmpfacelink.model.DeviceCapabilityProfile
import io.github.kmpfacelink.model.DeviceTier
import io.github.kmpfacelink.model.NpuVendor

/**
 * iOS implementation of [detectDeviceCapabilities]. ARKit manages its own
 * vision pipeline internally, so we report a HIGH-tier profile with GPU
 * acceleration and no explicit NPU vendor — the Neural Engine is consumed
 * transparently by ARKit.
 */
public actual fun detectDeviceCapabilities(
    @Suppress("UNUSED_PARAMETER") context: PlatformContext,
): DeviceCapabilityProfile = DeviceCapabilityProfile(
    hasGpuAcceleration = true,
    hasNpu = false,
    tier = DeviceTier.HIGH,
    npuVendor = NpuVendor.NONE,
)
