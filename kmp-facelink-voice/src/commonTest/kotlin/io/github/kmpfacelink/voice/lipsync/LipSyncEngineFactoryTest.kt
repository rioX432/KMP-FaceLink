package io.github.kmpfacelink.voice.lipsync

import io.github.kmpfacelink.voice.audio.AudioData
import io.github.kmpfacelink.voice.lipsync.internal.DefaultLipSyncEngine
import io.github.kmpfacelink.voice.lipsync.internal.EnhancedAmplitudeLipSync
import io.github.kmpfacelink.voice.tts.PhonemeEvent
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class LipSyncEngineFactoryTest {

    @AfterTest
    fun cleanup() {
        LipSyncEngineFactory.clearNeuralProvider()
    }

    @Test
    fun defaultTypeCreatesDefaultEngine() {
        val engine = LipSyncEngineFactory.create(LipSyncEngineType.DEFAULT)
        assertIs<DefaultLipSyncEngine>(engine)
    }

    @Test
    fun enhancedAmplitudeTypeCreatesEnhancedEngine() {
        val engine = LipSyncEngineFactory.create(LipSyncEngineType.ENHANCED_AMPLITUDE)
        assertIs<EnhancedAmplitudeLipSync>(engine)
    }

    @Test
    fun neuralTypeFallsBackToEnhancedWhenNoProvider() {
        val engine = LipSyncEngineFactory.create(LipSyncEngineType.NEURAL)
        assertIs<EnhancedAmplitudeLipSync>(engine)
    }

    @Test
    fun neuralTypeUsesProviderWhenRegistered() {
        LipSyncEngineFactory.registerNeuralProvider { FakeNeuralLipSyncEngine() }
        val engine = LipSyncEngineFactory.create(LipSyncEngineType.NEURAL)
        assertIs<FakeNeuralLipSyncEngine>(engine)
    }

    @Test
    fun isNeuralAvailableReturnsFalseByDefault() {
        assertFalse(LipSyncEngineFactory.isNeuralAvailable())
    }

    @Test
    fun isNeuralAvailableReturnsTrueAfterRegistration() {
        LipSyncEngineFactory.registerNeuralProvider { FakeNeuralLipSyncEngine() }
        assertTrue(LipSyncEngineFactory.isNeuralAvailable())
    }

    @Test
    fun clearNeuralProviderResetsAvailability() {
        LipSyncEngineFactory.registerNeuralProvider { FakeNeuralLipSyncEngine() }
        LipSyncEngineFactory.clearNeuralProvider()
        assertFalse(LipSyncEngineFactory.isNeuralAvailable())
    }

    @Test
    fun defaultCreateUsesDefaultType() {
        val engine = LipSyncEngineFactory.create()
        assertIs<DefaultLipSyncEngine>(engine)
    }
}

private class FakeNeuralLipSyncEngine : NeuralLipSyncEngine {
    override val isModelLoaded: Boolean = false

    override suspend fun loadModel(modelPath: String) = Unit

    override fun unloadModel() = Unit

    override fun predictFromAudio(
        audio: AudioData,
        config: LipSyncConfig,
    ): Flow<LipSyncFrame> = emptyFlow()

    override fun animateFromPhonemes(
        phonemeEvents: List<PhonemeEvent>,
        config: LipSyncConfig,
    ): Flow<LipSyncFrame> = emptyFlow()

    override fun animateFromAudio(
        audio: AudioData,
        config: LipSyncConfig,
    ): Flow<LipSyncFrame> = emptyFlow()
}
