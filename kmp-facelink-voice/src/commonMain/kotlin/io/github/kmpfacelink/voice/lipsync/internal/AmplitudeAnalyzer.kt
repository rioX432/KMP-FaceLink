package io.github.kmpfacelink.voice.lipsync.internal

import io.github.kmpfacelink.voice.AudioConstants
import io.github.kmpfacelink.voice.audio.AudioData
import kotlin.math.sqrt
private const val BITS_PER_BYTE = 8
private const val BYTES_PER_SAMPLE_8BIT = 1
private const val BYTES_PER_SAMPLE_16BIT = 2
private const val BYTE_MASK = 0xFF

/**
 * Analyzes audio data to extract amplitude envelope.
 *
 * Computes RMS amplitude for fixed-duration windows.
 */
internal object AmplitudeAnalyzer {

    /**
     * Computes RMS amplitude values at the given frame rate.
     *
     * @param audio Audio data (expected 16-bit PCM)
     * @param targetFps Number of amplitude samples per second
     * @return List of normalized amplitude values (0.0–1.0) with one entry per frame
     */
    fun analyze(audio: AudioData, targetFps: Int): List<Float> {
        val bytesPerSample = audio.format.bitsPerSample / BITS_PER_BYTE
        val totalSamples = audio.bytes.size / bytesPerSample
        val samplesPerFrame = audio.format.sampleRate / targetFps

        if (totalSamples == 0 || samplesPerFrame == 0) return emptyList()

        val frameCount = (totalSamples + samplesPerFrame - 1) / samplesPerFrame
        val amplitudes = ArrayList<Float>(frameCount)

        var maxAmplitude = 0f

        for (frame in 0 until frameCount) {
            val startSample = frame * samplesPerFrame
            val endSample = minOf(startSample + samplesPerFrame, totalSamples)
            val rms = computeRms(audio.bytes, startSample, endSample, bytesPerSample)
            amplitudes.add(rms)
            if (rms > maxAmplitude) maxAmplitude = rms
        }

        // Normalize to 0.0–1.0
        if (maxAmplitude > 0f) {
            for (i in amplitudes.indices) {
                amplitudes[i] = amplitudes[i] / maxAmplitude
            }
        }

        return amplitudes
    }

    /**
     * Computes the frame duration in milliseconds.
     */
    fun frameDurationMs(targetFps: Int): Long = AudioConstants.MILLIS_PER_SECOND / targetFps

    private fun computeRms(
        bytes: ByteArray,
        startSample: Int,
        endSample: Int,
        bytesPerSample: Int,
    ): Float {
        var sumSquares = 0.0
        val count = endSample - startSample

        for (i in startSample until endSample) {
            val sample = when (bytesPerSample) {
                BYTES_PER_SAMPLE_8BIT -> bytes[i].toFloat() / Byte.MAX_VALUE
                BYTES_PER_SAMPLE_16BIT -> {
                    val offset = i * BYTES_PER_SAMPLE_16BIT
                    if (offset + 1 < bytes.size) {
                        val lo = bytes[offset].toInt() and BYTE_MASK
                        val hi = bytes[offset + 1].toInt()
                        val int16 = (hi shl BITS_PER_BYTE) or lo
                        int16.toFloat() / Short.MAX_VALUE
                    } else {
                        0f
                    }
                }
                else -> 0f
            }
            sumSquares += (sample * sample).toDouble()
        }

        return if (count > 0) sqrt(sumSquares / count).toFloat() else 0f
    }
}
