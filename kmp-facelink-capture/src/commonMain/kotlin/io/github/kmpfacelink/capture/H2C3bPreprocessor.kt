@file:Suppress("MagicNumber", "NestedBlockDepth", "ReturnCount", "TooManyFunctions")

package io.github.kmpfacelink.capture

import io.github.kmpfacelink.model.FaceLandmark
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

/** The frozen H2 image/landmark orientation. Both planes have already been front-camera mirrored. */
internal enum class H2FrameOrientation {
    FRONT_CAMERA_MIRRORED_RGBA_AND_LANDMARKS,
}

/** One exact MediaPipe input admitted to the frozen H2 preprocessor. */
internal class H2RgbaLandmarkFrame(
    rgba: ByteArray,
    val width: Int,
    val height: Int,
    landmarks: List<FaceLandmark>,
    val orientation: H2FrameOrientation,
) {
    val rgba: ByteArray = rgba.copyOf()
    val landmarks: List<FaceLandmark> = landmarks.toList()

    init {
        require(width == WIDTH && height == HEIGHT) { "H2 requires canonical 240x320 RGBA" }
        require(this.rgba.size == width * height * RGBA_CHANNELS) { "invalid RGBA byte count" }
        require(this.landmarks.size == LANDMARK_COUNT) { "H2 requires exactly 478 landmarks" }
        require(this.landmarks.all { it.x.isFinite() && it.y.isFinite() }) { "landmark x/y must be finite" }
        require(orientation == H2FrameOrientation.FRONT_CAMERA_MIRRORED_RGBA_AND_LANDMARKS) {
            "image and landmarks must share the frozen mirrored front-camera orientation"
        }
    }

    companion object {
        const val WIDTH: Int = 240
        const val HEIGHT: Int = 320
        const val LANDMARK_COUNT: Int = 478
        const val RGBA_CHANNELS: Int = 4
    }
}

internal enum class H2FeatureReadiness { CALIBRATING, READY, REJECTED }

internal data class H2FeatureObservation(
    val readiness: H2FeatureReadiness,
    val values: List<Float>?,
    val reason: String?,
)

/**
 * Frozen C3b RGBA-to-384 implementation.
 *
 * Calibration accepts every joined frame in the exact `(1500, 3000]` millisecond take-local
 * window. The pixelwise median/MAD template is closed only when the first post-window frame is
 * observed, and at least 15 calibration frames are required. Inputs are snapshots and no frame,
 * landmark, or feature is persisted by this class after the in-memory take state is reset.
 */
internal class H2C3bPreprocessor {
    private val calibration: MutableList<FloatArray> = mutableListOf()
    private var template: FloatArray? = null
    private var scale: FloatArray? = null
    private var rejectedReason: String? = null

    fun observeCalibration(frame: H2RgbaLandmarkFrame, elapsedMillis: Long): H2FeatureObservation {
        rejectedReason?.let { return rejected(it) }
        if (template != null) return H2FeatureObservation(H2FeatureReadiness.READY, null, null)
        if (elapsedMillis <= CALIBRATION_BURN_IN_MILLIS) {
            return H2FeatureObservation(H2FeatureReadiness.CALIBRATING, null, "c3b_calibration_burn_in")
        }
        if (elapsedMillis > CALIBRATION_END_MILLIS) {
            return reject("c3b_calibration_window_closed")
        }
        val aligned = alignGray(frame) ?: return reject("c3b_alignment_rejected")
        calibration += aligned
        return H2FeatureObservation(H2FeatureReadiness.CALIBRATING, null, "c3b_calibration_pending")
    }

    fun observeEvaluation(frame: H2RgbaLandmarkFrame, elapsedMillis: Long): H2FeatureObservation {
        rejectedReason?.let { return rejected(it) }
        if (elapsedMillis <= CALIBRATION_END_MILLIS) {
            return H2FeatureObservation(H2FeatureReadiness.CALIBRATING, null, "c3b_calibration_window_open")
        }
        if (template == null) {
            if (calibration.size < MIN_CALIBRATION_FRAMES) return reject("c3b_calibration_insufficient")
            closeCalibration()
        }
        val neutral = checkNotNull(template)
        val divisor = checkNotNull(scale)
        val aligned = alignGray(frame) ?: return reject("c3b_alignment_rejected")
        val normalized = FloatArray(ALIGNED_PIXELS) { index ->
            ((aligned[index] - neutral[index]) / divisor[index]).coerceIn(-CLIP, CLIP)
        }
        val feature = encodeFeature384(normalized)
        return H2FeatureObservation(H2FeatureReadiness.READY, feature.toList(), null)
    }

    fun reset() {
        calibration.clear()
        template = null
        scale = null
        rejectedReason = null
    }

    private fun closeCalibration() {
        val neutral = FloatArray(ALIGNED_PIXELS)
        val divisor = FloatArray(ALIGNED_PIXELS)
        val scratch = FloatArray(calibration.size)
        for (pixel in 0 until ALIGNED_PIXELS) {
            for (index in calibration.indices) scratch[index] = calibration[index][pixel]
            scratch.sort()
            val median = medianOfSorted(scratch)
            neutral[pixel] = median
            for (index in calibration.indices) scratch[index] = abs(calibration[index][pixel] - median)
            scratch.sort()
            divisor[pixel] = max(MAD_FACTOR * medianOfSorted(scratch), MIN_SCALE)
        }
        calibration.clear()
        template = neutral
        scale = divisor
    }

    private fun reject(reason: String): H2FeatureObservation {
        rejectedReason = reason
        calibration.clear()
        template = null
        scale = null
        return rejected(reason)
    }

    private fun rejected(reason: String): H2FeatureObservation =
        H2FeatureObservation(H2FeatureReadiness.REJECTED, null, reason)

    private fun alignGray(frame: H2RgbaLandmarkFrame): FloatArray? {
        val points = frame.landmarks
        val sourceX = doubleArrayOf(
            ((points[33].x + points[133].x) * 0.5f * frame.width).toDouble(),
            ((points[362].x + points[263].x) * 0.5f * frame.width).toDouble(),
            (points[1].x * frame.width).toDouble(),
        )
        val sourceY = doubleArrayOf(
            ((points[33].y + points[133].y) * 0.5f * frame.height).toDouble(),
            ((points[362].y + points[263].y) * 0.5f * frame.height).toDouble(),
            (points[1].y * frame.height).toDouble(),
        )
        val forward = affineFromTriangles(sourceX, sourceY, TARGET_X, TARGET_Y) ?: return null
        val inverse = forward.inverse() ?: return null
        val gray = rgbaToGray(frame.rgba)
        val aligned = FloatArray(ALIGNED_PIXELS)
        for (y in 0 until ALIGNED_SIZE) {
            for (x in 0 until ALIGNED_SIZE) {
                val source = inverse.map(x.toDouble(), y.toDouble())
                aligned[y * ALIGNED_SIZE + x] = bilinearZeroBorder(
                    gray,
                    frame.width,
                    frame.height,
                    source.first,
                    source.second,
                )
            }
        }
        return aligned.takeIf { values -> values.all(Float::isFinite) }
    }

    private fun rgbaToGray(rgba: ByteArray): FloatArray {
        val gray = FloatArray(H2RgbaLandmarkFrame.WIDTH * H2RgbaLandmarkFrame.HEIGHT)
        for (pixel in gray.indices) {
            val offset = pixel * H2RgbaLandmarkFrame.RGBA_CHANNELS
            val red = rgba[offset].toInt() and 0xff
            val green = rgba[offset + 1].toInt() and 0xff
            val blue = rgba[offset + 2].toInt() and 0xff
            val encoded = (red * RGB_RED + green * RGB_GREEN + blue * RGB_BLUE + RGB_ROUND) shr RGB_SHIFT
            gray[pixel] = encoded / 255f
        }
        return gray
    }

    private fun encodeFeature384(normalized: FloatArray): FloatArray {
        val left = centeredAreaResize(normalized, LEFT_X, CHEEK_Y)
        val right = centeredAreaResize(normalized, RIGHT_X, CHEEK_Y, flipHorizontal = true)
        return FloatArray(FEATURE_WIDTH) { index ->
            if (index < PATCH_OUTPUT_PIXELS) left[index] else right[index - PATCH_OUTPUT_PIXELS]
        }
    }

    private fun centeredAreaResize(
        source: FloatArray,
        xRange: IntRange,
        yRange: IntRange,
        flipHorizontal: Boolean = false,
    ): FloatArray {
        val patchWidth = xRange.count()
        val patchHeight = yRange.count()
        val patch = FloatArray(patchWidth * patchHeight)
        var sum = 0.0
        for (y in 0 until patchHeight) {
            for (x in 0 until patchWidth) {
                val sourceX = if (flipHorizontal) xRange.last - x else xRange.first + x
                val value = source[(yRange.first + y) * ALIGNED_SIZE + sourceX]
                patch[y * patchWidth + x] = value
                sum += value
            }
        }
        val mean = (sum / patch.size).toFloat()
        for (index in patch.indices) patch[index] -= mean
        return areaResize(patch, patchWidth, patchHeight, OUTPUT_WIDTH, OUTPUT_HEIGHT)
    }

    private fun areaResize(
        source: FloatArray,
        sourceWidth: Int,
        sourceHeight: Int,
        targetWidth: Int,
        targetHeight: Int,
    ): FloatArray {
        val output = FloatArray(targetWidth * targetHeight)
        val scaleX = sourceWidth.toDouble() / targetWidth
        val scaleY = sourceHeight.toDouble() / targetHeight
        val area = scaleX * scaleY
        for (targetY in 0 until targetHeight) {
            val y0 = targetY * scaleY
            val y1 = (targetY + 1) * scaleY
            val firstY = floor(y0).toInt()
            val lastY = min(sourceHeight - 1, floor(y1 - EPSILON).toInt())
            for (targetX in 0 until targetWidth) {
                val x0 = targetX * scaleX
                val x1 = (targetX + 1) * scaleX
                val firstX = floor(x0).toInt()
                val lastX = min(sourceWidth - 1, floor(x1 - EPSILON).toInt())
                var weighted = 0.0
                for (sourceY in firstY..lastY) {
                    val yWeight = min(y1, sourceY + 1.0) - max(y0, sourceY.toDouble())
                    for (sourceX in firstX..lastX) {
                        val xWeight = min(x1, sourceX + 1.0) - max(x0, sourceX.toDouble())
                        weighted += source[sourceY * sourceWidth + sourceX] * xWeight * yWeight
                    }
                }
                output[targetY * targetWidth + targetX] = (weighted / area).toFloat()
            }
        }
        return output
    }

    private fun bilinearZeroBorder(
        source: FloatArray,
        width: Int,
        height: Int,
        x: Double,
        y: Double,
    ): Float {
        val x0 = floor(x).toInt()
        val y0 = floor(y).toInt()
        val dx = (x - x0).toFloat()
        val dy = (y - y0).toFloat()
        fun sample(px: Int, py: Int): Float =
            if (px in 0 until width && py in 0 until height) source[py * width + px] else 0f
        val top = sample(x0, y0) * (1f - dx) + sample(x0 + 1, y0) * dx
        val bottom = sample(x0, y0 + 1) * (1f - dx) + sample(x0 + 1, y0 + 1) * dx
        return top * (1f - dy) + bottom * dy
    }

    private fun affineFromTriangles(
        sourceX: DoubleArray,
        sourceY: DoubleArray,
        targetX: DoubleArray,
        targetY: DoubleArray,
    ): Affine? {
        val denominator = sourceX[0] * (sourceY[1] - sourceY[2]) +
            sourceX[1] * (sourceY[2] - sourceY[0]) + sourceX[2] * (sourceY[0] - sourceY[1])
        if (!denominator.isFinite() || abs(denominator) < DEGENERATE_EPSILON) return null
        fun coefficients(values: DoubleArray): Triple<Double, Double, Double> {
            val a = (
                values[0] * (sourceY[1] - sourceY[2]) +
                    values[1] * (sourceY[2] - sourceY[0]) +
                    values[2] * (sourceY[0] - sourceY[1])
                ) / denominator
            val b = (
                values[0] * (sourceX[2] - sourceX[1]) +
                    values[1] * (sourceX[0] - sourceX[2]) +
                    values[2] * (sourceX[1] - sourceX[0])
                ) / denominator
            val c = (
                values[0] * (sourceX[1] * sourceY[2] - sourceX[2] * sourceY[1]) +
                    values[1] * (sourceX[2] * sourceY[0] - sourceX[0] * sourceY[2]) +
                    values[2] * (sourceX[0] * sourceY[1] - sourceX[1] * sourceY[0])
                ) / denominator
            return Triple(a, b, c)
        }
        val x = coefficients(targetX)
        val y = coefficients(targetY)
        return Affine(x.first, x.second, x.third, y.first, y.second, y.third)
    }

    private data class Affine(
        val a: Double,
        val b: Double,
        val c: Double,
        val d: Double,
        val e: Double,
        val f: Double,
    ) {
        fun inverse(): Affine? {
            val determinant = a * e - b * d
            if (!determinant.isFinite() || abs(determinant) < DEGENERATE_EPSILON) return null
            return Affine(
                e / determinant,
                -b / determinant,
                (b * f - e * c) / determinant,
                -d / determinant,
                a / determinant,
                (d * c - a * f) / determinant,
            )
        }

        fun map(x: Double, y: Double): Pair<Double, Double> = Pair(a * x + b * y + c, d * x + e * y + f)
    }

    private fun medianOfSorted(values: FloatArray): Float {
        val middle = values.size / 2
        return if (values.size % 2 == 1) values[middle] else (values[middle - 1] + values[middle]) * 0.5f
    }

    companion object {
        const val FEATURE_KEY: String = "research:c3b-static-cheek-patch/features384/v1"
        const val FEATURE_WIDTH: Int = 384
        const val MIN_CALIBRATION_FRAMES: Int = 15
        const val CALIBRATION_BURN_IN_MILLIS: Long = 1_500L
        const val CALIBRATION_END_MILLIS: Long = 3_000L
        private const val ALIGNED_SIZE: Int = 128
        private const val ALIGNED_PIXELS: Int = ALIGNED_SIZE * ALIGNED_SIZE
        private const val OUTPUT_WIDTH: Int = 12
        private const val OUTPUT_HEIGHT: Int = 16
        private const val PATCH_OUTPUT_PIXELS: Int = OUTPUT_WIDTH * OUTPUT_HEIGHT
        private const val MAD_FACTOR: Float = 1.4826f
        private const val MIN_SCALE: Float = 1f / 255f
        private const val CLIP: Float = 5f
        private const val RGB_RED: Int = 9798
        private const val RGB_GREEN: Int = 19235
        private const val RGB_BLUE: Int = 3735
        private const val RGB_ROUND: Int = 1 shl 14
        private const val RGB_SHIFT: Int = 15
        private const val DEGENERATE_EPSILON: Double = 1e-8
        private const val EPSILON: Double = 1e-12
        private val TARGET_X: DoubleArray = doubleArrayOf(40.0, 88.0, 64.0)
        private val TARGET_Y: DoubleArray = doubleArrayOf(44.0, 44.0, 72.0)
        private val LEFT_X: IntRange = 12 until 52
        private val RIGHT_X: IntRange = 76 until 116
        private val CHEEK_Y: IntRange = 52 until 100
    }
}
