@file:OptIn(ExperimentalForeignApi::class)

package io.github.kmpfacelink.capture

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.UByteVar
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.convert
import kotlinx.cinterop.get
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.usePinned
import platform.CoreGraphics.CGColorSpaceCreateDeviceRGB
import platform.CoreImage.CIContext
import platform.CoreImage.CIImage
import platform.CoreImage.kCIFormatRGBA8
import platform.CoreVideo.CVPixelBufferGetBaseAddressOfPlane
import platform.CoreVideo.CVPixelBufferGetBytesPerRowOfPlane
import platform.CoreVideo.CVPixelBufferGetHeight
import platform.CoreVideo.CVPixelBufferGetHeightOfPlane
import platform.CoreVideo.CVPixelBufferGetPlaneCount
import platform.CoreVideo.CVPixelBufferGetWidth
import platform.CoreVideo.CVPixelBufferGetWidthOfPlane
import platform.CoreVideo.CVPixelBufferLockBaseAddress
import platform.CoreVideo.CVPixelBufferRef
import platform.CoreVideo.CVPixelBufferUnlockBaseAddress
import platform.CoreVideo.kCVPixelBufferLock_ReadOnly

internal class IosFrameConverter {
    data class Geometry(val width: Int, val height: Int)
    data class Output(val width: Int, val height: Int, val pixels: ByteArray)

    private val context = CIContext.contextWithOptions(null)
    private val colorSpace = CGColorSpaceCreateDeviceRGB()

    fun geometry(pixelBuffer: CVPixelBufferRef): Geometry = Geometry(
        width = CVPixelBufferGetWidth(pixelBuffer).toInt(),
        height = CVPixelBufferGetHeight(pixelBuffer).toInt(),
    )

    fun rgba(pixelBuffer: CVPixelBufferRef): Output {
        val geometry = geometry(pixelBuffer)
        val pixels = ByteArray(geometry.width * geometry.height * RawFrameFormat.BYTES_PER_PIXEL)
        val image = CIImage.imageWithCVPixelBuffer(pixelBuffer)
        pixels.usePinned { pinned ->
            context.render(
                image,
                toBitmap = pinned.addressOf(0),
                rowBytes = (geometry.width * RawFrameFormat.BYTES_PER_PIXEL).convert(),
                bounds = image.extent,
                format = kCIFormatRGBA8,
                colorSpace = colorSpace,
            )
        }
        // Captured camera images are opaque. Stamping that invariant makes the stored RGBA
        // straight rather than leaving its interpretation to Core Image's bitmap alpha mode.
        for (offset in ALPHA_OFFSET until pixels.size step RawFrameFormat.BYTES_PER_PIXEL) {
            pixels[offset] = OPAQUE_ALPHA
        }
        return Output(geometry.width, geometry.height, pixels)
    }

    /** The first plane of ARKit's bi-planar camera buffer is full-range luma. */
    fun averageLuma(pixelBuffer: CVPixelBufferRef): Float {
        check(CVPixelBufferGetPlaneCount(pixelBuffer) > 0uL) { "ARKit camera buffer has no luma plane" }
        check(CVPixelBufferLockBaseAddress(pixelBuffer, kCVPixelBufferLock_ReadOnly) == 0) {
            "could not lock ARKit camera buffer"
        }
        try {
            val base = checkNotNull(CVPixelBufferGetBaseAddressOfPlane(pixelBuffer, 0uL))
                .reinterpret<UByteVar>()
            val width = CVPixelBufferGetWidthOfPlane(pixelBuffer, 0uL).toInt()
            val height = CVPixelBufferGetHeightOfPlane(pixelBuffer, 0uL).toInt()
            val stride = CVPixelBufferGetBytesPerRowOfPlane(pixelBuffer, 0uL).toInt()
            var total = 0L
            var samples = 0
            var y = 0
            while (y < height) {
                var x = 0
                while (x < width) {
                    total += base[y * stride + x].toLong()
                    samples++
                    x += SAMPLE_STEP
                }
                y += SAMPLE_STEP
            }
            return total.toFloat() / samples
        } finally {
            CVPixelBufferUnlockBaseAddress(pixelBuffer, kCVPixelBufferLock_ReadOnly)
        }
    }

    private companion object {
        const val ALPHA_OFFSET = 3
        const val OPAQUE_ALPHA: Byte = -1
        const val SAMPLE_STEP = 16
    }
}
