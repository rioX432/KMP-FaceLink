package io.github.kmpfacelink.api

import io.github.kmpfacelink.model.DeviceCapabilityProfile

/**
 * Runtime detection of the current device's ML-inference capabilities.
 *
 * Returns a [DeviceCapabilityProfile] summarising GPU / NPU availability and
 * a coarse performance tier. Call once at startup and pass the result to
 * `QualityPreset.autoSelect(profile)` to pick a default tracking quality
 * tier; the result does not change during a session.
 *
 * Detection is platform-specific:
 * - **Android**: delegates to the internal `HardwareCapabilityDetector`,
 *   which spins up a throwaway EGL context to query OpenGL ES compute
 *   support, then reads the NPU vendor from `NpuVendorDetector`. Can take
 *   ~50–200 ms on first call — cache the result.
 * - **iOS**: reports GPU acceleration as `true` and `tier = HIGH`. ARKit
 *   manages its own inference runtime so no NPU or GPU probing is needed.
 * - **JVM / Desktop**: reports GPU acceleration as `false` and `tier = MID`
 *   by default. ONNX Runtime's CPU EP handles inference; MID-tier keeps the
 *   preset at [io.github.kmpfacelink.model.QualityPreset.Medium] which the
 *   OpenSeeFace models can sustain at 30 FPS on modern laptops.
 * - **Wasm / Web**: reports GPU acceleration as `false` and `tier = MID`.
 *   Real WebGPU/WebNN probing is tracked separately; until then MID is the
 *   safe default.
 */
public expect fun detectDeviceCapabilities(context: PlatformContext): DeviceCapabilityProfile
