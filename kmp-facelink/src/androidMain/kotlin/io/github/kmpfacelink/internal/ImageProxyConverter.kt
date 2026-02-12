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
        @Suppress("MagicNumber")
        if (rotationDegrees == 90 || rotationDegrees == 270) {
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
        } else {
            val requiredCapacity = expectedRowBytes * fullHeight
            val packed = if (packedBufferCapacity >= requiredCapacity) {
                packedBuffer!!.also { it.clear() }
            } else {
                java.nio.ByteBuffer.allocateDirect(requiredCapacity).also {
                    packedBuffer = it
                    packedBufferCapacity = requiredCapacity
                }
            }
            val row = if (rowDataCapacity >= rowStride) {
                rowData!!
            } else {
                ByteArray(rowStride).also {
                    rowData = it
                    rowDataCapacity = rowStride
                }
            }
            buffer.rewind()
            for (y in 0 until fullHeight) {
                val bytesToRead = if (y < fullHeight - 1) rowStride else expectedRowBytes
                buffer.get(row, 0, bytesToRead)
                packed.put(row, 0, expectedRowBytes)
            }
            packed.rewind()
            dest.copyPixelsFromBuffer(packed)
        }
    }

    fun returnBitmap(bitmap: Bitmap) {
        bitmapPool.returnBitmap(bitmap)
    }

    fun release() {
        sourceBitmap?.recycle()
        sourceBitmap = null
        bitmapPool.clear()
        packedBuffer = null
        packedBufferCapacity = 0
        rowData = null
        rowDataCapacity = 0
    }
}
