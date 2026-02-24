package io.github.kmpfacelink.internal

import android.graphics.Bitmap

/**
 * Circular bitmap pool that reuses ARGB_8888 bitmaps to eliminate GC pressure.
 *
 * When the requested dimensions change, all pooled bitmaps are recycled and
 * new ones are allocated on demand.
 */
internal class BitmapPool(
    private val config: Bitmap.Config = Bitmap.Config.ARGB_8888,
    private val poolSize: Int = 4,
) {
    private var width = 0
    private var height = 0
    private val queue = ArrayDeque<Bitmap>()

    @Synchronized
    fun getBitmap(width: Int, height: Int): Bitmap {
        if (width != this.width || height != this.height) {
            clearInternal()
            this.width = width
            this.height = height
        }

        val reused = queue.removeFirstOrNull()
        if (reused != null) {
            // No eraseColor — the bitmap will be fully overwritten by Canvas.drawBitmap
            return reused
        }
        return Bitmap.createBitmap(width, height, config)
    }

    @Synchronized
    fun returnBitmap(bitmap: Bitmap) {
        if (bitmap.isRecycled) return
        if (bitmap.width != width || bitmap.height != height) {
            bitmap.recycle()
            return
        }
        if (queue.size >= poolSize) {
            queue.removeFirst().recycle()
        }
        queue.addLast(bitmap)
    }

    @Synchronized
    fun clear() {
        clearInternal()
    }

    private fun clearInternal() {
        queue.forEach { it.recycle() }
        queue.clear()
    }
}
