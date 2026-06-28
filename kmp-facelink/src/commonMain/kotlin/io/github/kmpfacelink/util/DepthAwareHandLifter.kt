package io.github.kmpfacelink.util

import io.github.kmpfacelink.model.HandJoint
import io.github.kmpfacelink.model.HandLandmarkPoint
import kotlin.math.floor

/**
 * A dense relative-depth map produced by a monocular depth estimator (#898).
 *
 * [values] is row-major, length [width] * [height]. Values are relative (affine-invariant) — only
 * their ordering is meaningful, not absolute distance.
 *
 * Ownership of [values] transfers to this [DepthMap]: callers must not mutate the array afterwards
 * (no defensive copy is made, to avoid per-frame allocation of the dense map).
 */
internal data class DepthMap(
    val width: Int,
    val height: Int,
    val values: FloatArray,
) {
    init {
        require(width > 0 && height > 0) { "depth map dimensions must be positive" }
        require(values.size == width * height) {
            "values size ${values.size} != width*height ${width * height}"
        }
    }

    /** Depth at integer pixel [x], [y] (no bounds checking — caller clamps). */
    internal fun at(x: Int, y: Int): Float = values[y * width + x]

    // Generated equals/hashCode would compare the FloatArray by reference; override for value
    // semantics so DepthMap behaves like the data class it is declared as.
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is DepthMap) return false
        return width == other.width && height == other.height && values.contentEquals(other.values)
    }

    override fun hashCode(): Int {
        var result = width
        result = HASH_MULTIPLIER * result + height
        result = HASH_MULTIPLIER * result + values.contentHashCode()
        return result
    }

    private companion object {
        const val HASH_MULTIPLIER = 31
    }
}

/** How sampled depth is turned into the [HandLandmarkPoint.z] value. */
internal enum class HandDepthMode {
    /** z = sampled depth at the landmark, scaled. Use when no prior z exists (e.g. iOS, z=0). */
    ABSOLUTE,

    /**
     * z = (wrist depth - landmark depth) * scale, i.e. depth relative to the wrist — matching the
     * MediaPipe convention where the wrist is the origin. Differentiates fingers nearer/farther
     * than the wrist.
     */
    WRIST_RELATIVE,
}

/**
 * Enhances hand-landmark Z values using a monocular [DepthMap] (#899).
 *
 * Samples the depth map at each landmark's normalized (x, y) position via bilinear interpolation
 * and writes a new z. This is pure, platform-agnostic logic; the platform trackers supply the depth
 * map from their depth estimator and call [enhanceDepth] before emitting tracking data.
 *
 * NOTE: the depth model's sign/scale convention is model-dependent (MiDaS-style inverse depth has
 * "larger = closer"). [scale] (and its sign) calibrates sampled depth into the project's z
 * convention (negative = closer to camera) and should be tuned against the chosen model on-device.
 */
internal class DepthAwareHandLifter(
    private val mode: HandDepthMode = HandDepthMode.WRIST_RELATIVE,
    private val scale: Float = 1f,
) {

    /**
     * Returns a copy of [landmarks] with z replaced by depth sampled from [depthMap].
     * Returns [landmarks] unchanged when it is empty.
     */
    internal fun enhanceDepth(
        landmarks: List<HandLandmarkPoint>,
        depthMap: DepthMap,
    ): List<HandLandmarkPoint> {
        if (landmarks.isEmpty()) return landmarks

        val wristDepth = when (mode) {
            HandDepthMode.WRIST_RELATIVE -> {
                val wrist = landmarks.firstOrNull { it.joint == HandJoint.WRIST } ?: landmarks.first()
                sampleBilinear(depthMap, wrist.x, wrist.y)
            }
            HandDepthMode.ABSOLUTE -> 0f
        }

        return landmarks.map { point ->
            val sampled = sampleBilinear(depthMap, point.x, point.y)
            val z = when (mode) {
                HandDepthMode.ABSOLUTE -> sampled * scale
                HandDepthMode.WRIST_RELATIVE -> (wristDepth - sampled) * scale
            }
            point.copy(z = z)
        }
    }

    /**
     * Bilinearly samples [depthMap] at the normalized coordinate ([nx], [ny] in [0, 1]).
     * Coordinates are clamped to the map bounds.
     */
    private fun sampleBilinear(depthMap: DepthMap, nx: Float, ny: Float): Float {
        val fx = nx.coerceIn(0f, 1f) * (depthMap.width - 1)
        val fy = ny.coerceIn(0f, 1f) * (depthMap.height - 1)

        val x0 = floor(fx).toInt()
        val y0 = floor(fy).toInt()
        val x1 = minOf(x0 + 1, depthMap.width - 1)
        val y1 = minOf(y0 + 1, depthMap.height - 1)
        val dx = fx - x0
        val dy = fy - y0

        val top = depthMap.at(x0, y0) * (1f - dx) + depthMap.at(x1, y0) * dx
        val bottom = depthMap.at(x0, y1) * (1f - dx) + depthMap.at(x1, y1) * dx
        return top * (1f - dy) + bottom * dy
    }
}
