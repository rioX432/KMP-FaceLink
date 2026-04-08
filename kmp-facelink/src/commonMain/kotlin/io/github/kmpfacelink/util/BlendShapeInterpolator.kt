package io.github.kmpfacelink.util

import io.github.kmpfacelink.model.BlendShape
import io.github.kmpfacelink.model.BlendShapeData
import io.github.kmpfacelink.model.HeadTransform

/**
 * Provides lightweight interpolation for skipped frames using linear interpolation (lerp).
 *
 * When a frame is skipped, this class generates an interpolated result from the last
 * two processed frames, producing smooth output without running full detection.
 */
internal class BlendShapeInterpolator {

    private var previousBlendShapes: BlendShapeData? = null
    private var currentBlendShapes: BlendShapeData? = null
    private var previousTimestampMs: Long = 0L
    private var currentTimestampMs: Long = 0L

    private var previousTransform: HeadTransform? = null
    private var currentTransform: HeadTransform? = null

    // Pre-allocated scratch map to avoid per-frame allocation
    private val scratchMap = HashMap<BlendShape, Float>(BlendShape.entries.size)

    /**
     * Records a fully processed frame for use in future interpolation.
     *
     * @param blendShapes The processed blend shape data.
     * @param headTransform The processed head transform.
     * @param timestampMs Frame timestamp in milliseconds.
     */
    fun onFrameProcessed(
        blendShapes: BlendShapeData,
        headTransform: HeadTransform,
        timestampMs: Long,
    ) {
        previousBlendShapes = currentBlendShapes
        previousTimestampMs = currentTimestampMs
        previousTransform = currentTransform

        currentBlendShapes = blendShapes
        currentTimestampMs = timestampMs
        currentTransform = headTransform
    }

    /**
     * Generates interpolated blend shapes for a skipped frame.
     *
     * Uses linear extrapolation from the last two processed frames when available,
     * or returns the last known data if only one frame exists.
     *
     * @param timestampMs The timestamp of the skipped frame.
     * @return Interpolated blend shape data, or null if no prior data exists.
     */
    fun interpolateBlendShapes(timestampMs: Long): BlendShapeData? {
        val current = currentBlendShapes ?: return null
        val previous = previousBlendShapes ?: return HashMap(current)

        val frameDelta = currentTimestampMs - previousTimestampMs
        if (frameDelta <= 0L) return HashMap(current)

        val t = (timestampMs - currentTimestampMs).toFloat() / frameDelta.toFloat()
        // Clamp extrapolation to avoid overshooting
        val clampedT = t.coerceIn(0f, MAX_EXTRAPOLATION_FACTOR)

        scratchMap.clear()
        for (shape in BlendShape.entries) {
            val prevVal = previous[shape] ?: 0f
            val curVal = current[shape] ?: 0f
            // Linear extrapolation: current + (current - previous) * t
            val interpolated = curVal + (curVal - prevVal) * clampedT
            scratchMap[shape] = interpolated.coerceIn(0f, 1f)
        }
        return HashMap(scratchMap)
    }

    /**
     * Generates interpolated head transform for a skipped frame.
     *
     * @param timestampMs The timestamp of the skipped frame.
     * @return Interpolated head transform, or null if no prior data exists.
     */
    fun interpolateTransform(timestampMs: Long): HeadTransform? {
        val current = currentTransform ?: return null
        val previous = previousTransform ?: return current

        val frameDelta = currentTimestampMs - previousTimestampMs
        if (frameDelta <= 0L) return current

        val t = (timestampMs - currentTimestampMs).toFloat() / frameDelta.toFloat()
        val clampedT = t.coerceIn(0f, MAX_EXTRAPOLATION_FACTOR)

        return lerpTransform(previous, current, clampedT)
    }

    /** Whether enough data exists to produce interpolated output. */
    val hasData: Boolean get() = currentBlendShapes != null

    /** Resets all stored state. */
    fun reset() {
        previousBlendShapes = null
        currentBlendShapes = null
        previousTimestampMs = 0L
        currentTimestampMs = 0L
        previousTransform = null
        currentTransform = null
    }

    internal companion object {
        /** Maximum extrapolation factor to prevent overshooting. */
        internal const val MAX_EXTRAPOLATION_FACTOR = 1.5f

        /**
         * Extrapolates a head transform forward by `t` frame intervals past `cur`.
         *
         * Rotation is extrapolated along the great-circle arc through the previous
         * and current orientations using quaternion slerp. This avoids the gimbal
         * artifacts that naive per-component Euler interpolation produces when yaw
         * approaches ±90° or when rotation axes swap sign between frames.
         *
         * Position and (when present) the raw transform matrix fall back to linear
         * component-wise extrapolation, since they are free of the angle-wrap
         * pathology that motivated slerp.
         *
         * - `t == 0` returns `cur` exactly.
         * - `t == 1` returns one frame interval beyond `cur` along the
         *   `prev -> cur` trajectory.
         */
        internal fun lerpTransform(
            prev: HeadTransform,
            cur: HeadTransform,
            t: Float,
        ): HeadTransform {
            // At t == 0 the caller wants cur verbatim — skip the quat round-trip so the
            // result is bit-identical to the input (needed by callers that rely on
            // equality checks for cache invalidation) and to avoid pointless work on
            // the hot skip-frame path.
            if (t == 0f) return cur

            val qPrev = Quaternion.fromEulerDegrees(prev.pitch, prev.yaw, prev.roll)
            val qCur = Quaternion.fromEulerDegrees(cur.pitch, cur.yaw, cur.roll)
            // slerp parameter 1 returns `cur`; parameter `1 + t` extrapolates `t` frame
            // intervals beyond `cur` along the `prev -> cur` great-circle arc.
            val qExtrapolated = Quaternion.slerp(qPrev, qCur, 1f + t)
            val euler = qExtrapolated.toEulerDegrees()

            return HeadTransform(
                pitch = euler.pitch,
                yaw = euler.yaw,
                roll = euler.roll,
                positionX = cur.positionX + (cur.positionX - prev.positionX) * t,
                positionY = cur.positionY + (cur.positionY - prev.positionY) * t,
                positionZ = cur.positionZ + (cur.positionZ - prev.positionZ) * t,
                // Don't interpolate the raw matrix — consumers use Euler angles.
                transformMatrix = cur.transformMatrix,
            )
        }
    }
}
