package io.github.kmpfacelink.internal.onnx

import org.bytedeco.javacv.Frame
import org.bytedeco.javacv.Java2DFrameConverter
import java.awt.Graphics2D
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.awt.image.DataBufferByte

/**
 * Image preprocessing utilities for ONNX model inference.
 *
 * Handles frame-to-tensor conversion with ImageNet normalization
 * as used by OpenSeeFace models.
 */
internal object ImagePreprocessor {

    // ImageNet normalization constants pre-combined:
    // normalized = (pixel/255 - mean) / std
    // which equals: pixel/255 * (1/std) + (-mean/std)
    // OpenSeeFace uses: im * std_res + mean_res
    // where mean_res = [-2.117, -2.036, -1.804] and std_res = [4.368, 4.464, 4.444]
    // This is equivalent: result = pixel/255 * std_res + mean_res
    private val STD_SCALE = floatArrayOf(4.368f, 4.464f, 4.444f)
    private val MEAN_OFFSET = floatArrayOf(-2.117f, -2.036f, -1.804f)

    private val converter = Java2DFrameConverter()

    /**
     * Convert a JavaCV Frame to a BufferedImage.
     */
    fun frameToBufferedImage(frame: Frame): BufferedImage? {
        return converter.convert(frame)
    }

    /**
     * Preprocess a full frame for the detection model (224x224).
     *
     * @param image Source image (any size).
     * @param targetSize Output dimension (square).
     * @return Normalized float array in CHW format (3 * targetSize * targetSize).
     */
    fun preprocessForDetection(image: BufferedImage, targetSize: Int): FloatArray {
        val resized = resizeImage(image, targetSize, targetSize)
        return imageToNormalizedCHW(resized, targetSize, targetSize)
    }

    /**
     * Crop a face region from the image and preprocess for the landmark model.
     *
     * @param image Source image.
     * @param box Face bounding box.
     * @param targetSize Output dimension (square).
     * @return Normalized float array in CHW format and the crop coordinates used.
     */
    fun preprocessForLandmark(
        image: BufferedImage,
        box: FaceBox,
        targetSize: Int,
    ): LandmarkInput {
        // Expand bounding box by padding
        val padX = box.width * BBOX_PAD_X
        val padY = box.height * BBOX_PAD_Y
        val cropX1 = (box.x - padX).coerceAtLeast(0f).toInt()
        val cropY1 = (box.y - padY).coerceAtLeast(0f).toInt()
        val cropX2 = (box.x + box.width + padX).coerceAtMost(image.width.toFloat()).toInt()
        val cropY2 = (box.y + box.height + padY).coerceAtMost(image.height.toFloat()).toInt()

        val cropW = (cropX2 - cropX1).coerceAtLeast(1)
        val cropH = (cropY2 - cropY1).coerceAtLeast(1)

        val cropped = image.getSubimage(cropX1, cropY1, cropW, cropH)
        val resized = resizeImage(cropped, targetSize, targetSize)
        val tensor = imageToNormalizedCHW(resized, targetSize, targetSize)

        return LandmarkInput(
            tensor = tensor,
            cropX1 = cropX1,
            cropY1 = cropY1,
            cropWidth = cropW,
            cropHeight = cropH,
        )
    }

    private fun resizeImage(source: BufferedImage, width: Int, height: Int): BufferedImage {
        val resized = BufferedImage(width, height, BufferedImage.TYPE_3BYTE_BGR)
        val g: Graphics2D = resized.createGraphics()
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR)
        g.drawImage(source, 0, 0, width, height, null)
        g.dispose()
        return resized
    }

    /**
     * Convert a BGR BufferedImage to a normalized CHW float array.
     * Layout: [R_channel, G_channel, B_channel] each of size (h * w).
     */
    private fun imageToNormalizedCHW(image: BufferedImage, width: Int, height: Int): FloatArray {
        val dataBuffer = image.raster.dataBuffer as? DataBufferByte
            ?: error(
                "Expected DataBufferByte but got ${image.raster.dataBuffer::class.simpleName}. " +
                    "Ensure the image type is TYPE_3BYTE_BGR.",
            )
        val pixels = dataBuffer.data
        val channelSize = width * height
        val result = FloatArray(CHANNELS * channelSize)

        for (i in 0 until channelSize) {
            // BGR byte order in TYPE_3BYTE_BGR
            val b = (pixels[i * CHANNELS].toInt() and BYTE_MASK).toFloat() / MAX_PIXEL
            val g = (pixels[i * CHANNELS + 1].toInt() and BYTE_MASK).toFloat() / MAX_PIXEL
            val r = (pixels[i * CHANNELS + 2].toInt() and BYTE_MASK).toFloat() / MAX_PIXEL

            // CHW layout with ImageNet normalization: R, G, B channels
            result[i] = r * STD_SCALE[0] + MEAN_OFFSET[0]
            result[channelSize + i] = g * STD_SCALE[1] + MEAN_OFFSET[1]
            result[2 * channelSize + i] = b * STD_SCALE[2] + MEAN_OFFSET[2]
        }
        return result
    }

    private const val CHANNELS = 3
    private const val BYTE_MASK = 0xFF
    private const val MAX_PIXEL = 255f
    private const val BBOX_PAD_X = 0.1f
    private const val BBOX_PAD_Y = 0.125f
}

/**
 * Preprocessed input for the landmark model, including crop metadata.
 */
internal data class LandmarkInput(
    val tensor: FloatArray,
    val cropX1: Int,
    val cropY1: Int,
    val cropWidth: Int,
    val cropHeight: Int,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is LandmarkInput) return false
        return tensor.contentEquals(other.tensor) &&
            cropX1 == other.cropX1 && cropY1 == other.cropY1 &&
            cropWidth == other.cropWidth && cropHeight == other.cropHeight
    }

    override fun hashCode(): Int {
        var result = tensor.contentHashCode()
        result = 31 * result + cropX1
        result = 31 * result + cropY1
        result = 31 * result + cropWidth
        result = 31 * result + cropHeight
        return result
    }
}
