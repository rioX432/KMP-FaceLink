package io.github.kmpfacelink

import io.github.kmpfacelink.model.FaceLandmark
import io.github.kmpfacelink.util.LandmarkGeometry
import kotlin.math.abs
import kotlin.math.sqrt
import kotlin.test.Test
import kotlin.test.assertTrue

class LandmarkGeometryTest {

    private fun assertApprox(expected: Float, actual: Float, epsilon: Float = 0.0001f) {
        assertTrue(
            abs(expected - actual) < epsilon,
            "Expected $expected but got $actual (epsilon=$epsilon)",
        )
    }

    @Test
    fun distance2D_samePoint_returnsZero() {
        val p = FaceLandmark(0.5f, 0.5f, 0.1f)
        assertApprox(0f, LandmarkGeometry.distance2D(p, p))
    }

    @Test
    fun distance2D_ignoresZ() {
        val a = FaceLandmark(0f, 0f, 0f)
        val b = FaceLandmark(0.3f, 0.4f, 0.9f)
        // 2D distance = sqrt(0.09 + 0.16) = 0.5
        assertApprox(0.5f, LandmarkGeometry.distance2D(a, b))
    }

    @Test
    fun distance3D_includesZ() {
        val a = FaceLandmark(0f, 0f, 0f)
        val b = FaceLandmark(0.1f, 0.2f, 0.3f)
        val expected = sqrt(0.01f + 0.04f + 0.09f)
        assertApprox(expected, LandmarkGeometry.distance3D(a, b))
    }

    @Test
    fun remap_mapsCorrectly() {
        // Middle of input range -> middle of output range
        assertApprox(0.5f, LandmarkGeometry.remap(0.5f, 0f, 1f))
    }

    @Test
    fun remap_clampsBelow() {
        assertApprox(0f, LandmarkGeometry.remap(-0.5f, 0f, 1f))
    }

    @Test
    fun remap_clampsAbove() {
        assertApprox(1f, LandmarkGeometry.remap(1.5f, 0f, 1f))
    }

    @Test
    fun remap_customOutputRange() {
        assertApprox(5f, LandmarkGeometry.remap(0.5f, 0f, 1f, 0f, 10f))
    }

    @Test
    fun remap_invertedInputRange_returnsOutMin() {
        // inMax <= inMin is a degenerate case
        assertApprox(0f, LandmarkGeometry.remap(0.5f, 1f, 0f))
    }

    @Test
    fun remap_narrowRange() {
        // value=0.15, range=[0.1, 0.2] -> t=0.5 -> output=0.5
        assertApprox(0.5f, LandmarkGeometry.remap(0.15f, 0.1f, 0.2f))
    }
}
