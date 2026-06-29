package io.github.kmpfacelink.internal

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Matrix
import androidx.camera.core.ImageProxy

/**
 * Converts [ImageProxy] frames (RGBA_8888) to correctly oriented [Bitmap]s
 * with optional horizontal mirroring for front-camera pre-processing.
 *
 * Reuses a source bitmap and a [BitmapPool] for output bitmaps to eliminate
 * per-frame allocation and GC pressure.
 *
 * All public methods are `@Synchronized` to prevent concurrent access to
 * mutable bitmap state (source bitmap, packed buffer, row data). This ensures
 * that [release] cannot recycle a bitmap while [convert] is still drawing.
 */
internal class ImageProxyConverter {

    private var sourceBitmap: Bitmap? = null
    private val bitmapPool = BitmapPool()
    private val transformMatrix = Matrix()

    // Cached buffers for stride-mismatch path to avoid per-frame allocation
    private var packedBuffer: java.nio.ByteBuffer? = null
    private var packedBufferCapacity: Int = 0
    private var rowData: ByteArray? = null
    private var rowDataCapacity: Int = 0

    @Synchronized
    fun convert(imageProxy: ImageProxy, mirrorHorizontally: Boolean = false): Bitmap? {
        val fullWidth = imageProxy.width
        val fullHeight = imageProxy.height

        val src = getSourceBitmap(fullWidth, fullHeight)
        copyPixels(imageProxy, src)

        // Build transform: rotate + optional mirror
        val rotationDegrees = imageProxy.imageInfo.rotationDegrees
        transformMatrix.reset()

        val cx = fullWidth / 2f
        val cy = fullHeight / 2f
        if (rotationDegrees != 0) {
            transformMatrix.postTranslate(-cx, -cy)
            transformMatrix.postRotate(rotationDegrees.toFloat())
        }

        val outWidth: Int
        val outHeight: Int
        if (rotationDegrees == ROTATION_90 || rotationDegrees == ROTATION_270) {
            outWidth = fullHeight
            outHeight = fullWidth
        } else {
            outWidth = fullWidth
            outHeight = fullHeight
        }

        if (mirrorHorizontally) {
            if (rotationDegrees != 0) {
                transformMatrix.postScale(-1f, 1f)
            } else {
                transformMatrix.postTranslate(-cx, -cy)
                transformMatrix.postScale(-1f, 1f)
            }
        }

        transformMatrix.postTranslate(outWidth / 2f, outHeight / 2f)

        val output = bitmapPool.getBitmap(outWidth, outHeight)
        Canvas(output).drawBitmap(src, transformMatrix, null)
        return output
    }

    private fun getSourceBitmap(width: Int, height: Int): Bitmap =
        sourceBitmap?.takeIf { it.width == width && it.height == height }
            ?: Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).also {
                sourceBitmap?.recycle()
                sourceBitmap = it
            }

    private fun copyPixels(imageProxy: ImageProxy, dest: Bitmap) {
        val plane = imageProxy.planes[0]
        val buffer = plane.buffer
        val fullWidth = imageProxy.width
        val fullHeight = imageProxy.height
        val rowStride = plane.rowStride
        val expectedRowBytes = fullWidth * plane.pixelStride

        if (rowStride == expectedRowBytes) {
            buffer.rewind()
            dest.copyPixelsFromBuffer(buffer)
            return
        }

        val packed = obtainPackedBuffer(expectedRowBytes * fullHeight)
        val row = obtainRowBuffer(rowStride)
        buffer.rewind()
        for (y in 0 until fullHeight) {
            val bytesToRead = if (y < fullHeight - 1) rowStride else expectedRowBytes
            buffer.get(row, 0, bytesToRead)
            packed.put(row, 0, expectedRowBytes)
        }
        packed.rewind()
        dest.copyPixelsFromBuffer(packed)
    }

    /** Returns a cleared direct buffer of at least [requiredCapacity] bytes, reusing the cached one when large enough. */
    private fun obtainPackedBuffer(requiredCapacity: Int): java.nio.ByteBuffer {
        val cached = packedBuffer
        if (cached != null && packedBufferCapacity >= requiredCapacity) {
            return cached.also { it.clear() }
        }
        return java.nio.ByteBuffer.allocateDirect(requiredCapacity).also {
            packedBuffer = it
            packedBufferCapacity = requiredCapacity
        }
    }

    /** Returns a row scratch array of at least [rowStride] bytes, reusing the cached one when large enough. */
    private fun obtainRowBuffer(rowStride: Int): ByteArray {
        val cached = rowData
        if (cached != null && rowDataCapacity >= rowStride) {
            return cached
        }
        return ByteArray(rowStride).also {
            rowData = it
            rowDataCapacity = rowStride
        }
    }

    @Synchronized
    fun returnBitmap(bitmap: Bitmap) {
        bitmapPool.returnBitmap(bitmap)
    }

    @Synchronized
    fun release() {
        sourceBitmap?.recycle()
        sourceBitmap = null
        bitmapPool.clear()
        packedBuffer = null
        packedBufferCapacity = 0
        rowData = null
        rowDataCapacity = 0
    }

    private companion object {
        const val ROTATION_90 = 90
        const val ROTATION_270 = 270
    }
}
