package io.github.kmpfacelink.model

/**
 * Platform-neutral summary of the device's ML-inference hardware.
 *
 * Produced at runtime by `detectDeviceCapabilities(PlatformContext)` and
 * consumed by [QualityPreset.autoSelect] to pick a default tracking quality
 * profile that the current hardware can actually sustain.
 *
 * @property hasGpuAcceleration Whether the device has a GPU path that MediaPipe
 *   (Android) or the platform vision pipeline (iOS/JVM) can use for inference
 *   — OpenGL ES 3.1+ compute shaders on Android, Apple Silicon GPUs on iOS,
 *   and so on. `true` does not imply that the GPU is currently selected; it
 *   only means the hardware is capable.
 * @property hasNpu Whether a vendor NPU delegate is available. Currently only
 *   Android sets this to `true` when [npuVendor] is not [NpuVendor.NONE]; iOS
 *   treats the Neural Engine as part of ARKit and does not expose it here.
 * @property tier Coarse performance tier used when finer-grained signals
 *   (GPU type, NPU vendor) are not enough to resolve a preset.
 * @property npuVendor The detected NPU vendor, if any. Defaults to
 *   [NpuVendor.NONE] on platforms that do not run vendor NPU inference.
 */
public data class DeviceCapabilityProfile(
    public val hasGpuAcceleration: Boolean,
    public val hasNpu: Boolean,
    public val tier: DeviceTier,
    public val npuVendor: NpuVendor = NpuVendor.NONE,
)

/**
 * Coarse performance tier used as a fallback when capability detection cannot
 * distinguish GPU / NPU availability (for example on JVM desktop or Wasm).
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
