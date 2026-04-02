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

        internal fun lerpTransform(
            prev: HeadTransform,
            cur: HeadTransform,
            t: Float,
        ): HeadTransform = HeadTransform(
            pitch = cur.pitch + (cur.pitch - prev.pitch) * t,
            yaw = cur.yaw + (cur.yaw - prev.yaw) * t,
            roll = cur.roll + (cur.roll - prev.roll) * t,
            positionX = cur.positionX + (cur.positionX - prev.positionX) * t,
            positionY = cur.positionY + (cur.positionY - prev.positionY) * t,
            positionZ = cur.positionZ + (cur.positionZ - prev.positionZ) * t,
            // Don't interpolate the raw matrix — consumers use Euler angles
            transformMatrix = cur.transformMatrix,
        )
    }
}
