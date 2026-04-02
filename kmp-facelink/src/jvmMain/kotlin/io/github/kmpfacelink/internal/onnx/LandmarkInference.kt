package io.github.kmpfacelink.internal.onnx

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import java.nio.FloatBuffer
import kotlin.math.ln

/**
 * Runs OpenSeeFace landmark inference on cropped face images.
 *
 * Input: 224x224 normalized RGB tensor (CHW format).
 * Output: 66 facial landmark points in image-space coordinates.
 *
 * The model outputs heatmaps (27x27) with confidence and offset channels
 * for each of the 66 landmarks.
 */
internal class LandmarkInference(private val session: OrtSession) {

    private val env: OrtEnvironment = OrtEnvironment.getEnvironment()

    /**
     * Run landmark inference on a preprocessed face crop.
     *
     * @param input Preprocessed landmark input with tensor and crop metadata.
     * @param frameWidth Original frame width for coordinate normalization.
     * @param frameHeight Original frame height for coordinate normalization.
     * @return Array of 66 landmark points in normalized [0,1] coordinates, or null if inference fails.
     */
    fun infer(
        input: LandmarkInput,
        frameWidth: Int,
        frameHeight: Int,
    ): LandmarkResult? {
        val inputShape = longArrayOf(1, CHANNELS.toLong(), INPUT_SIZE.toLong(), INPUT_SIZE.toLong())
        val tensor = OnnxTensor.createTensor(env, FloatBuffer.wrap(input.tensor), inputShape)

        val results = session.run(mapOf(INPUT_NAME to tensor))
        val output = results.first()

        @Suppress("UNCHECKED_CAST")
        val rawOutput = output.value as Array<FloatArray>

        val landmarks = decodeLandmarks(rawOutput, input, frameWidth, frameHeight)
        val confidences = decodeConfidences(rawOutput)

        tensor.close()
        results.close()

        return LandmarkResult(landmarks = landmarks, confidences = confidences)
    }

    /**
     * Decode landmark coordinates from heatmap output.
     *
     * Each landmark has 3 channels in the output: confidence heatmap, x-offset, y-offset.
     * The peak of the confidence heatmap gives coarse location; offsets refine it.
     */
    private fun decodeLandmarks(
        output: Array<FloatArray>,
        input: LandmarkInput,
        frameWidth: Int,
        frameHeight: Int,
    ): FloatArray {
        val landmarks = FloatArray(NUM_LANDMARKS * 2)
        val scaleX = input.cropWidth.toFloat() / INPUT_SIZE
        val scaleY = input.cropHeight.toFloat() / INPUT_SIZE

        for (i in 0 until NUM_LANDMARKS) {
            val confChannel = output[i * CHANNELS_PER_LANDMARK]
            val offXChannel = output[i * CHANNELS_PER_LANDMARK + 1]
            val offYChannel = output[i * CHANNELS_PER_LANDMARK + 2]

            // Find peak in confidence heatmap
            val peakIdx = argmax(confChannel)
            val gridY = peakIdx / OUTPUT_GRID_SIZE
            val gridX = peakIdx % OUTPUT_GRID_SIZE

            // Decode sub-pixel offset via logit
            val offsetX = logit(offXChannel[peakIdx]) * INPUT_SIZE
            val offsetY = logit(offYChannel[peakIdx]) * INPUT_SIZE

            // Convert from heatmap grid to input image coordinates
            val localX = (gridX.toFloat() * INPUT_SIZE / OUTPUT_GRID_SIZE) + offsetX
            val localY = (gridY.toFloat() * INPUT_SIZE / OUTPUT_GRID_SIZE) + offsetY

            // Map back to original frame coordinates (normalized to [0,1])
            val frameX = (input.cropX1 + localX * scaleX) / frameWidth
            val frameY = (input.cropY1 + localY * scaleY) / frameHeight

            landmarks[i * 2] = frameX.coerceIn(0f, 1f)
            landmarks[i * 2 + 1] = frameY.coerceIn(0f, 1f)
        }
        return landmarks
    }

    private fun decodeConfidences(output: Array<FloatArray>): FloatArray {
        val confidences = FloatArray(NUM_LANDMARKS)
        for (i in 0 until NUM_LANDMARKS) {
            val confChannel = output[i * CHANNELS_PER_LANDMARK]
            confidences[i] = confChannel.max()
        }
        return confidences
    }

    companion object {
        private const val INPUT_NAME = "input"
        private const val INPUT_SIZE = 224
        private const val OUTPUT_GRID_SIZE = 27
        private const val NUM_LANDMARKS = 66
        private const val CHANNELS = 3L
        private const val CHANNELS_PER_LANDMARK = 3
        private const val LOGIT_FACTOR = 16.0f
        private const val LOGIT_CLIP_MIN = 0.0000001f
        private const val LOGIT_CLIP_MAX = 0.9999999f

        private fun argmax(arr: FloatArray): Int {
            var maxIdx = 0
            var maxVal = arr[0]
            for (i in 1 until arr.size) {
                if (arr[i] > maxVal) {
                    maxVal = arr[i]
                    maxIdx = i
                }
            }
            return maxIdx
        }

        /**
         * Inverse sigmoid (logit) with clipping and scaling.
         * Matches OpenSeeFace: log(p / (1-p)) / factor
         */
        private fun logit(value: Float): Float {
            val p = value.coerceIn(LOGIT_CLIP_MIN, LOGIT_CLIP_MAX)
            return (ln(p / (1f - p)) / LOGIT_FACTOR)
        }
    }
}

/**
 * Raw landmark inference result.
 *
 * @property landmarks Flat array of 66 (x, y) pairs in normalized [0,1] coordinates. Size = 132.
 * @property confidences Per-landmark confidence scores. Size = 66.
 */
internal data class LandmarkResult(
    val landmarks: FloatArray,
    val confidences: FloatArray,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is LandmarkResult) return false
        return landmarks.contentEquals(other.landmarks) && confidences.contentEquals(other.confidences)
    }

    override fun hashCode(): Int = landmarks.contentHashCode() * 31 + confidences.contentHashCode()
}
