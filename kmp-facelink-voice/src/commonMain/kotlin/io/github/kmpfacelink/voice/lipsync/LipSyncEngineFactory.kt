package io.github.kmpfacelink.voice.lipsync

import io.github.kmpfacelink.voice.lipsync.internal.DefaultLipSyncEngine
import io.github.kmpfacelink.voice.lipsync.internal.EnhancedAmplitudeLipSync

/**
 * Factory for creating [LipSyncEngine] instances.
 *
 * Selects the best available engine based on the requested [LipSyncEngineType].
 * Falls back gracefully: if [LipSyncEngineType.NEURAL] is requested but no
 * [NeuralLipSyncEngine] provider is registered, returns [LipSyncEngineType.ENHANCED_AMPLITUDE].
 */
public object LipSyncEngineFactory {

    @Volatile
    private var neuralProvider: (() -> NeuralLipSyncEngine)? = null

    /**
     * Creates a [LipSyncEngine] for the given type.
     *
     * @param type Desired engine type
     * @return A [LipSyncEngine] instance (may fall back to a simpler engine)
     */
    public fun create(type: LipSyncEngineType = LipSyncEngineType.DEFAULT): LipSyncEngine {
        val provider = neuralProvider // Snapshot volatile read
        return when (type) {
            LipSyncEngineType.DEFAULT -> DefaultLipSyncEngine()
            LipSyncEngineType.ENHANCED_AMPLITUDE -> EnhancedAmplitudeLipSync()
            LipSyncEngineType.NEURAL -> provider?.invoke() ?: EnhancedAmplitudeLipSync()
        }
    }

    /**
     * Registers a provider for [NeuralLipSyncEngine] instances.
     *
     * Platform-specific modules call this to make neural lip sync available.
     *
     * @param provider Factory function that creates a [NeuralLipSyncEngine]
     */
    public fun registerNeuralProvider(provider: () -> NeuralLipSyncEngine) {
        neuralProvider = provider
    }

    /**
     * Returns whether a neural engine provider is registered.
     */
    public fun isNeuralAvailable(): Boolean = neuralProvider != null

    /**
     * Clears the registered neural provider. Mainly for testing.
     */
    public fun clearNeuralProvider() {
        neuralProvider = null
    }
}
