package io.github.kmpfacelink.api

import io.github.kmpfacelink.model.DeviceCapabilityProfile
import io.github.kmpfacelink.model.DeviceTier
import io.github.kmpfacelink.model.NpuVendor

private const val MIN_CORES_FOR_MID_TIER = 4

/**
 * JVM / Desktop implementation of [detectDeviceCapabilities].
 *
 * ONNX Runtime's CPU execution provider (with Arm KleidiAI auto-enabled on
 * ARM64 hosts since ORT 1.22) handles inference for the OpenSeeFace models
 * bundled with `OnnxFaceTracker`. We do not probe CUDA / DirectML / CoreML
 * execution providers yet, so GPU acceleration is reported as `false`.
 *
 * Tier is derived from `Runtime.availableProcessors()`: desktops with at
 * least 4 cores are treated as [DeviceTier.MID] (30 FPS target), smaller
 * VMs fall back to [DeviceTier.LOW].
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
