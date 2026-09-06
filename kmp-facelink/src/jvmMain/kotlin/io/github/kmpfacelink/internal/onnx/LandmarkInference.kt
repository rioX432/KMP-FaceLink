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
 * The model outputs a `[1, 198, 28, 28]` tensor: 66 confidence heatmaps,
 * followed by 66 row-offset and 66 column-offset heatmaps.
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
        val inputShape = longArrayOf(1, INPUT_CHANNELS, INPUT_SIZE.toLong(), INPUT_SIZE.toLong())
        return OnnxTensor.createTensor(env, FloatBuffer.wrap(input.tensor), inputShape).use { tensor ->
            session.run(mapOf(INPUT_NAME to tensor)).use { results ->
                OpenSeeFaceLandmarkDecoder.decode(
                    value = results[OUTPUT_NAME].orElseThrow().value,
                    input = input,
                    frameWidth = frameWidth,
                    frameHeight = frameHeight,
                )
            }
        }
    }

    companion object {
        private const val INPUT_NAME = "input"
        private const val OUTPUT_NAME = "output"
        private const val INPUT_SIZE = 224
        private const val INPUT_CHANNELS = 3L
    }
}

/**
 * Decodes the output contract of OpenSeeFace's `lm_model3_opt.onnx`.
 *
 * ONNX Runtime exposes the model's `[1, 198, 28, 28]` float tensor as a nested
 * JVM array. Channels are grouped as 66 confidence maps, 66 row-offset maps,
 * then 66 column-offset maps. They are not interleaved per landmark.
 */
internal object OpenSeeFaceLandmarkDecoder {

    fun decode(
        value: Any?,
        input: LandmarkInput,
        frameWidth: Int,
        frameHeight: Int,
    ): LandmarkResult {
        require(frameWidth > 0) { "frameWidth must be positive" }
        require(frameHeight > 0) { "frameHeight must be positive" }

        val output = requireOutputShape(value)
        val landmarks = decodeLandmarks(output, input, frameWidth, frameHeight)
        val confidences = decodeConfidences(output)
        return LandmarkResult(landmarks = landmarks, confidences = confidences)
    }

    /**
     * Validate the runtime tensor before interpreting its axes or channels.
     * A descriptive contract failure is preferable to an unchecked cast whose
     * exception says nothing about the model/version mismatch.
     */
    internal fun requireOutputShape(value: Any?): Array<Array<FloatArray>> {
        val batches = value as? Array<*>
            ?: throw IllegalArgumentException("Expected landmark output [1,198,28,28], got ${typeName(value)}")
        require(batches.size == BATCH_SIZE) {
            "Expected landmark output batch size $BATCH_SIZE, got ${batches.size}"
        }

        val rawChannels = batches[0] as? Array<*>
            ?: throw IllegalArgumentException("Expected landmark output channels, got ${typeName(batches[0])}")
        require(rawChannels.size == OUTPUT_CHANNELS) {
            "Expected $OUTPUT_CHANNELS landmark output channels, got ${rawChannels.size}"
        }

        return Array(OUTPUT_CHANNELS) { channelIndex ->
            val rawRows = rawChannels[channelIndex] as? Array<*>
                ?: throw IllegalArgumentException(
                    "Expected channel $channelIndex to contain $OUTPUT_GRID_SIZE rows",
                )
            require(rawRows.size == OUTPUT_GRID_SIZE) {
                "Expected channel $channelIndex to contain $OUTPUT_GRID_SIZE rows, got ${rawRows.size}"
            }
            Array(OUTPUT_GRID_SIZE) { rowIndex ->
                val row = rawRows[rowIndex] as? FloatArray
                    ?: throw IllegalArgumentException(
                        "Expected channel $channelIndex row $rowIndex to be FloatArray",
                    )
                require(row.size == OUTPUT_GRID_SIZE) {
                    "Expected channel $channelIndex row $rowIndex to contain " +
                        "$OUTPUT_GRID_SIZE values, got ${row.size}"
                }
                row
            }
        }
    }

    /**
     * Decode landmark coordinates from heatmap output.
     *
     * The peak of each confidence heatmap gives a coarse row/column. Separate
     * grouped offset channels refine those coordinates. OpenSeeFace stores the
     * vertical component before the horizontal component; this method converts
     * them to the public `(x, y)` order.
     */
    private fun decodeLandmarks(
        output: Array<Array<FloatArray>>,
        input: LandmarkInput,
        frameWidth: Int,
        frameHeight: Int,
    ): FloatArray {
        val landmarks = FloatArray(NUM_LANDMARKS * 2)
        val scaleX = input.cropWidth.toFloat() / INPUT_SIZE
        val scaleY = input.cropHeight.toFloat() / INPUT_SIZE

        for (i in 0 until NUM_LANDMARKS) {
            val confChannel = output[i]
            val rowOffsetChannel = output[NUM_LANDMARKS + i]
            val columnOffsetChannel = output[NUM_LANDMARKS * 2 + i]

            // Find peak in confidence heatmap
            val peakIdx = argmax(confChannel)
            val gridRow = peakIdx / OUTPUT_GRID_SIZE
            val gridColumn = peakIdx % OUTPUT_GRID_SIZE

            // Decode sub-pixel offsets via logit, matching OpenSeeFace tracker.py.
            val rowOffset = logit(rowOffsetChannel[gridRow][gridColumn]) * OUTPUT_SPAN
            val columnOffset = logit(columnOffsetChannel[gridRow][gridColumn]) * OUTPUT_SPAN

            // Convert from model grid to crop coordinates.
            val localX = (gridColumn.toFloat() * OUTPUT_SPAN / OUTPUT_GRID_DIVISOR) + columnOffset
            val localY = (gridRow.toFloat() * OUTPUT_SPAN / OUTPUT_GRID_DIVISOR) + rowOffset

            // Map back to original frame coordinates (normalized to [0,1])
            val frameX = (input.cropX1 + localX * scaleX) / frameWidth
            val frameY = (input.cropY1 + localY * scaleY) / frameHeight

            landmarks[i * 2] = frameX.coerceIn(0f, 1f)
            landmarks[i * 2 + 1] = frameY.coerceIn(0f, 1f)
        }
        return landmarks
    }

    private fun decodeConfidences(output: Array<Array<FloatArray>>): FloatArray {
        val confidences = FloatArray(NUM_LANDMARKS)
        for (i in 0 until NUM_LANDMARKS) {
            val confChannel = output[i]
            confidences[i] = confChannel.maxOf { it.max() }
        }
        return confidences
    }

    private fun argmax(channel: Array<FloatArray>): Int {
        var maxIndex = 0
        var maxValue = channel[0][0]
        for (row in channel.indices) {
            for (column in channel[row].indices) {
                val value = channel[row][column]
                if (value > maxValue) {
                    maxValue = value
                    maxIndex = row * OUTPUT_GRID_SIZE + column
                }
            }
        }
        return maxIndex
    }

    private fun typeName(value: Any?): String = value?.let { it::class.qualifiedName } ?: "null"

    private const val BATCH_SIZE = 1
    private const val NUM_LANDMARKS = 66
    private const val OUTPUT_CHANNELS = NUM_LANDMARKS * 3
    private const val OUTPUT_GRID_SIZE = 28
    private const val OUTPUT_GRID_DIVISOR = 27f
    private const val OUTPUT_SPAN = 223f
    private const val INPUT_SIZE = 224f
    private const val LOGIT_FACTOR = 16.0f
    private const val LOGIT_CLIP_MIN = 0.0000001f
    private const val LOGIT_CLIP_MAX = 0.9999999f

    /** Inverse sigmoid (logit) with clipping and scaling, matching OpenSeeFace. */
    private fun logit(value: Float): Float {
        val p = value.coerceIn(LOGIT_CLIP_MIN, LOGIT_CLIP_MAX)
        return ln(p / (1f - p)) / LOGIT_FACTOR
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
