package io.github.kmpfacelink.model

/**
 * Whether an inference path is there to be used.
 *
 * [UNKNOWN] is a real answer, not a polite [UNAVAILABLE]: a target that never probed for a GPU
 * knows nothing about one, and saying "no GPU" on its behalf is a claim nobody made.
 */
public enum class CapabilityState {
    /** The path exists and can be used. */
    AVAILABLE,

    /** The path was looked for and is not there. */
    UNAVAILABLE,

    /** Nothing here has looked. The path may or may not exist. */
    UNKNOWN,

    /** The concept does not apply on this platform, so there is nothing to look for. */
    NOT_APPLICABLE,
}

/**
 * How an assessment was arrived at.
 *
 * This is the axis that keeps "we measured the hardware" apart from "we wrote down what we expect".
 * Both produce a [CapabilityState]; only one of them is evidence. Without this distinction a
 * hardcoded constant and a live probe reach the caller as the same answer, and the caller has no
 * way to weigh them differently.
 */
public enum class CapabilitySource {
    /** Determined by querying the device at runtime. */
    PROBED,

    /** Computed from another measured signal, such as the processor count. */
    DERIVED,

    /** Follows from how the platform is built, not from anything measured on this device. */
    PLATFORM_DECLARED,

    /** A working assumption pending real probing. Carries no evidence about this device. */
    ASSUMED,
}

/**
 * One inference path, what is known about it, and how that came to be known.
 *
 * @property state What is known about the path.
 * @property source How [state] was arrived at.
 * @property basis Short human-readable note naming the probe, derivation, or assumption. Meant for
 *   logs and support, not for parsing.
 */
public data class CapabilityAssessment(
    public val state: CapabilityState,
    public val source: CapabilitySource,
    public val basis: String,
)

/**
 * A suggested starting quality tier, and how it was chosen.
 *
 * A recommendation is not a measured property of the device. Keeping it in its own type, rather
 * than as a bare tier beside the hardware assessments, stops a preset from being read back as a
 * hardware fact.
 */
public data class DeviceTierRecommendation(
    public val tier: DeviceTier,
    public val source: CapabilitySource,
    public val basis: String,
)

/**
 * What is known about this device's ML-inference hardware, and on what basis.
 *
 * Produced at runtime by `assessDeviceCapabilities(PlatformContext)` and consumed by
 * [QualityPreset.autoSelect].
 *
 * Not every target probes. Android queries the device; the others report what follows from the
 * platform or from a single derived signal. Each field carries its own [CapabilitySource] so a
 * caller can tell those apart, which a plain `Boolean` could not express.
 *
 * @property gpuInference The GPU inference path.
 * @property npuInference The vendor NPU inference path.
 * @property npuVendor The detected NPU vendor, or null when none was detected or nothing looked.
 * @property qualityRecommendation Suggested starting quality tier.
 */
public data class DeviceCapabilityProfile(
    public val gpuInference: CapabilityAssessment,
    public val npuInference: CapabilityAssessment,
    public val npuVendor: NpuVendor?,
    public val qualityRecommendation: DeviceTierRecommendation,
)

/**
 * Coarse performance tier used as a starting point for [QualityPreset.autoSelect].
 *
 * - [LOW]: older mobile chipsets, low-end Android devices, slow laptops.
 * - [MID]: typical 2021+ mobile chipsets, mid-range laptops, most browsers.
 * - [HIGH]: flagship 2022+ mobile chipsets, Apple Silicon, NPU-equipped SoCs.
 */
public enum class DeviceTier {
    LOW,
    MID,
    HIGH,
}
