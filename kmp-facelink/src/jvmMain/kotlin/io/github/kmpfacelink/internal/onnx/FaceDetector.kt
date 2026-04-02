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
        val tensor = OnnxTensor.createTensor(env, FloatBuffer.wrap(rgbData), inputShape)

        val results = session.run(mapOf(INPUT_NAME to tensor))
        val outputNames = results.map { it.key }

        // OpenSeeFace detection outputs: confidence + radius maps
        val confOutput = results[outputNames[0]].get().value
        val faces = mutableListOf<FaceBox>()

        @Suppress("UNCHECKED_CAST")
        val confMap = confOutput as Array<Array<Array<FloatArray>>>
        val scaleX = frameWidth.toFloat() / INPUT_SIZE
        val scaleY = frameHeight.toFloat() / INPUT_SIZE

        for (y in 0 until OUTPUT_SIZE) {
            for (x in 0 until OUTPUT_SIZE) {
                val confidence = confMap[0][0][y][x]
                if (confidence > DETECTION_THRESHOLD) {
                    val radiusVal = if (outputNames.size > 1) {
                        @Suppress("UNCHECKED_CAST")
                        val radMap = results[outputNames[1]].get().value as Array<Array<Array<FloatArray>>>
                        radMap[0][0][y][x] * HALF_INPUT_SIZE
                    } else {
                        DEFAULT_RADIUS
                    }
                    val cx = x.toFloat() * SPATIAL_SCALE * scaleX
                    val cy = y.toFloat() * SPATIAL_SCALE * scaleY
                    val radius = radiusVal * scaleX
                    faces.add(
                        FaceBox(
                            x = (cx - radius).coerceAtLeast(0f),
                            y = (cy - radius).coerceAtLeast(0f),
                            width = (radius * 2f).coerceAtMost(frameWidth.toFloat()),
                            height = (radius * 2f).coerceAtMost(frameHeight.toFloat()),
                            confidence = confidence,
                        ),
                    )
                }
            }
        }
        tensor.close()
        results.close()

        return suppressOverlapping(faces)
    }

    companion object {
        private const val INPUT_NAME = "input"
        private const val INPUT_SIZE = 224
        private const val HALF_INPUT_SIZE = 112f
        private const val OUTPUT_SIZE = 56
        private const val CHANNELS = 3
        private const val SPATIAL_SCALE = 4f
        private const val DETECTION_THRESHOLD = 0.6f
        private const val DEFAULT_RADIUS = 50f
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
