package io.github.kmpfacelink

import io.github.kmpfacelink.util.TransformUtils
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class TransformUtilsTest {

    private fun assertApprox(expected: Float, actual: Float, epsilon: Float = 0.1f) {
        assertTrue(abs(expected - actual) < epsilon, "Expected $expected but was $actual")
    }

    @Test
    fun identityMatrixReturnsZeroTransform() {
        // Column-major identity matrix
        val identity = floatArrayOf(
            1f, 0f, 0f, 0f,
            0f, 1f, 0f, 0f,
            0f, 0f, 1f, 0f,
            0f, 0f, 0f, 1f,
        )
        val transform = TransformUtils.fromMatrix(identity)
        assertApprox(0f, transform.pitch)
        assertApprox(0f, transform.yaw)
        assertApprox(0f, transform.roll)
        assertApprox(0f, transform.positionX)
        assertApprox(0f, transform.positionY)
        assertApprox(0f, transform.positionZ)
    }

    @Test
    fun translationIsExtractedCorrectly() {
        // Column-major identity with translation (5, 10, -3)
        val matrix = floatArrayOf(
            1f, 0f, 0f, 0f,
            0f, 1f, 0f, 0f,
            0f, 0f, 1f, 0f,
            5f, 10f, -3f, 1f,
        )
        val transform = TransformUtils.fromMatrix(matrix)
        assertApprox(5f, transform.positionX)
        assertApprox(10f, transform.positionY)
        assertApprox(-3f, transform.positionZ)
    }

    @Test
    fun transformMatrixIsCopied() {
        val matrix = floatArrayOf(
            1f, 0f, 0f, 0f,
            0f, 1f, 0f, 0f,
            0f, 0f, 1f, 0f,
            0f, 0f, 0f, 1f,
        )
        val transform = TransformUtils.fromMatrix(matrix)
        assertEquals(16, checkNotNull(transform.transformMatrix).size)
        // Verify it's a copy
        matrix[12] = 999f
        assertApprox(0f, checkNotNull(transform.transformMatrix)[12])
    }

    @Test
    fun invalidMatrixSizeThrows() {
        assertFailsWith<IllegalArgumentException> {
            TransformUtils.fromMatrix(floatArrayOf(1f, 2f, 3f))
        }
    }
}
