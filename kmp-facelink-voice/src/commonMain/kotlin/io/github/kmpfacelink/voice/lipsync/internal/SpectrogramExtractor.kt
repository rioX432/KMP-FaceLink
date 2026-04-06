package io.github.kmpfacelink.voice.lipsync.internal

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.sin

// FFT and mel filter constants
private const val MEL_FILTER_BANKS = 26
private const val FFT_SIZE = 512
private const val MEL_FREQ_BASE = 700.0
private const val MEL_SCALE_FACTOR = 2595.0
private const val LOG_BASE_10 = 10.0
private const val LOG_FLOOR = 1e-10
private const val TWO = 2
private const val HALF = 0.5

/**
 * Extracts mel spectrogram features from raw PCM audio.
 *
 * Produces a sequence of mel-frequency feature vectors suitable for
 * neural lip sync model input. Uses a simplified mel filter bank
 * with Hann windowing and power spectrum computation.
 */
internal object SpectrogramExtractor {

    /**
     * Extracts mel spectrogram frames from PCM float samples.
     *
     * @param samples Normalized PCM samples (-1.0 to 1.0)
     * @param sampleRate Audio sample rate in Hz
     * @param hopSize Number of samples between successive frames
     * @param numMelBins Number of mel filter bank outputs
     * @return List of mel spectrogram frames, each with [numMelBins] values
     */
    fun extract(
        samples: FloatArray,
        sampleRate: Int,
        hopSize: Int,
        numMelBins: Int = MEL_FILTER_BANKS,
    ): List<FloatArray> {
        if (samples.size < FFT_SIZE) return emptyList()

        val window = hannWindow(FFT_SIZE)
        val melFilters = buildMelFilterBank(numMelBins, FFT_SIZE, sampleRate)
        val frameCount = (samples.size - FFT_SIZE) / hopSize + 1

        return (0 until frameCount).map { frame ->
            val offset = frame * hopSize
            val windowed = applyWindow(samples, offset, window)
            val powerSpectrum = computePowerSpectrum(windowed)
            applyMelFilters(powerSpectrum, melFilters)
        }
    }

    private fun hannWindow(size: Int): FloatArray =
        FloatArray(size) { i ->
            val cosVal = cos(TWO * PI * i / (size - 1))
            (HALF * (1.0 - cosVal)).toFloat()
        }

    private fun applyWindow(
        samples: FloatArray,
        offset: Int,
        window: FloatArray,
    ): FloatArray {
        val result = FloatArray(window.size)
        for (i in window.indices) {
            val idx = offset + i
            result[i] = if (idx < samples.size) samples[idx] * window[i] else 0f
        }
        return result
    }

    /**
     * Computes power spectrum using a real-valued DFT.
     *
     * For production use, this should be replaced with an optimized FFT.
     * This naive O(N^2) implementation is sufficient for the small FFT_SIZE (512).
     */
    private fun computePowerSpectrum(frame: FloatArray): FloatArray {
        val n = frame.size
        val halfN = n / TWO + 1
        val spectrum = FloatArray(halfN)

        for (k in 0 until halfN) {
            var real = 0.0
            var imag = 0.0
            val freqRatio = TWO * PI * k / n
            for (i in 0 until n) {
                val angle = freqRatio * i
                real += frame[i] * cos(angle)
                imag -= frame[i] * sin(angle)
            }
            spectrum[k] = ((real * real + imag * imag) / n).toFloat()
        }
        return spectrum
    }

    private fun applyMelFilters(
        powerSpectrum: FloatArray,
        filters: Array<FloatArray>,
    ): FloatArray {
        val result = FloatArray(filters.size)
        for (i in filters.indices) {
            var sum = 0f
            val filter = filters[i]
            val limit = minOf(filter.size, powerSpectrum.size)
            for (j in 0 until limit) {
                sum += powerSpectrum[j] * filter[j]
            }
            // Log-scale mel energy
            result[i] = ln(maxOf(sum.toDouble(), LOG_FLOOR)).toFloat()
        }
        return result
    }

    private fun buildMelFilterBank(
        numFilters: Int,
        fftSize: Int,
        sampleRate: Int,
    ): Array<FloatArray> {
        val halfFft = fftSize / TWO + 1
        val maxFreq = sampleRate.toDouble() / TWO
        val maxMel = hzToMel(maxFreq)
        val minMel = hzToMel(0.0)

        // Evenly spaced mel points
        val melPoints = DoubleArray(numFilters + TWO) { i ->
            minMel + i * (maxMel - minMel) / (numFilters + 1)
        }
        val binPoints = IntArray(melPoints.size) { i ->
            ((melToHz(melPoints[i]) * fftSize / sampleRate)).toInt()
                .coerceIn(0, halfFft - 1)
        }

        return Array(numFilters) { i ->
            buildSingleFilter(binPoints[i], binPoints[i + 1], binPoints[i + TWO], halfFft)
        }
    }

    private fun buildSingleFilter(
        left: Int,
        center: Int,
        right: Int,
        size: Int,
    ): FloatArray {
        val filter = FloatArray(size)
        for (j in left..center) {
            if (center > left) {
                filter[j] = (j - left).toFloat() / (center - left)
            }
        }
        for (j in center..right) {
            if (right > center) {
                filter[j] = (right - j).toFloat() / (right - center)
            }
        }
        return filter
    }

    private fun hzToMel(hz: Double): Double =
        MEL_SCALE_FACTOR * ln(1.0 + hz / MEL_FREQ_BASE) / ln(LOG_BASE_10)

    private fun melToHz(mel: Double): Double =
        MEL_FREQ_BASE * (exp(mel * ln(LOG_BASE_10) / MEL_SCALE_FACTOR) - 1.0)
}
