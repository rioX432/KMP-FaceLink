package io.github.kmpfacelink.voice.lipsync.internal

import io.github.kmpfacelink.voice.lipsync.NeuralLipSyncConfig
import io.github.kmpfacelink.voice.lipsync.NeuralModelBridge

/**
 * Stub [NeuralModelBridge] that always reports the model as not loaded.
 *
 * Used as the default bridge when no platform-specific ML runtime is available.
 * The [NeuralLipSyncEngineImpl] will fall back to amplitude-based lip sync
 * when this bridge is active.
 */
internal class StubNeuralModelBridge : NeuralModelBridge {

    override val isLoaded: Boolean = false

    override suspend fun load(config: NeuralLipSyncConfig) {
        // No ML runtime available on this platform
    }

    override fun infer(melFeatures: FloatArray): FloatArray = FloatArray(0)

    override fun release(): Unit = Unit
}
