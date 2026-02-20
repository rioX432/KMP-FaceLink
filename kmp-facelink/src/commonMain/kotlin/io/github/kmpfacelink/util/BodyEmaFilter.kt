package io.github.kmpfacelink.util

import io.github.kmpfacelink.model.BodyJoint
import io.github.kmpfacelink.model.BodyLandmarkPoint
import io.github.kmpfacelink.model.TrackedBody

/**
 * Exponential Moving Average filter for body landmark smoothing.
 *
 * Maintains separate state per (body index, BodyJoint, axis) and resets
 * when the number of tracked bodies changes.
 *
 * @param alpha Smoothing factor (0.0-1.0). Higher = less smoothing.
 */
internal class BodyEmaFilter(private val alpha: Float = 0.5f) : BodyLandmarkSmoother {

    // Key: body index -> Joint -> (prevX, prevY, prevZ)
    private val previousValues = mutableMapOf<Int, MutableMap<BodyJoint, FloatArray>>()
    private var previousBodyCount = 0

    override fun smooth(bodies: List<TrackedBody>, timestampMs: Long): List<TrackedBody> {
        // Reset state when body count changes (lost/gained tracking)
        if (bodies.size != previousBodyCount) {
            previousValues.clear()
        }
        previousBodyCount = bodies.size

        return bodies.mapIndexed { index, body -> smoothBody(index, body) }
    }

    override fun reset() {
        previousValues.clear()
        previousBodyCount = 0
    }

    private fun smoothBody(bodyIndex: Int, body: TrackedBody): TrackedBody {
        val prevJoints = previousValues.getOrPut(bodyIndex) { mutableMapOf() }

        val smoothedLandmarks = body.landmarks.map { point ->
            val prev = prevJoints[point.joint]
            if (prev == null) {
                prevJoints[point.joint] = floatArrayOf(point.x, point.y, point.z)
                point
            } else {
                val sx = alpha * point.x + (1f - alpha) * prev[0]
                val sy = alpha * point.y + (1f - alpha) * prev[1]
                val sz = alpha * point.z + (1f - alpha) * prev[2]
                prev[0] = sx
                prev[1] = sy
                prev[2] = sz
                BodyLandmarkPoint(
                    joint = point.joint,
                    x = sx,
                    y = sy,
                    z = sz,
                    visibility = point.visibility,
                )
            }
        }

        return body.copy(landmarks = smoothedLandmarks)
    }
}
