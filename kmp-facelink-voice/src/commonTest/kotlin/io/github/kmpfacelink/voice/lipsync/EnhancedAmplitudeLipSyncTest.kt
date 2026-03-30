package io.github.kmpfacelink.voice.lipsync

import io.github.kmpfacelink.model.BlendShape
import io.github.kmpfacelink.voice.audio.AudioData
import io.github.kmpfacelink.voice.audio.AudioFormat
import io.github.kmpfacelink.voice.lipsync.internal.EnhancedAmplitudeLipSync
import io.github.kmpfacelink.voice.lipsync.internal.mapFeaturesToBlendShapes
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertTrue

class EnhancedAmplitudeLipSyncTest {

    private val format16kHz = AudioFormat(sampleRate = 16000, channels = 1, bitsPerSample = 16)

    @Test
    fun silenceProducesSilenceFrame() {
        val features = AudioFeatures(energy = 0.01f, zeroCrossingRate = 0f, spectralCentroid = 0f)
        val result = mapFeaturesToBlendShapes(features)
        assertTrue(result.isEmpty(), "Silence should produce empty blend shapes")
    }

    @Test
    fun highEnergyProducesJawOpen() {
        val features = AudioFeatures(energy = 0.8f, zeroCrossingRate = 0.3f, spectralCentroid = 0.5f)
        val result = mapFeaturesToBlendShapes(features)
        assertTrue(BlendShape.JAW_OPEN in result, "High energy should produce JAW_OPEN")
        assertTrue(result[BlendShape.JAW_OPEN]!! > 0.3f, "JAW_OPEN should be significant")
    }

    @Test
    fun highEnergyHighZcrProducesStretch() {
        val features = AudioFeatures(energy = 0.8f, zeroCrossingRate = 0.7f, spectralCentroid = 0.5f)
        val result = mapFeaturesToBlendShapes(features)
        assertTrue(
            BlendShape.MOUTH_STRETCH_LEFT in result,
            "High energy + high ZCR should produce MOUTH_STRETCH",
        )
    }

    @Test
    fun mediumEnergyProducesFunnel() {
        val features = AudioFeatures(energy = 0.4f, zeroCrossingRate = 0.3f, spectralCentroid = 0.3f)
        val result = mapFeaturesToBlendShapes(features)
        assertTrue(BlendShape.MOUTH_FUNNEL in result, "Medium energy should produce MOUTH_FUNNEL")
    }

    @Test
    fun lowEnergyProducesMouthClose() {
        val features = AudioFeatures(energy = 0.1f, zeroCrossingRate = 0.2f, spectralCentroid = 0.1f)
        val result = mapFeaturesToBlendShapes(features)
        assertTrue(BlendShape.MOUTH_CLOSE in result, "Low energy should produce MOUTH_CLOSE")
    }

    @Test
    fun transientProducesPress() {
        val features = AudioFeatures(energy = 0.5f, zeroCrossingRate = 0.9f, spectralCentroid = 0.7f)
        val result = mapFeaturesToBlendShapes(features)
        assertTrue(
            BlendShape.MOUTH_PRESS_LEFT in result,
            "Transient should produce MOUTH_PRESS",
        )
    }

    @Test
    fun animateFromAudioProducesFrames() = runTest {
        val engine = EnhancedAmplitudeLipSync()
        val bytes = ByteArray(32000)
        for (i in bytes.indices step 2) {
            bytes[i] = 0x00.toByte()
            bytes[i + 1] = 0x40.toByte()
        }
        val audio = AudioData(bytes = bytes, format = format16kHz, durationMs = 1000)
        val config = LipSyncConfig(targetFps = 30, smoothing = 0.2f)
        val frames = engine.animateFromAudio(audio, config).toList()

        assertTrue(frames.isNotEmpty(), "Should produce frames")
        // Last frame should be the silence frame
        assertTrue(
            frames.last().blendShapes.isEmpty(),
            "Last frame should be silence",
        )
    }

    @Test
    fun allBlendShapeValuesArePositive() {
        val features = AudioFeatures(energy = 0.7f, zeroCrossingRate = 0.5f, spectralCentroid = 0.5f)
        val result = mapFeaturesToBlendShapes(features)
        result.values.forEach { value ->
            assertTrue(value > 0f, "All blend shape values should be positive")
        }
    }
}
