package io.github.kmpfacelink.model

/**
 * NPU vendor identified on the current device.
 *
 * Used to select the appropriate LiteRT vendor delegate for NPU acceleration.
 * Detection is performed at runtime based on SoC identifiers.
 *
 * - [QUALCOMM]: Qualcomm QNN / AI Engine Direct (Snapdragon 8 Gen 1+, 7 Gen 3+)
 * - [MEDIATEK]: MediaTek NeuroPilot (Dimensity 7300+)
 * - [GOOGLE_TENSOR]: Google AICore (Tensor G3+)
 * - [NONE]: No supported NPU vendor detected, or not on Android
 */
public enum class NpuVendor {
    /** Qualcomm QNN / AI Engine Direct runtime. */
    QUALCOMM,

    /** MediaTek NeuroPilot runtime. */
    MEDIATEK,

    /** Google AICore runtime (Tensor G3, G4). */
    GOOGLE_TENSOR,

    /** No supported NPU vendor detected. */
    NONE,
}
