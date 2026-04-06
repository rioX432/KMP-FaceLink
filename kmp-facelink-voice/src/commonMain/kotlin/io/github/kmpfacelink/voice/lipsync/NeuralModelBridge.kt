package io.github.kmpfacelink.voice.lipsync

/**
 * Platform-abstracted neural model inference bridge.
 *
 * Each platform provides its own implementation:
 * - Android: TensorFlow Lite (LiteRT) interpreter
 * - iOS: Core ML model inference
 *
 * The model takes mel spectrogram features as input and outputs
 * blend shape weights as a float array.
 *
 * Platform modules implement this interface and register it via
 * [NeuralLipSyncProvider.registerBridgeFactory].
 */
public interface NeuralModelBridge {

    /** Whether the model is currently loaded and ready for inference. */
    public val isLoaded: Boolean

    /**
     * Loads the model from the given path with the specified configuration.
     *
     * @param config Neural lip sync configuration including model path
     * @throws IllegalStateException if the model cannot be loaded
     */
    public suspend fun load(config: NeuralLipSyncConfig)

    /**
     * Runs inference on a single mel spectrogram frame.
     *
     * @param melFeatures Mel spectrogram feature vector
     * @return Predicted blend shape weights as a float array
     * @throws IllegalStateException if the model is not loaded
     */
    public fun infer(melFeatures: FloatArray): FloatArray

    /**
     * Releases the model and all associated resources.
     */
    public fun release()
}
