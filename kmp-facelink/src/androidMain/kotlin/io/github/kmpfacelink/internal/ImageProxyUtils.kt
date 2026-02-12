package io.github.kmpfacelink.internal

import android.graphics.Bitmap
import android.graphics.Matrix
import androidx.camera.core.ImageProxy

/**
 * Converts an [ImageProxy] (RGBA_8888 format) to a correctly cropped and rotated [Bitmap].
 *
 * CameraX may deliver buffers larger than the requested resolution and use [ImageProxy.getCropRect]
 * to indicate the valid region. This function applies the crop rect before rotation so that
 * MediaPipe landmark coordinates align with the preview display.
 */
internal fun imageProxyToBitmap(imageProxy: ImageProxy): Bitmap? {
    val plane = imageProxy.planes[0]
    val buffer = plane.buffer
    val fullWidth = imageProxy.width
    val fullHeight = imageProxy.height
    val rowStride = plane.rowStride
    val pixelStride = plane.pixelStride // 4 for RGBA_8888
    val expectedRowBytes = fullWidth * pixelStride

    val fullBitmap = Bitmap.createBitmap(fullWidth, fullHeight, Bitmap.Config.ARGB_8888)

    if (rowStride == expectedRowBytes) {
        buffer.rewind()
        fullBitmap.copyPixelsFromBuffer(buffer)
    } else {
        val packed = java.nio.ByteBuffer.allocateDirect(expectedRowBytes * fullHeight)
        val rowData = ByteArray(rowStride)
        buffer.rewind()
        for (y in 0 until fullHeight) {
            val bytesToRead = if (y < fullHeight - 1) rowStride else expectedRowBytes
            buffer.get(rowData, 0, bytesToRead)
            packed.put(rowData, 0, expectedRowBytes)
        }
        packed.rewind()
        fullBitmap.copyPixelsFromBuffer(packed)
    }

    // Apply cropRect if it differs from the full buffer
    val cropRect = imageProxy.cropRect
    val fullRect = android.graphics.Rect(0, 0, fullWidth, fullHeight)
    val cropped = if (cropRect != fullRect) {
        Bitmap.createBitmap(fullBitmap, cropRect.left, cropRect.top, cropRect.width(), cropRect.height())
    } else {
        fullBitmap
    }

    // Apply rotation
    val rotationDegrees = imageProxy.imageInfo.rotationDegrees
    return if (rotationDegrees != 0) {
        val matrix = Matrix().apply { postRotate(rotationDegrees.toFloat()) }
        Bitmap.createBitmap(cropped, 0, 0, cropped.width, cropped.height, matrix, true)
    } else {
        cropped
    }
}
