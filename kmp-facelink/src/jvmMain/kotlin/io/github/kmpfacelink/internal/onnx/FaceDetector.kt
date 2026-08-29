package io.github.kmpfacelink.internal.onnx

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import java.nio.FloatBuffer

/**
 * Face detection using OpenSeeFace's MobileNetV3-based detection model.
 *
 * Input: 224x224 RGB image normalized with ImageNet constants.
 * Output: Confidence map (1,1,56,56) and radius map (1,1,56,56).
 * Returns bounding boxes as [FaceBox] instances.
 */
internal class FaceDetector(private val session: OrtSession) {

    private val env: OrtEnvironment = OrtEnvironment.getEnvironment()

    /**
     * Detect faces in a preprocessed 224x224 float tensor.
     *
     * @param rgbData Normalized RGB float array in CHW format (3*224*224).
     * @param frameWidth Original frame width for scaling detection boxes.
     * @param frameHeight Original frame height for scaling detection boxes.
     * @return List of detected face bounding boxes.
     */
    fun detect(rgbData: FloatArray, frameWidth: Int, frameHeight: Int): List<FaceBox> {
        val inputShape = longArrayOf(1, CHANNELS.toLong(), INPUT_SIZE.toLong(), INPUT_SIZE.toLong())
        return OnnxTensor.createTensor(env, FloatBuffer.wrap(rgbData), inputShape).use { tensor ->
            session.run(mapOf(INPUT_NAME to tensor)).use { results ->
                val output = requireOutputShape(results[OUTPUT_NAME].orElseThrow().value, OUTPUT_NAME)
                val maxpool = requireOutputShape(results[MAXPOOL_NAME].orElseThrow().value, MAXPOOL_NAME)
                val faces = mutableListOf<FaceBox>()
                val scaleX = frameWidth.toFloat() / INPUT_SIZE
                val scaleY = frameHeight.toFloat() / INPUT_SIZE

                for (y in 0 until OUTPUT_SIZE) {
                    for (x in 0 until OUTPUT_SIZE) {
                        val confidence = output[0][y][x]
                        val isLocalMaximum = confidence == maxpool[0][y][x]
                        if (confidence > DETECTION_THRESHOLD && isLocalMaximum) {
                            val radiusInModel = output[1][y][x] * HALF_INPUT_SIZE
                            val centerX = x.toFloat() * SPATIAL_SCALE * scaleX
                            val centerY = y.toFloat() * SPATIAL_SCALE * scaleY
                            val radiusX = radiusInModel * scaleX
                            val radiusY = radiusInModel * scaleY
                            val x1 = (centerX - radiusX).coerceIn(0f, frameWidth.toFloat())
                            val y1 = (centerY - radiusY).coerceIn(0f, frameHeight.toFloat())
                            val x2 = (centerX + radiusX).coerceIn(0f, frameWidth.toFloat())
                            val y2 = (centerY + radiusY).coerceIn(0f, frameHeight.toFloat())
                            faces.add(
                                FaceBox(
                                    x = x1,
                                    y = y1,
                                    width = x2 - x1,
                                    height = y2 - y1,
                                    confidence = confidence,
                                ),
                            )
                        }
                    }
                }
                suppressOverlapping(faces)
            }
        }
    }

    internal fun requireOutputShape(value: Any?, label: String): Array<Array<FloatArray>> {
        val batches = value as? Array<*>
            ?: throw IllegalArgumentException("$label must be [1,2,56,56]")
        require(batches.size == 1) { "$label batch must contain one item" }
        val rawChannels = batches[0] as? Array<*>
            ?: throw IllegalArgumentException("$label must contain channels")
        require(rawChannels.size == OUTPUT_CHANNELS) {
            "$label must contain $OUTPUT_CHANNELS channels, got ${rawChannels.size}"
        }
        return Array(OUTPUT_CHANNELS) { channelIndex ->
            val rawRows = rawChannels[channelIndex] as? Array<*>
                ?: throw IllegalArgumentException("$label channel $channelIndex must contain rows")
            require(rawRows.size == OUTPUT_SIZE) {
                "$label channel $channelIndex must contain $OUTPUT_SIZE rows, got ${rawRows.size}"
            }
            Array(OUTPUT_SIZE) { rowIndex ->
                val row = rawRows[rowIndex] as? FloatArray
                    ?: throw IllegalArgumentException(
                        "$label channel $channelIndex row $rowIndex must be FloatArray",
                    )
                require(row.size == OUTPUT_SIZE) {
                    "$label channel $channelIndex row $rowIndex must contain " +
                        "$OUTPUT_SIZE values, got ${row.size}"
                }
                row
            }
        }
    }

    companion object {
        private const val INPUT_NAME = "input"
        private const val OUTPUT_NAME = "output"
        private const val MAXPOOL_NAME = "maxpool"
        private const val INPUT_SIZE = 224
        private const val HALF_INPUT_SIZE = 112f
        private const val OUTPUT_SIZE = 56
        private const val OUTPUT_CHANNELS = 2
        private const val CHANNELS = 3
        private const val SPATIAL_SCALE = 4f
        private const val DETECTION_THRESHOLD = 0.6f
        private const val NMS_IOU_THRESHOLD = 0.4f

        /** Simple non-maximum suppression by IOU overlap. */
        private fun suppressOverlapping(boxes: List<FaceBox>): List<FaceBox> {
            val sorted = boxes.sortedByDescending { it.confidence }
            val result = mutableListOf<FaceBox>()
            val suppressed = BooleanArray(sorted.size)
            for (i in sorted.indices) {
                if (suppressed[i]) continue
                result.add(sorted[i])
                for (j in i + 1 until sorted.size) {
                    if (!suppressed[j] && iou(sorted[i], sorted[j]) > NMS_IOU_THRESHOLD) {
                        suppressed[j] = true
                    }
                }
            }
            return result
        }

        private fun iou(a: FaceBox, b: FaceBox): Float {
            val x1 = maxOf(a.x, b.x)
            val y1 = maxOf(a.y, b.y)
            val x2 = minOf(a.x + a.width, b.x + b.width)
            val y2 = minOf(a.y + a.height, b.y + b.height)
            val intersection = maxOf(0f, x2 - x1) * maxOf(0f, y2 - y1)
            val union = a.width * a.height + b.width * b.height - intersection
            return if (union > 0f) intersection / union else 0f
        }
    }
}

/**
 * Detected face bounding box in pixel coordinates.
 */
internal data class FaceBox(
    val x: Float,
    val y: Float,
    val width: Float,
    val height: Float,
    val confidence: Float,
)
