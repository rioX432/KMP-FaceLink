package io.github.kmpfacelink.capture

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import androidx.camera.core.ImageProxy
import androidx.core.graphics.createBitmap
import java.nio.ByteBuffer

/**
 * Turns a camera frame into the exact bitmap inference will see: rotated to display orientation and
 * horizontally mirrored for the front camera, which is what the product does before it hands a
 * frame to MediaPipe.
 *
 * Mirroring is not cosmetic here. The product mirrors, the earlier iPhone captures did not, and the
 * left/right asymmetry in the outputs is a finding in its own right — so a capture that does not
 * reproduce the product's orientation is measuring a different input. Every take records which way
 * it went.
 *
 * **The destination bitmap is fresh every frame and never pooled.** `detectAsync` may still be
 * reading the previous frame while this one is drawn, and a reused destination would be repainted
 * mid-inference: the result would be a well-formed set of landmarks for pixels that no longer
 * exist. The source copy is reused, because it is consumed synchronously into the destination
 * before inference is asked for anything.
 */
internal class FrameTransformer {

    class Output(
        val bitmap: Bitmap,
        val rotationDegrees: Int,
        val width: Int,
        val height: Int,
        val luma: Float,
    )

    private var source: Bitmap? = null
    private val matrix = Matrix()
    private val canvas = Canvas()

    fun transform(proxy: ImageProxy, mirror: Boolean): Output {
        val sourceWidth = proxy.width
        val sourceHeight = proxy.height
        val rotationDegrees = proxy.imageInfo.rotationDegrees

        val cached = source
        val original = if (cached != null && cached.width == sourceWidth && cached.height == sourceHeight) {
            cached
        } else {
            createBitmap(sourceWidth, sourceHeight).also {
                source?.recycle()
                source = it
            }
        }
        proxy.use { original.copyPixelsFromBuffer(it.planes[0].buffer) }

        val turned = rotationDegrees % HALF_TURN != 0
        val width = if (turned) sourceHeight else sourceWidth
        val height = if (turned) sourceWidth else sourceHeight
        val rotated = createBitmap(width, height)

        matrix.reset()
        matrix.postTranslate(-sourceWidth / 2f, -sourceHeight / 2f)
        matrix.postRotate(rotationDegrees.toFloat())
        if (mirror) matrix.postScale(-1f, 1f)
        matrix.postTranslate(width / 2f, height / 2f)

        canvas.setBitmap(rotated)
        canvas.drawBitmap(original, matrix, null)

        return Output(rotated, rotationDegrees, width, height, averageLuma(rotated))
    }

    /** The same bytes inference is about to receive, for the frame payload. */
    fun rgbaOf(bitmap: Bitmap): ByteArray {
        val bytes = ByteArray(bitmap.width * bitmap.height * RawFrameFormat.BYTES_PER_PIXEL)
        bitmap.copyPixelsToBuffer(ByteBuffer.wrap(bytes))
        return bytes
    }

    /** Coarse grid rather than every pixel: this runs on the analyzer thread once per frame. */
    private fun averageLuma(bitmap: Bitmap): Float {
        val stepX = (bitmap.width / LUMA_GRID).coerceAtLeast(1)
        val stepY = (bitmap.height / LUMA_GRID).coerceAtLeast(1)
        var sum = 0.0
        var count = 0
        var y = 0
        while (y < bitmap.height) {
            var x = 0
            while (x < bitmap.width) {
                val c = bitmap.getPixel(x, y)
                sum += RED_WEIGHT * Color.red(c) +
                    GREEN_WEIGHT * Color.green(c) +
                    BLUE_WEIGHT * Color.blue(c)
                count++
                x += stepX
            }
            y += stepY
        }
        return if (count == 0) 0f else (sum / count).toFloat()
    }

    private companion object {
        const val LUMA_GRID = 24
        const val HALF_TURN = 180
        const val RED_WEIGHT = 0.299
        const val GREEN_WEIGHT = 0.587
        const val BLUE_WEIGHT = 0.114
    }
}
