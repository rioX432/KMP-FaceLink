package io.github.kmpfacelink.voice.lipsync

import io.github.kmpfacelink.voice.lipsync.internal.SpectrogramExtractor
import kotlin.math.PI
import kotlin.math.sin
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SpectrogramExtractorTest {

    @Test
    fun emptyInputProducesEmptyOutput() {
        val result = SpectrogramExtractor.extract(
            samples = FloatArray(0),
            sampleRate = 16000,
            hopSize = 160,
        )
        assertTrue(result.isEmpty())
    }

    @Test
    fun tooShortInputProducesEmptyOutput() {
        // Less than FFT_SIZE (512) samples
        val result = SpectrogramExtractor.extract(
            samples = FloatArray(100),
            sampleRate = 16000,
            hopSize = 160,
        )
        assertTrue(result.isEmpty())
    }

    @Test
    fun producesExpectedFrameCount() {
        val sampleRate = 16000
        val hopSize = 160 // 10ms hop at 16kHz
        val durationSamples = sampleRate // 1 second
        val samples = FloatArray(durationSamples)

        val result = SpectrogramExtractor.extract(samples, sampleRate, hopSize)

        // Expected: (16000 - 512) / 160 + 1 = 97 frames (approx)
        assertTrue(result.isNotEmpty(), "Should produce frames")
        assertTrue(result.size > 90, "Should produce approximately 97 frames for 1s audio")
    }

    @Test
    fun eachFrameHasCorrectBinCount() {
        val numMelBins = 26
        val samples = FloatArray(1600) // 100ms at 16kHz
        val result = SpectrogramExtractor.extract(
            samples = samples,
            sampleRate = 16000,
            hopSize = 160,
            numMelBins = numMelBins,
        )

        assertTrue(result.isNotEmpty())
        result.forEach { frame ->
            assertEquals(numMelBins, frame.size, "Each frame should have $numMelBins mel bins")
        }
    }

    @Test
    fun silenceProducesLowEnergy() {
        val samples = FloatArray(1600) // All zeros
        val result = SpectrogramExtractor.extract(samples, 16000, 160)

        assertTrue(result.isNotEmpty())
        // Silence should have very low mel energies (log of near-zero)
        result.forEach { frame ->
            frame.forEach { value ->
                assertTrue(value < 0f, "Silence mel values should be negative (log scale)")
            }
        }
    }

    @Test
    fun toneProducesHigherEnergyThanSilence() {
        val sampleRate = 16000
        val samples = FloatArray(sampleRate) // 1 second
        // Generate a 440Hz sine wave
        for (i in samples.indices) {
            samples[i] = (sin(2.0 * PI * TONE_FREQ * i / sampleRate) * TONE_AMPLITUDE).toFloat()
        }

        val toneResult = SpectrogramExtractor.extract(samples, sampleRate, 160)
        val silenceResult = SpectrogramExtractor.extract(FloatArray(sampleRate), sampleRate, 160)

        assertTrue(toneResult.isNotEmpty())
        assertTrue(silenceResult.isNotEmpty())

        // Average energy of tone should be higher than silence
        val toneAvg = toneResult.map { it.average() }.average()
        val silenceAvg = silenceResult.map { it.average() }.average()
        assertTrue(toneAvg > silenceAvg, "Tone should have higher mel energy than silence")
    }

    companion object {
        private const val TONE_FREQ = 440.0
        private const val TONE_AMPLITUDE = 0.8
    }
}
