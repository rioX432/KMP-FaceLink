package io.github.kmpfacelink.voice.lipsync

import io.github.kmpfacelink.voice.lipsync.internal.NeuralLipSyncEngineImpl
import io.github.kmpfacelink.voice.lipsync.internal.StubNeuralModelBridge

/**
 * Factory for creating [NeuralLipSyncEngine] instances.
 *
 * Platform modules should call [registerBridgeFactory] to provide
 * platform-specific ML runtime bridges (TFLite on Android, CoreML on iOS).
 * If no bridge factory is registered, a stub bridge is used that causes
 * graceful fallback to amplitude-based lip sync.
 *
 * Usage from platform module:
 * ```kotlin
 * // In Android initializer:
 * NeuralLipSyncProvider.registerBridgeFactory { TfLiteNeuralModelBridge(context) }
 *
 * // Then register with the engine factory:
 * LipSyncEngineFactory.registerNeuralProvider {
 *     NeuralLipSyncProvider.create(config)
 * }
 * ```
 */
public object NeuralLipSyncProvider {

    @Volatile
    private var bridgeFactory: (() -> NeuralModelBridge)? = null

    /**
     * Creates a [NeuralLipSyncEngine] with the given configuration.
     *
     * Uses the registered platform bridge factory, or a stub bridge
     * that falls back to amplitude-based lip sync.
     *
     * @param config Neural model configuration
     * @return A [NeuralLipSyncEngine] instance
     */
    public fun create(config: NeuralLipSyncConfig): NeuralLipSyncEngine {
        val bridge = bridgeFactory?.invoke() ?: StubNeuralModelBridge()
        return NeuralLipSyncEngineImpl(bridge, config)
    }

    /**
     * Registers a factory for platform-specific [NeuralModelBridge] instances.
     *
     * @param factory Function that creates a [NeuralModelBridge]
     */
    public fun registerBridgeFactory(factory: () -> NeuralModelBridge) {
        bridgeFactory = factory
    }

    /**
     * Returns whether a platform-specific bridge factory is registered.
     */
    public fun isBridgeAvailable(): Boolean = bridgeFactory != null

    /**
     * Clears the registered bridge factory. Mainly for testing.
     */
    public fun clearBridgeFactory() {
        bridgeFactory = null
    }
}
