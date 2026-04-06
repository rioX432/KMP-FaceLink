package io.github.kmpfacelink.voice.lipsync

/**
 * Configuration for neural lip sync model inference.
 *
 * @property modelPath Platform-specific path to the model file (TFLite on Android, CoreML on iOS)
 * @property useGpuDelegate Whether to attempt GPU-accelerated inference
 * @property numThreads Number of CPU threads for inference (ignored when GPU delegate is active)
 * @property inputWindowMs Audio window size in milliseconds for each inference call
 * @property outputBlendShapeCount Number of blend shape outputs from the model
 */
public data class NeuralLipSyncConfig(
    val modelPath: String,
    val useGpuDelegate: Boolean = false,
    val numThreads: Int = DEFAULT_NUM_THREADS,
    val inputWindowMs: Long = DEFAULT_INPUT_WINDOW_MS,
    val outputBlendShapeCount: Int = DEFAULT_OUTPUT_BLEND_SHAPE_COUNT,
) {
    public companion object {
        private const val DEFAULT_NUM_THREADS = 2
        private const val DEFAULT_INPUT_WINDOW_MS = 40L
        private const val DEFAULT_OUTPUT_BLEND_SHAPE_COUNT = 15
    }
}
