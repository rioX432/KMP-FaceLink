package io.github.kmpfacelink.voice.lipsync

import io.github.kmpfacelink.voice.audio.AudioData
import kotlinx.coroutines.flow.Flow

/**
 * Interface for neural/ML-based audio-to-blendshape lip sync engines.
 *
 * Extends [LipSyncEngine] with additional capabilities specific to
 * neural inference: model lifecycle management and direct audio-to-blendshape
 * prediction without intermediate phoneme/viseme steps.
 *
 * Implementations will wrap platform-specific ML runtimes:
 * - Android: LiteRT (TensorFlow Lite) / ONNX Runtime
 * - iOS: Core ML / ONNX Runtime
 *
 * The model is expected to take audio features as input and output
 * per-frame blend shape weights directly.
 */
public interface NeuralLipSyncEngine : LipSyncEngine {

    /** Whether the underlying model is loaded and ready for inference. */
    public val isModelLoaded: Boolean

    /**
     * Loads the neural model from the given path.
     *
     * @param modelPath Platform-specific path to the model file
     * @throws IllegalStateException if the model cannot be loaded
     */
    public suspend fun loadModel(modelPath: String)

    /**
     * Unloads the model and frees associated resources.
     *
     * After calling this, [isModelLoaded] returns false and
     * inference calls will fall back to amplitude-based lip sync.
     */
    public fun unloadModel()

    /**
     * Predicts blend shape weights directly from audio data using the neural model.
     *
     * This bypasses the phoneme/viseme pipeline entirely, producing
     * per-frame blend shape predictions from raw audio features.
     *
     * @param audio Raw PCM audio data
     * @param config Lip sync configuration (targetFps, smoothing)
     * @return Flow of [LipSyncFrame] with neural-predicted blend shapes
     */
    public fun predictFromAudio(
        audio: AudioData,
        config: LipSyncConfig = LipSyncConfig(),
    ): Flow<LipSyncFrame>
}
