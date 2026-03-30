package io.github.kmpfacelink.model

/**
 * Inference delegate preference for ML model execution.
 *
 * Controls which hardware accelerator the tracking model uses for inference.
 * The actual delegate used may differ from the requested one if the device
 * does not support it — see [AUTO] for automatic fallback behavior.
 *
 * Android (MediaPipe): All options are supported. GPU requires OpenGL ES 3.1+.
 *   NNAPI delegates to the Android Neural Networks API (deprecated in Android 15,
 *   but still functional on older devices and useful as a bridge to NPU acceleration).
 * iOS (ARKit/Vision): This setting has no effect — Apple frameworks manage
 *   hardware acceleration internally via CoreML and the Apple Neural Engine.
 */
public enum class InferenceDelegate {
    /**
     * Automatic delegate selection with fallback chain: GPU → CPU.
     *
     * This is the default and matches the existing behavior.
     * On Android, the tracker first attempts GPU inference; if the GPU delegate
     * is unavailable (e.g. missing OpenGL ES 3.1+), it transparently falls back to CPU.
     */
    AUTO,

    /** Force CPU-only inference. Universally supported but slowest. */
    CPU,

    /**
     * Prefer GPU inference via OpenGL ES.
     *
     * Falls back to CPU if the GPU delegate is unavailable.
     * On most modern Android devices this provides a good balance of speed and compatibility.
     */
    GPU,

    /**
     * Prefer Android NNAPI delegate.
     *
     * NNAPI can route inference to a device's NPU/DSP when a vendor driver is available,
     * providing significant performance gains. Falls back to GPU → CPU if NNAPI is
     * unavailable or fails to initialize.
     *
     * Note: NNAPI is deprecated starting in Android 15 (API 35). For future NPU access,
     * direct LiteRT vendor delegates will be needed (tracked separately).
     * This option has no effect on iOS.
     */
    NNAPI,
}
