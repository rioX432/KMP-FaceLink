package io.github.kmpfacelink.internal.depth

import android.graphics.Bitmap
import io.github.kmpfacelink.model.HandJoint
import io.github.kmpfacelink.model.HandLandmarkPoint
import io.github.kmpfacelink.util.DepthAwareHandLifter
import io.github.kmpfacelink.util.HandDepthMode
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame

private const val MAP_WIDTH = 2
private const val MAP_HEIGHT = 2

/** Fake estimator returning a fixed depth map, or throwing to simulate a released/failed estimator. */
private class FakeDepthEstimator(
    override val outputWidth: Int = MAP_WIDTH,
    override val outputHeight: Int = MAP_HEIGHT,
    private val values: FloatArray = floatArrayOf(0f, 10f, 20f, 30f),
    private val throwOnEstimate: Boolean = false,
) : MonocularDepthEstimator {
    var releaseCount: Int = 0

    override fun estimate(bitmap: Bitmap): FloatArray {
        check(!throwOnEstimate) { "simulated estimate failure" }
        return values
    }

    override fun release() {
        releaseCount++
    }
}

@RunWith(RobolectricTestRunner::class)
class HandDepthEnhancerTest {

    private val tolerance = 1e-4f

    private fun bitmap(): Bitmap = Bitmap.createBitmap(MAP_WIDTH, MAP_HEIGHT, Bitmap.Config.ARGB_8888)

    @Test
    fun estimateDepthWrapsEstimatorOutput() {
        val enhancer = HandDepthEnhancer(FakeDepthEstimator())

        val depthMap = enhancer.estimateDepth(bitmap())

        assertNotNull(depthMap)
        assertEquals(MAP_WIDTH, depthMap.width)
        assertEquals(MAP_HEIGHT, depthMap.height)
    }

    @Test
    fun estimateDepthReturnsNullWhenEstimatorFails() {
        val enhancer = HandDepthEnhancer(FakeDepthEstimator(throwOnEstimate = true))

        assertNull(enhancer.estimateDepth(bitmap()))
    }

    @Test
    fun enhanceRewritesLandmarkZFromDepthMap() {
        val enhancer = HandDepthEnhancer(
            FakeDepthEstimator(),
            DepthAwareHandLifter(mode = HandDepthMode.WRIST_RELATIVE),
        )
        val depthMap = assertNotNull(enhancer.estimateDepth(bitmap()))
        val landmarks = listOf(
            HandLandmarkPoint(HandJoint.WRIST, x = 0f, y = 0f, z = 0f), // depth 0
            HandLandmarkPoint(HandJoint.INDEX_FINGER_TIP, x = 1f, y = 1f, z = 0f), // depth 30
        )

        val result = enhancer.enhance(landmarks, depthMap)

        assertEquals(0f, result[0].z, tolerance)
        // wrist-relative: (wristDepth - sampled) = (0 - 30) = -30
        assertEquals(-30f, result[1].z, tolerance)
    }

    @Test
    fun enhanceReturnsEmptyListUnchanged() {
        val enhancer = HandDepthEnhancer(FakeDepthEstimator())
        val depthMap = assertNotNull(enhancer.estimateDepth(bitmap()))
        val empty = emptyList<HandLandmarkPoint>()

        assertSame(empty, enhancer.enhance(empty, depthMap))
    }

    @Test
    fun releaseDelegatesToEstimator() {
        val estimator = FakeDepthEstimator()
        val enhancer = HandDepthEnhancer(estimator)

        enhancer.release()

        assertEquals(1, estimator.releaseCount)
    }
}
