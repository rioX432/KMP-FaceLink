package io.github.kmpfacelink.api

import io.github.kmpfacelink.internal.HardwareCapabilityDetector
import io.github.kmpfacelink.model.CapabilityAssessment
import io.github.kmpfacelink.model.CapabilitySource
import io.github.kmpfacelink.model.CapabilityState
import io.github.kmpfacelink.model.DeviceCapabilityProfile
import io.github.kmpfacelink.model.DeviceTier
import io.github.kmpfacelink.model.DeviceTierRecommendation
import io.github.kmpfacelink.model.NpuVendor
import java.util.concurrent.atomic.AtomicReference

/**
 * Process-wide cache of the first successful detection result.
 *
 * `HardwareCapabilityDetector.detect()` creates and tears down an EGL
 * context on every call, which is expensive (~50–200 ms on first call) and
 * unnecessary: device capabilities do not change during a session. We
 * memoise the first result and return it on every subsequent call, making
 * repeated invocations effectively free and preventing EGL state leaks on
 * drivers that misbehave under rapid context churn.
 */
private val cachedProfile = AtomicReference<DeviceCapabilityProfile?>(null)

/**
 * Android implementation of [assessDeviceCapabilities]. Delegates to the
 * internal [HardwareCapabilityDetector] for GPU compute probing and to
 * `NpuVendorDetector` (via [HardwareCapabilityDetector.detect]) for the
 * vendor NPU identifier. Result is cached for the lifetime of the process.
 *
 * Tier derivation:
 * - [DeviceTier.HIGH] when a supported NPU vendor is detected.
 * - [DeviceTier.MID] when GPU compute is supported but no NPU is detected.
 * - [DeviceTier.LOW] otherwise (no GPU compute path).
 */
public actual fun assessDeviceCapabilities(context: PlatformContext): DeviceCapabilityProfile {
    cachedProfile.get()?.let { return it }

    val capabilities = HardwareCapabilityDetector.detect(context.context)
    val hasNpu = capabilities.npuVendor != NpuVendor.NONE
    val tier = when {
        hasNpu -> DeviceTier.HIGH
        capabilities.gpuComputeSupported -> DeviceTier.MID
        else -> DeviceTier.LOW
    }
    val profile = DeviceCapabilityProfile(
        gpuInference = CapabilityAssessment(
            state = if (capabilities.gpuComputeSupported) {
                CapabilityState.AVAILABLE
            } else {
                CapabilityState.UNAVAILABLE
            },
            source = CapabilitySource.PROBED,
            basis = "OpenGL ES compute support queried through a throwaway EGL context",
        ),
        npuInference = CapabilityAssessment(
            state = if (hasNpu) CapabilityState.AVAILABLE else CapabilityState.UNAVAILABLE,
            source = CapabilitySource.PROBED,
            basis = "vendor NPU delegate lookup",
        ),
        npuVendor = capabilities.npuVendor.takeIf { it != NpuVendor.NONE },
        qualityRecommendation = DeviceTierRecommendation(
            tier = tier,
            source = CapabilitySource.DERIVED,
            basis = "derived from the probed GPU compute and NPU results",
        ),
    )
    // Tolerate benign races: if two threads detect concurrently the first
    // completed publish wins and the second call's probing is discarded.
    cachedProfile.compareAndSet(null, profile)
    return cachedProfile.get() ?: profile
}
