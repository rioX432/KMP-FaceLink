package io.github.kmpfacelink.model

/**
 * Inference delegate preference for ML model execution.
 *
 * Controls which hardware accelerator the tracking model uses for inference.
 * The actual delegate used may differ from the requested one if the device
 * does not support it — see [AUTO] for automatic fallback behavior.
 *
 * Android (MediaPipe): All options are supported. GPU requires OpenGL ES 3.1+.
 *   NPU delegates to vendor-specific accelerators (Qualcomm QNN, MediaTek NeuroPilot)
 *   via LiteRT vendor delegates. Falls back to GPU → CPU when unavailable.
 * iOS (ARKit/Vision): This setting has no effect — Apple frameworks manage
 *   hardware acceleration internally via CoreML and the Apple Neural Engine.
 * Wasm/Web (MediaPipe JS): [AUTO] and [GPU] probe for WebGPU (Chrome 113+,
 *   Safari 26+, Firefox 141+) then WebGL 2.0, passing the "GPU" delegate to
 *   MediaPipe Tasks Vision. Falls back to CPU when no GPU backend is available.
 *   [CPU] forces CPU-only inference. [NPU] behaves like [AUTO].
 */
public enum class InferenceDelegate {
    /**
     * Automatic delegate selection (GPU �� CPU).
     *
     * This is the default. Uses GPU with CPU fallback for maximum compatibility.
     * NPU is NOT auto-activated — use [NPU] explicitly to opt in to hardware
     * neural acceleration.
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
     * Prefer NPU inference via LiteRT vendor delegates.
     *
     * Routes inference to the device's Neural Processing Unit when a supported vendor
     * runtime is available:
     * - **Qualcomm**: QNN / AI Engine Direct (Snapdragon 8 Gen 1+, 7 Gen 3+)
     * - **MediaTek**: NeuroPilot (Dimensity 7300+)
     * - **Google Tensor**: AICore (Tensor G3+)
     *
     * Falls back to GPU → CPU when the NPU delegate is unavailable or fails to
     * initialize. NPU inference provides significant power efficiency gains (3-5x
     * over GPU), making it ideal for long tracking sessions.
     *
     * Note: NPU delegate support in MediaPipe Tasks is currently in progress.
     * Until fully available, this option falls back to GPU → CPU.
     * This option has no effect on iOS.
     */
    NPU,
}
