package io.github.kmpfacelink.voice.lipsync

import io.github.kmpfacelink.voice.audio.AudioData
import io.github.kmpfacelink.voice.audio.AudioFormat
import io.github.kmpfacelink.voice.lipsync.internal.AmplitudeAnalyzer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AmplitudeAnalyzerTest {

    private val format16kHz = AudioFormat(sampleRate = 16000, channels = 1, bitsPerSample = 16)

    @Test
    fun emptyAudioReturnsEmptyList() {
        val audio = AudioData(bytes = ByteArray(0), format = format16kHz, durationMs = 0)
        val result = AmplitudeAnalyzer.analyze(audio, targetFps = 30)
        assertTrue(result.isEmpty())
    }

    @Test
    fun silentAudioReturnsZeroAmplitudes() {
        // 1 second of 16kHz 16-bit silence = 32000 bytes (all zeros)
        val silentBytes = ByteArray(32000)
        val audio = AudioData(bytes = silentBytes, format = format16kHz, durationMs = 1000)
        val result = AmplitudeAnalyzer.analyze(audio, targetFps = 30)

        assertTrue(result.isNotEmpty())
        result.forEach { amplitude ->
            assertEquals(0f, amplitude, "Silent audio should have zero amplitude")
        }
    }

    @Test
    fun nonSilentAudioReturnsNonZeroAmplitudes() {
        // Create non-silent audio: simple alternating pattern
        val bytes = ByteArray(32000) // 1 second at 16kHz 16-bit
        for (i in bytes.indices step 2) {
            bytes[i] = 0x00.toByte() // Low byte
            bytes[i + 1] = 0x40.toByte() // High byte (~16384)
        }
        val audio = AudioData(bytes = bytes, format = format16kHz, durationMs = 1000)
        val result = AmplitudeAnalyzer.analyze(audio, targetFps = 30)

        assertTrue(result.isNotEmpty())
        assertTrue(result.any { it > 0f }, "Non-silent audio should have non-zero amplitudes")
    }

    @Test
    fun amplitudesAreNormalized() {
        // Create audio with varying amplitudes
        val bytes = ByteArray(32000)
        for (i in bytes.indices step 2) {
            val value = if (i < 16000) 0x20 else 0x7F // Half quiet, half loud
            bytes[i] = 0x00.toByte()
            bytes[i + 1] = value.toByte()
        }
        val audio = AudioData(bytes = bytes, format = format16kHz, durationMs = 1000)
        val result = AmplitudeAnalyzer.analyze(audio, targetFps = 30)

        result.forEach { amplitude ->
            assertTrue(amplitude in 0f..1f, "Amplitude $amplitude should be normalized to 0..1")
        }
        // The loudest frame should be normalized to 1.0
        assertTrue(result.max() > 0.9f, "Max amplitude should be close to 1.0")
    }

    @Test
    fun frameDurationIsCorrect() {
        assertEquals(33L, AmplitudeAnalyzer.frameDurationMs(30))
        assertEquals(16L, AmplitudeAnalyzer.frameDurationMs(60))
    }
}
