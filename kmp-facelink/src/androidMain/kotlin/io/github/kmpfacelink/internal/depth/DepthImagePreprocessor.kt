package io.github.kmpfacelink.internal.depth

import android.graphics.Bitmap

/**
 * Converts a camera [Bitmap] into the normalized float input tensor expected by the depth model.
 *
 * Output is a flat float array in **NHWC** order `[1, inputSize, inputSize, 3]`, ready for
 * `TensorBuffer.writeFloat`. NOTE: NHWC layout and the normalization constants are
 * model-dependent; the defaults target Depth Anything V2. Validate against the actual model
 * metadata during benchmarking (#898).
 */
internal object DepthImagePreprocessor {

    private const val CHANNELS = 3
    private const val MAX_CHANNEL_VALUE = 255f

    /**
     * Normalizes a single ARGB [pixel] into `[r, g, b]` using [normalization].
     *
     * Extracted as a pure function so the normalization math is unit-testable without a [Bitmap].
     */
    fun normalizeRgb(pixel: Int, normalization: DepthNormalization): FloatArray {
        val r = ((pixel shr 16) and 0xFF) / MAX_CHANNEL_VALUE
        val g = ((pixel shr 8) and 0xFF) / MAX_CHANNEL_VALUE
        val b = (pixel and 0xFF) / MAX_CHANNEL_VALUE
        return floatArrayOf(
            (r - normalization.r.mean) / normalization.r.std,
            (g - normalization.g.mean) / normalization.g.std,
            (b - normalization.b.mean) / normalization.b.std,
        )
    }

    /**
     * Resizes [bitmap] to [inputSize] x [inputSize] and returns normalized RGB pixels as a flat
     * NHWC float array of length `inputSize * inputSize * 3`.
     */
    fun toInputFloats(
        bitmap: Bitmap,
        inputSize: Int,
        normalization: DepthNormalization,
    ): FloatArray {
        val scaled = Bitmap.createScaledBitmap(bitmap, inputSize, inputSize, true)
        val pixels = IntArray(inputSize * inputSize)
        scaled.getPixels(pixels, 0, inputSize, 0, 0, inputSize, inputSize)

        val output = FloatArray(inputSize * inputSize * CHANNELS)
        var i = 0
        for (pixel in pixels) {
            val (r, g, b) = normalizeRgb(pixel, normalization)
            output[i++] = r
            output[i++] = g
            output[i++] = b
        }

        if (scaled !== bitmap) {
            scaled.recycle()
        }
        return output
    }
}
