package io.github.kmpfacelink.api

import io.github.kmpfacelink.model.DeviceCapabilityProfile
import io.github.kmpfacelink.model.DeviceTier
import io.github.kmpfacelink.model.NpuVendor

private const val MIN_CORES_FOR_MID_TIER = 4

/**
 * JVM / Desktop implementation of [detectDeviceCapabilities].
 *
 * ONNX Runtime's CPU execution provider handles inference for the pinned
 * OpenSeeFace FP32 models bundled with the published JVM artifact. We do not
 * probe CUDA / DirectML / CoreML execution providers yet, so GPU acceleration
 * is reported as `false`.
 *
 * Tier is derived from `Runtime.availableProcessors()`: desktops with at
 * least 4 cores are treated as [DeviceTier.MID], while smaller VMs fall back
 * to [DeviceTier.LOW]. This is a scheduling preset, not a measured FPS claim.
 */
public actual fun detectDeviceCapabilities(
    @Suppress("UNUSED_PARAMETER") context: PlatformContext,
): DeviceCapabilityProfile {
    val cores = Runtime.getRuntime().availableProcessors()
    val tier = if (cores >= MIN_CORES_FOR_MID_TIER) DeviceTier.MID else DeviceTier.LOW
    return DeviceCapabilityProfile(
        hasGpuAcceleration = false,
        hasNpu = false,
        tier = tier,
        npuVendor = NpuVendor.NONE,
    )
}
