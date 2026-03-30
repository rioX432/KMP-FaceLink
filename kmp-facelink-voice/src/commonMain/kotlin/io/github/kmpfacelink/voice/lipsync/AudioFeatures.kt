package io.github.kmpfacelink.voice.lipsync

/**
 * Audio features extracted from a single frame of audio data.
 *
 * These features provide a richer representation than raw amplitude,
 * enabling more nuanced lip sync animation.
 *
 * @property energy RMS energy of the frame (0.0–1.0, normalized)
 * @property zeroCrossingRate Rate of sign changes in the signal (0.0–1.0, normalized)
 * @property spectralCentroid Estimated brightness/frequency center (0.0–1.0, normalized)
 */
public data class AudioFeatures(
    val energy: Float,
    val zeroCrossingRate: Float,
    val spectralCentroid: Float,
)
