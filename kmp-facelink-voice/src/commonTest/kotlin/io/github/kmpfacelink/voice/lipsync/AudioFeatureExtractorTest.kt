package io.github.kmpfacelink.voice.lipsync

import io.github.kmpfacelink.voice.audio.AudioData
import io.github.kmpfacelink.voice.audio.AudioFormat
import io.github.kmpfacelink.voice.lipsync.internal.AudioFeatureExtractor
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AudioFeatureExtractorTest {

    private val format16kHz = AudioFormat(sampleRate = 16000, channels = 1, bitsPerSample = 16)

    @Test
    fun emptyAudioReturnsEmptyList() {
        val audio = AudioData(bytes = ByteArray(0), format = format16kHz, durationMs = 0)
        val result = AudioFeatureExtractor.extract(audio, targetFps = 30)
        assertTrue(result.isEmpty())
    }

    @Test
    fun silentAudioReturnsZeroFeatures() {
        val silentBytes = ByteArray(32000) // 1 second at 16kHz 16-bit
        val audio = AudioData(bytes = silentBytes, format = format16kHz, durationMs = 1000)
        val result = AudioFeatureExtractor.extract(audio, targetFps = 30)

        assertTrue(result.isNotEmpty())
        result.forEach { features ->
            assertEquals(0f, features.energy, "Silent audio should have zero energy")
            assertEquals(0f, features.zeroCrossingRate, "Silent audio should have zero ZCR")
            assertEquals(0f, features.spectralCentroid, "Silent audio should have zero centroid")
        }
    }

    @Test
    fun nonSilentAudioReturnsNonZeroEnergy() {
        val bytes = ByteArray(32000)
        for (i in bytes.indices step 2) {
            bytes[i] = 0x00.toByte()
            bytes[i + 1] = 0x40.toByte()
        }
        val audio = AudioData(bytes = bytes, format = format16kHz, durationMs = 1000)
        val result = AudioFeatureExtractor.extract(audio, targetFps = 30)

        assertTrue(result.isNotEmpty())
        assertTrue(result.any { it.energy > 0f }, "Non-silent audio should have non-zero energy")
    }

    @Test
    fun featuresAreNormalized() {
        val bytes = ByteArray(32000)
        for (i in bytes.indices step 2) {
            val value = if (i < 16000) 0x20 else 0x7F
            bytes[i] = 0x00.toByte()
            bytes[i + 1] = value.toByte()
        }
        val audio = AudioData(bytes = bytes, format = format16kHz, durationMs = 1000)
        val result = AudioFeatureExtractor.extract(audio, targetFps = 30)

        result.forEach { features ->
            assertTrue(features.energy in 0f..1f, "Energy should be in 0..1")
            assertTrue(features.zeroCrossingRate in 0f..1f, "ZCR should be in 0..1")
            assertTrue(features.spectralCentroid in 0f..1f, "Centroid should be in 0..1")
        }
    }

    @Test
    fun alternatingSignalHasHighZeroCrossingRate() {
        // Create a signal that alternates between positive and negative
        val bytes = ByteArray(32000)
        for (i in 0 until 16000) {
            val offset = i * 2
            if (i % 2 == 0) {
                bytes[offset] = 0x00.toByte()
                bytes[offset + 1] = 0x40.toByte() // Positive
            } else {
                bytes[offset] = 0x00.toByte()
                bytes[offset + 1] = 0xC0.toByte() // Negative (two's complement)
            }
        }
        val audio = AudioData(bytes = bytes, format = format16kHz, durationMs = 1000)
        val result = AudioFeatureExtractor.extract(audio, targetFps = 30)

        assertTrue(result.isNotEmpty())
        val avgZcr = result.map { it.zeroCrossingRate }.average().toFloat()
        assertTrue(avgZcr > 0.5f, "Alternating signal should have high ZCR, got $avgZcr")
    }

    @Test
    fun frameCountMatchesExpected() {
        val silentBytes = ByteArray(32000)
        val audio = AudioData(bytes = silentBytes, format = format16kHz, durationMs = 1000)
        val result = AudioFeatureExtractor.extract(audio, targetFps = 30)

        // 16000 samples / (16000/30) = 30 frames
        assertEquals(30, result.size, "Should produce 30 frames for 1s at 30fps")
    }
}
