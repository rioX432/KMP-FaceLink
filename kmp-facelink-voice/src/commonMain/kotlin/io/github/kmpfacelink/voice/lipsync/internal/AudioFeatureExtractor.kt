package io.github.kmpfacelink.voice.lipsync.internal

import io.github.kmpfacelink.voice.audio.AudioData
import io.github.kmpfacelink.voice.lipsync.AudioFeatures
import kotlin.math.abs
import kotlin.math.sqrt

private const val BITS_PER_BYTE = 8
private const val BYTES_PER_SAMPLE_16BIT = 2
private const val BYTE_MASK = 0xFF
private const val MIN_FEATURE_THRESHOLD = 0.0001f

/**
 * Extracts audio features (energy, zero-crossing rate, spectral centroid) from raw PCM audio.
 *
 * All features are computed per-frame and normalized to 0.0–1.0 range.
 * Pure Kotlin implementation — no FFT library required.
 */
internal object AudioFeatureExtractor {

    /**
     * Extracts [AudioFeatures] for each frame at the given frame rate.
     *
     * @param audio Raw PCM audio data (expected 16-bit mono)
     * @param targetFps Number of feature frames per second
     * @return List of [AudioFeatures] with one entry per frame
     */
    fun extract(audio: AudioData, targetFps: Int): List<AudioFeatures> {
        val bytesPerSample = audio.format.bitsPerSample / BITS_PER_BYTE
        val totalSamples = audio.bytes.size / bytesPerSample
        val samplesPerFrame = audio.format.sampleRate / targetFps

        if (totalSamples == 0 || samplesPerFrame == 0) return emptyList()

        val samples = decodeSamples(audio.bytes, totalSamples, bytesPerSample)
        val frameCount = (totalSamples + samplesPerFrame - 1) / samplesPerFrame
        val rawFeatures = extractRawFeatures(samples, frameCount, samplesPerFrame)

        return normalize(rawFeatures)
    }

    private fun extractRawFeatures(
        samples: FloatArray,
        frameCount: Int,
        samplesPerFrame: Int,
    ): List<RawFeatures> {
        val result = ArrayList<RawFeatures>(frameCount)
        for (frame in 0 until frameCount) {
            val start = frame * samplesPerFrame
            val end = minOf(start + samplesPerFrame, samples.size)
            result.add(computeFrameFeatures(samples, start, end))
        }
        return result
    }

    private fun computeFrameFeatures(
        samples: FloatArray,
        start: Int,
        end: Int,
    ): RawFeatures {
        val count = end - start
        if (count == 0) return RawFeatures(0f, 0f, 0f)

        var sumSquares = 0.0
        var zeroCrossings = 0
        var weightedFreqSum = 0.0
        var magnitudeSum = 0.0

        for (i in start until end) {
            val sample = samples[i]
            sumSquares += (sample * sample).toDouble()

            if (i > start && hasSignChange(samples[i - 1], sample)) {
                zeroCrossings++
            }

            // Spectral centroid approximation via weighted average of absolute differences
            val absSample = abs(sample)
            val localFreq = if (i > start) abs(sample - samples[i - 1]) else 0f
            weightedFreqSum += (localFreq * absSample).toDouble()
            magnitudeSum += absSample.toDouble()
        }

        val energy = sqrt(sumSquares / count).toFloat()
        val zcr = zeroCrossings.toFloat() / count
        val centroid = if (magnitudeSum > MIN_FEATURE_THRESHOLD) {
            (weightedFreqSum / magnitudeSum).toFloat()
        } else {
            0f
        }

        return RawFeatures(energy, zcr, centroid)
    }

    private fun normalize(rawFeatures: List<RawFeatures>): List<AudioFeatures> {
        if (rawFeatures.isEmpty()) return emptyList()

        val maxEnergy = rawFeatures.maxOf { it.energy }
        val maxZcr = rawFeatures.maxOf { it.zcr }
        val maxCentroid = rawFeatures.maxOf { it.centroid }

        return rawFeatures.map { raw ->
            AudioFeatures(
                energy = safeDivide(raw.energy, maxEnergy),
                zeroCrossingRate = safeDivide(raw.zcr, maxZcr),
                spectralCentroid = safeDivide(raw.centroid, maxCentroid),
            )
        }
    }

    private fun safeDivide(value: Float, max: Float): Float =
        if (max > MIN_FEATURE_THRESHOLD) (value / max).coerceIn(0f, 1f) else 0f

    private fun decodeSamples(
        bytes: ByteArray,
        totalSamples: Int,
        bytesPerSample: Int,
    ): FloatArray {
        val samples = FloatArray(totalSamples)
        for (i in 0 until totalSamples) {
            samples[i] = decodeSample(bytes, i, bytesPerSample)
        }
        return samples
    }

    private fun decodeSample(bytes: ByteArray, index: Int, bytesPerSample: Int): Float {
        if (bytesPerSample == BYTES_PER_SAMPLE_16BIT) {
            val offset = index * BYTES_PER_SAMPLE_16BIT
            if (offset + 1 >= bytes.size) return 0f
            val lo = bytes[offset].toInt() and BYTE_MASK
            val hi = bytes[offset + 1].toInt()
            val int16 = (hi shl BITS_PER_BYTE) or lo
            return int16.toFloat() / Short.MAX_VALUE
        }
        return if (index < bytes.size) bytes[index].toFloat() / Byte.MAX_VALUE else 0f
    }

    private fun hasSignChange(prev: Float, current: Float): Boolean =
        (current >= 0f && prev < 0f) || (current < 0f && prev >= 0f)

    private data class RawFeatures(val energy: Float, val zcr: Float, val centroid: Float)
}
