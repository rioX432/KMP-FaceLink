package io.github.kmpfacelink.voice.lipsync

import io.github.kmpfacelink.model.BlendShape
import io.github.kmpfacelink.voice.audio.AudioData
import io.github.kmpfacelink.voice.audio.AudioFormat
import io.github.kmpfacelink.voice.lipsync.internal.NeuralLipSyncEngineImpl
import io.github.kmpfacelink.voice.lipsync.internal.StubNeuralModelBridge
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class NeuralLipSyncEngineImplTest {

    private val format16kHz = AudioFormat(sampleRate = 16000, channels = 1, bitsPerSample = 16)
    private val neuralConfig = NeuralLipSyncConfig(modelPath = "test_model.tflite")

    @AfterTest
    fun cleanup() {
        NeuralLipSyncProvider.clearBridgeFactory()
    }

    @Test
    fun stubBridgeReportsModelNotLoaded() {
        val engine = NeuralLipSyncEngineImpl(StubNeuralModelBridge(), neuralConfig)
        assertFalse(engine.isModelLoaded)
    }

    @Test
    fun fallsBackToAmplitudeWhenModelNotLoaded() = runTest {
        val engine = NeuralLipSyncEngineImpl(StubNeuralModelBridge(), neuralConfig)
        val audio = createTestAudio()
        val config = LipSyncConfig(targetFps = 30)

        val frames = engine.predictFromAudio(audio, config).toList()
        assertTrue(frames.isNotEmpty(), "Should produce fallback frames")
    }

    @Test
    fun loadedModelProducesNeuralFrames() = runTest {
        val bridge = FakeLoadedModelBridge()
        val engine = NeuralLipSyncEngineImpl(bridge, neuralConfig)

        assertTrue(engine.isModelLoaded)

        val audio = createTestAudio()
        val config = LipSyncConfig(targetFps = 30, smoothing = 0.0f)
        val frames = engine.predictFromAudio(audio, config).toList()

        assertTrue(frames.isNotEmpty(), "Should produce neural frames")
        // Last frame is silence
        assertTrue(frames.last().blendShapes.isEmpty(), "Last frame should be silence")
        // Non-silence frames should have JAW_OPEN from the fake model
        val nonSilentFrames = frames.dropLast(1)
        if (nonSilentFrames.isNotEmpty()) {
            assertTrue(
                nonSilentFrames.any { BlendShape.JAW_OPEN in it.blendShapes },
                "Neural frames should contain JAW_OPEN from fake model",
            )
        }
    }

    @Test
    fun unloadModelReleasesResources() {
        val bridge = FakeLoadedModelBridge()
        val engine = NeuralLipSyncEngineImpl(bridge, neuralConfig)

        assertTrue(engine.isModelLoaded)
        engine.unloadModel()
        assertFalse(bridge.isLoaded)
    }

    @Test
    fun releaseCleansBridge() {
        val bridge = FakeLoadedModelBridge()
        val engine = NeuralLipSyncEngineImpl(bridge, neuralConfig)

        engine.release()
        assertFalse(bridge.isLoaded)
    }

    @Test
    fun providerCreatesWithStubWhenNoBridgeRegistered() {
        val engine = NeuralLipSyncProvider.create(neuralConfig)
        assertFalse(engine.isModelLoaded)
    }

    @Test
    fun providerCreatesWithRegisteredBridge() {
        NeuralLipSyncProvider.registerBridgeFactory { FakeLoadedModelBridge() }
        val engine = NeuralLipSyncProvider.create(neuralConfig)
        assertTrue(engine.isModelLoaded)
    }

    @Test
    fun providerIsBridgeAvailableReflectsState() {
        assertFalse(NeuralLipSyncProvider.isBridgeAvailable())
        NeuralLipSyncProvider.registerBridgeFactory { FakeLoadedModelBridge() }
        assertTrue(NeuralLipSyncProvider.isBridgeAvailable())
        NeuralLipSyncProvider.clearBridgeFactory()
        assertFalse(NeuralLipSyncProvider.isBridgeAvailable())
    }

    @Test
    fun framesHaveIncreasingTimestamps() = runTest {
        val bridge = FakeLoadedModelBridge()
        val engine = NeuralLipSyncEngineImpl(bridge, neuralConfig)
        val audio = createTestAudio()
        val config = LipSyncConfig(targetFps = 30)

        val frames = engine.predictFromAudio(audio, config).toList()
        for (i in 1 until frames.size) {
            assertTrue(
                frames[i].timestampMs >= frames[i - 1].timestampMs,
                "Timestamps should be non-decreasing",
            )
        }
    }

    private fun createTestAudio(): AudioData {
        // 1 second of 16kHz 16-bit audio with some signal
        val bytes = ByteArray(32000)
        for (i in bytes.indices step 2) {
            bytes[i] = 0x00.toByte()
            bytes[i + 1] = 0x40.toByte()
        }
        return AudioData(bytes = bytes, format = format16kHz, durationMs = 1000)
    }
}

/**
 * Fake bridge that simulates a loaded model returning fixed blend shape weights.
 */
private class FakeLoadedModelBridge : NeuralModelBridge {
    private var _isLoaded = true

    override val isLoaded: Boolean get() = _isLoaded

    override suspend fun load(config: NeuralLipSyncConfig) {
        _isLoaded = true
    }

    override fun infer(melFeatures: FloatArray): FloatArray {
        // Return 15 fixed weights (matching BlendShapeOutputMap.outputOrder size)
        return FloatArray(OUTPUT_SIZE) { FIXED_WEIGHT }
    }

    override fun release() {
        _isLoaded = false
    }

    companion object {
        private const val OUTPUT_SIZE = 15
        private const val FIXED_WEIGHT = 0.5f
    }
}
