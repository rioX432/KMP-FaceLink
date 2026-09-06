package io.github.kmpfacelink.util

import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Least-squares similarity (rotation + isotropic scale + translation) alignment of one 3D point
 * cloud onto another, using a fixed subset of correspondences as the rigid anchor.
 *
 * This is the Umeyama estimator in the row-vector convention used by the reference implementation
 * (`scripts/plan2/geometry.py::_similarity` in the talks-2026 research repo): given anchor rows
 * `A` from the source and `B` from the destination, it forms `M = A0ᵀ · B0`, takes `M = U·S·Vᵀ`,
 * and builds `R = U · diag(1, 1, d) · Vᵀ` with `d = sign(det(U·Vᵀ))`. A point is then mapped by
 * `y = s · ((x − centroid(A)) · R) + centroid(B)`.
 *
 * The 3x3 SVD is a one-sided Jacobi iteration. `R` is invariant under the column-sign and
 * column-order freedom of the decomposition, so the result agrees with LAPACK to rounding.
 */
// Literal 3s and 9s below are the fixed 3x3 / flat-3x3 layout, not tunable values.
@Suppress("MagicNumber")
internal object SimilarityAlign3D {

    /** Row-major 3x3 rotation plus the scale and the two centroids of one alignment. */
    class Transform(
        val scale: Double,
        val rotation: DoubleArray,
        private val srcCentroid: DoubleArray,
        private val dstCentroid: DoubleArray,
    ) {
        /**
         * Maps every point of a flat `[x0, y0, z0, x1, ...]` cloud into the destination frame.
         * [source] and [destination] may be the same array.
         */
        fun apply(source: DoubleArray, destination: DoubleArray) {
            val r = rotation
            val cax = srcCentroid[0]
            val cay = srcCentroid[1]
            val caz = srcCentroid[2]
            val cbx = dstCentroid[0]
            val cby = dstCentroid[1]
            val cbz = dstCentroid[2]
            var i = 0
            while (i < source.size) {
                val x = source[i] - cax
                val y = source[i + 1] - cay
                val z = source[i + 2] - caz
                destination[i] = scale * (x * r[0] + y * r[3] + z * r[6]) + cbx
                destination[i + 1] = scale * (x * r[1] + y * r[4] + z * r[7]) + cby
                destination[i + 2] = scale * (x * r[2] + y * r[5] + z * r[8]) + cbz
                i += 3
            }
        }
    }

    /**
     * Estimates the transform that maps [source] onto [destination], fitted on the landmark
     * [anchors] only. Both clouds are flat `[x, y, z, ...]` arrays with the same layout.
     */
    fun estimate(source: DoubleArray, destination: DoubleArray, anchors: IntArray): Transform {
        val n = anchors.size
        val srcCentroid = centroid(source, anchors)
        val dstCentroid = centroid(destination, anchors)

        // m = A0ᵀ · B0, row-major 3x3.
        val m = DoubleArray(MATRIX_SIZE)
        var srcSquares = 0.0
        for (k in 0 until n) {
            val base = anchors[k] * 3
            val ax = source[base] - srcCentroid[0]
            val ay = source[base + 1] - srcCentroid[1]
            val az = source[base + 2] - srcCentroid[2]
            val bx = destination[base] - dstCentroid[0]
            val by = destination[base + 1] - dstCentroid[1]
            val bz = destination[base + 2] - dstCentroid[2]
            m[0] += ax * bx
            m[1] += ax * by
            m[2] += ax * bz
            m[3] += ay * bx
            m[4] += ay * by
            m[5] += ay * bz
            m[6] += az * bx
            m[7] += az * by
            m[8] += az * bz
            srcSquares += ax * ax + ay * ay + az * az
        }

        val svd = svd3x3(m)
        val u = svd.u
        val v = svd.v
        // d = sign(det(U · Vᵀ)); U and V are orthogonal so the determinant is exactly +/-1.
        val d = if (determinant(multiplyByTranspose(u, v)) < 0.0) -1.0 else 1.0

        val rotation = buildRotation(u, v, d)
        val trace = svd.singular[0] + svd.singular[1] + d * svd.singular[2]
        val scale = if (srcSquares == 0.0) 0.0 else trace / srcSquares
        return Transform(scale, rotation, srcCentroid, dstCentroid)
    }

    /** R = U · diag(1, 1, d) · Vᵀ, row-major. */
    private fun buildRotation(u: DoubleArray, v: DoubleArray, d: Double): DoubleArray {
        val out = DoubleArray(MATRIX_SIZE)
        for (row in 0 until 3) {
            for (col in 0 until 3) {
                out[row * 3 + col] = u[row * 3] * v[col * 3] +
                    u[row * 3 + 1] * v[col * 3 + 1] +
                    d * u[row * 3 + 2] * v[col * 3 + 2]
            }
        }
        return out
    }

    private fun centroid(points: DoubleArray, anchors: IntArray): DoubleArray {
        var sx = 0.0
        var sy = 0.0
        var sz = 0.0
        for (index in anchors) {
            val base = index * 3
            sx += points[base]
            sy += points[base + 1]
            sz += points[base + 2]
        }
        val n = anchors.size.toDouble()
        return doubleArrayOf(sx / n, sy / n, sz / n)
    }

    private class Svd(val u: DoubleArray, val singular: DoubleArray, val v: DoubleArray)

    /**
     * One-sided Jacobi SVD of a row-major 3x3 matrix, singular values in descending order.
     * Column rotations orthogonalize `M·V`; the column norms are then the singular values.
     */
    private fun svd3x3(matrix: DoubleArray): Svd {
        val a = matrix.copyOf()
        val v = doubleArrayOf(1.0, 0.0, 0.0, 0.0, 1.0, 0.0, 0.0, 0.0, 1.0)
        var sweep = 0
        while (sweep < MAX_SWEEPS && jacobiSweep(a, v)) sweep++

        val singular = DoubleArray(3) { sqrt(columnDot(a, it, it)) }
        val order = intArrayOf(0, 1, 2).sortedByDescending { singular[it] }
        val sortedSingular = DoubleArray(3)
        val u = DoubleArray(MATRIX_SIZE)
        val sortedV = DoubleArray(MATRIX_SIZE)
        for (col in 0 until 3) {
            val from = order[col]
            val norm = singular[from]
            sortedSingular[col] = norm
            for (row in 0 until 3) {
                u[row * 3 + col] = if (norm == 0.0) 0.0 else a[row * 3 + from] / norm
                sortedV[row * 3 + col] = v[row * 3 + from]
            }
        }
        return Svd(u, sortedSingular, sortedV)
    }

    /** One sweep over the three column pairs; false once every pair is already orthogonal. */
    private fun jacobiSweep(a: DoubleArray, v: DoubleArray): Boolean {
        var rotated = false
        for (p in 0 until 2) {
            for (q in p + 1 until 3) {
                val alpha = columnDot(a, p, p)
                val beta = columnDot(a, q, q)
                val gamma = columnDot(a, p, q)
                if (gamma == 0.0 || abs(gamma) <= JACOBI_EPSILON * sqrt(alpha * beta)) continue
                val zeta = (beta - alpha) / (2.0 * gamma)
                val sign = if (zeta < 0.0) -1.0 else 1.0
                val t = sign / (abs(zeta) + sqrt(1.0 + zeta * zeta))
                val c = 1.0 / sqrt(1.0 + t * t)
                rotateColumns(a, p, q, c, c * t)
                rotateColumns(v, p, q, c, c * t)
                rotated = true
            }
        }
        return rotated
    }

    private fun columnDot(m: DoubleArray, p: Int, q: Int): Double =
        m[p] * m[q] + m[3 + p] * m[3 + q] + m[6 + p] * m[6 + q]

    private fun rotateColumns(m: DoubleArray, p: Int, q: Int, c: Double, s: Double) {
        for (row in 0 until 3) {
            val base = row * 3
            val mp = m[base + p]
            val mq = m[base + q]
            m[base + p] = c * mp - s * mq
            m[base + q] = s * mp + c * mq
        }
    }

    /** Row-major `a · bᵀ`. */
    private fun multiplyByTranspose(a: DoubleArray, b: DoubleArray): DoubleArray {
        val out = DoubleArray(MATRIX_SIZE)
        for (row in 0 until 3) {
            for (col in 0 until 3) {
                out[row * 3 + col] =
                    a[row * 3] * b[col * 3] + a[row * 3 + 1] * b[col * 3 + 1] + a[row * 3 + 2] * b[col * 3 + 2]
            }
        }
        return out
    }

    private fun determinant(m: DoubleArray): Double =
        m[0] * (m[4] * m[8] - m[5] * m[7]) -
            m[1] * (m[3] * m[8] - m[5] * m[6]) +
            m[2] * (m[3] * m[7] - m[4] * m[6])

    private const val MATRIX_SIZE = 9
    private const val MAX_SWEEPS = 30
    private const val JACOBI_EPSILON = 1e-17
}
