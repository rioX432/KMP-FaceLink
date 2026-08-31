package io.github.kmpfacelink.api

import io.github.kmpfacelink.model.CapabilityAssessment
import io.github.kmpfacelink.model.CapabilitySource
import io.github.kmpfacelink.model.CapabilityState
import io.github.kmpfacelink.model.DeviceCapabilityProfile
import io.github.kmpfacelink.model.DeviceTier
import io.github.kmpfacelink.model.DeviceTierRecommendation

private const val MIN_CORES_FOR_MID_TIER = 4

/**
 * JVM / Desktop assessment of ML-inference capabilities.
 *
 * ONNX Runtime's CPU execution provider handles inference for the pinned OpenSeeFace models. The
 * CUDA, DirectML and CoreML providers are never queried, so the GPU path here is unknown rather
 * than absent — reporting it as absent would assert something this code did not check.
 *
 * The tier is derived from the processor count alone. It is a scheduling starting point, not a
 * measured frame rate.
 */
public actual fun assessDeviceCapabilities(
    @Suppress("UNUSED_PARAMETER") context: PlatformContext,
): DeviceCapabilityProfile {
    val cores = Runtime.getRuntime().availableProcessors()
    val tier = if (cores >= MIN_CORES_FOR_MID_TIER) DeviceTier.MID else DeviceTier.LOW
    return DeviceCapabilityProfile(
        gpuInference = CapabilityAssessment(
            state = CapabilityState.UNKNOWN,
            source = CapabilitySource.ASSUMED,
            basis = "no ONNX Runtime GPU execution provider is queried",
        ),
        npuInference = CapabilityAssessment(
            state = CapabilityState.UNKNOWN,
            source = CapabilitySource.ASSUMED,
            basis = "no NPU execution provider is queried",
        ),
        npuVendor = null,
        qualityRecommendation = DeviceTierRecommendation(
            tier = tier,
            source = CapabilitySource.DERIVED,
            basis = "derived from $cores available processors",
        ),
    )
}
