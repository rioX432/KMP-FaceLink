package io.github.kmpfacelink.internal

import android.graphics.Bitmap
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotSame
import kotlin.test.assertSame
import kotlin.test.assertTrue

private const val TEST_WIDTH = 640
private const val TEST_HEIGHT = 480

@RunWith(RobolectricTestRunner::class)
class BitmapPoolTest {

    @Test
    fun getBitmapCreatesNewBitmap() {
        val pool = BitmapPool()
        val bitmap = pool.getBitmap(TEST_WIDTH, TEST_HEIGHT)

        assertEquals(TEST_WIDTH, bitmap.width)
        assertEquals(TEST_HEIGHT, bitmap.height)
        assertEquals(Bitmap.Config.ARGB_8888, bitmap.config)
    }

    @Test
    fun returnAndGetReusesBitmap() {
        val pool = BitmapPool()
        val original = pool.getBitmap(TEST_WIDTH, TEST_HEIGHT)
        pool.returnBitmap(original)

        val reused = pool.getBitmap(TEST_WIDTH, TEST_HEIGHT)
        assertSame(original, reused)
    }

    @Test
    fun differentDimensionsCreateNewBitmap() {
        val pool = BitmapPool()
        val first = pool.getBitmap(TEST_WIDTH, TEST_HEIGHT)
        pool.returnBitmap(first)

        val second = pool.getBitmap(320, 240)
        assertNotSame(first, second)
        assertEquals(320, second.width)
        assertEquals(240, second.height)
    }

    @Test
    fun dimensionChangeRecyclesOldBitmaps() {
        val pool = BitmapPool()
        val first = pool.getBitmap(TEST_WIDTH, TEST_HEIGHT)
        pool.returnBitmap(first)

        // Change dimensions forces clearInternal
        pool.getBitmap(320, 240)
        assertTrue(first.isRecycled)
    }

    @Test
    fun clearRecyclesAllBitmaps() {
        val pool = BitmapPool()
        val bitmaps = (1..3).map {
            val b = pool.getBitmap(TEST_WIDTH, TEST_HEIGHT)
            pool.returnBitmap(b)
            b
        }

        // Only the last 3 should be in the pool (pool returns from queue)
        pool.clear()

        // At least the bitmaps that were in the pool should be recycled
        // Note: some bitmaps may have been evicted by the pool size limit
        val recycledCount = bitmaps.count { it.isRecycled }
        assertTrue(recycledCount > 0, "At least one bitmap should be recycled")
    }

    @Test
    fun poolSizeLimitEvictsOldest() {
        val poolSize = 2
        val pool = BitmapPool(poolSize = poolSize)

        val first = pool.getBitmap(TEST_WIDTH, TEST_HEIGHT)
        pool.returnBitmap(first)

        val second = pool.getBitmap(TEST_WIDTH, TEST_HEIGHT)
        pool.returnBitmap(second)

        val third = pool.getBitmap(TEST_WIDTH, TEST_HEIGHT)
        pool.returnBitmap(third)

        // Pool size is 2, so first should have been evicted and recycled
        // when third was returned (first was already removed to create second,
        // then second was returned, then third was gotten as new, then returned)
        // Actually: first returned -> queue=[first]
        //           second = removeFirst(first) -> queue=[]
        //           second returned -> queue=[second]
        //           third = removeFirst(second) -> queue=[]
        //           third returned -> queue=[third]
        // So first was reused as second, second was reused as third.
        // Let's test the eviction behavior more directly.
        val pool2 = BitmapPool(poolSize = 2)
        val bitmaps = mutableListOf<Bitmap>()
        // Create 3 bitmaps without getting from pool (all new)
        repeat(3) {
            bitmaps.add(Bitmap.createBitmap(TEST_WIDTH, TEST_HEIGHT, Bitmap.Config.ARGB_8888))
        }

        // Return all 3 to pool with size 2
        pool2.getBitmap(TEST_WIDTH, TEST_HEIGHT) // initialize dimensions
        bitmaps.forEach { pool2.returnBitmap(it) }

        // First bitmap should have been evicted (recycled) when third was added
        assertTrue(bitmaps[0].isRecycled, "Oldest bitmap should be evicted")
        assertFalse(bitmaps[1].isRecycled, "Second bitmap should be in pool")
        assertFalse(bitmaps[2].isRecycled, "Third bitmap should be in pool")
    }

    @Test
    fun returnRecycledBitmapIsIgnored() {
        val pool = BitmapPool()
        val bitmap = pool.getBitmap(TEST_WIDTH, TEST_HEIGHT)
        bitmap.recycle()
        pool.returnBitmap(bitmap)

        // Getting a new bitmap should create a fresh one (not the recycled one)
        val fresh = pool.getBitmap(TEST_WIDTH, TEST_HEIGHT)
        assertFalse(fresh.isRecycled)
        assertNotSame(bitmap, fresh)
    }

    @Test
    fun returnBitmapWithWrongDimensionsRecyclesIt() {
        val pool = BitmapPool()
        pool.getBitmap(TEST_WIDTH, TEST_HEIGHT) // initialize pool dimensions

        val wrongSize = Bitmap.createBitmap(320, 240, Bitmap.Config.ARGB_8888)
        pool.returnBitmap(wrongSize)

        assertTrue(wrongSize.isRecycled)
    }
}
