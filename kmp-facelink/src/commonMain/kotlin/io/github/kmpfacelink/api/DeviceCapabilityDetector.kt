package io.github.kmpfacelink.api

import io.github.kmpfacelink.model.DeviceCapabilityProfile

/**
 * What is known about this device's ML-inference hardware, and on what basis.
 *
 * Named for what it does rather than for what a caller might hope it does: only Android probes the
 * device. The other targets report what follows from the platform, or derive a tier from one
 * signal. Each field of the returned profile carries its own
 * [io.github.kmpfacelink.model.CapabilitySource] so a caller can tell a measurement from a stated
 * expectation.
 *
 * Call once at startup and pass the result to `QualityPreset.autoSelect(profile)`; the result does
 * not change during a session.
 *
 * - **Android**: probes. Spins up a throwaway EGL context to query OpenGL ES compute support, then
 *   reads the NPU vendor. Can take ~50–200 ms on the first call, so the result is cached.
 * - **iOS**: declares. ARKit owns its inference runtime and is not asked what it runs on, so the
 *   GPU path follows from the platform and the Neural Engine is not a separate path to report.
 * - **JVM / Desktop**: derives a tier from the processor count. No execution provider is probed, so
 *   the GPU path is unknown rather than absent.
 * - **Wasm / Web**: assumes. WebGPU and WebNN probing is not implemented, so the tier is a starting
 *   assumption carrying no evidence about this device.
 */
public expect fun assessDeviceCapabilities(context: PlatformContext): DeviceCapabilityProfile
