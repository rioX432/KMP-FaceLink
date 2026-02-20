package io.github.kmpfacelink.util

import io.github.kmpfacelink.model.BodyJoint
import io.github.kmpfacelink.model.BodyLandmarkPoint
import io.github.kmpfacelink.model.TrackedBody
import kotlin.math.PI
import kotlin.math.abs

/**
 * One Euro adaptive low-pass filter for body landmark smoothing.
 *
 * Maintains separate filter state per (body index, BodyJoint, axis).
 * Resets when the number of tracked bodies changes.
 *
 * @param minCutoff Minimum cutoff frequency in Hz (lower = more smoothing at rest)
 * @param beta Speed coefficient (higher = less lag during fast movements)
 * @param dCutoff Cutoff frequency for the derivative filter
 */
internal class BodyOneEuroFilter(
    private val minCutoff: Float = 1.0f,
    private val beta: Float = 0.007f,
    private val dCutoff: Float = 1.0f,
) : BodyLandmarkSmoother {

    // Key: body index -> Joint -> AxisState[3] (x, y, z)
    private val states = mutableMapOf<Int, MutableMap<BodyJoint, Array<AxisState>>>()
    private var lastTimestampMs: Long = -1L
    private var previousBodyCount = 0

    override fun smooth(bodies: List<TrackedBody>, timestampMs: Long): List<TrackedBody> {
        // Reset state when body count changes
        if (bodies.size != previousBodyCount) {
            states.clear()
            lastTimestampMs = -1L
        }
        previousBodyCount = bodies.size

        // First frame or same timestamp — pass through
        if (lastTimestampMs < 0 || timestampMs <= lastTimestampMs) {
            lastTimestampMs = timestampMs
            // Initialize states
            bodies.forEachIndexed { index, body ->
                val jointStates = states.getOrPut(index) { mutableMapOf() }
                for (point in body.landmarks) {
                    jointStates[point.joint] = arrayOf(
                        AxisState(point.x, 0f),
                        AxisState(point.y, 0f),
                        AxisState(point.z, 0f),
                    )
                }
            }
            return bodies
        }

        val dt = (timestampMs - lastTimestampMs) / MS_PER_SECOND
        lastTimestampMs = timestampMs

        return bodies.mapIndexed { index, body -> smoothBody(index, body, dt) }
    }

    override fun reset() {
        states.clear()
        lastTimestampMs = -1L
        previousBodyCount = 0
    }

    private fun smoothBody(bodyIndex: Int, body: TrackedBody, dt: Float): TrackedBody {
        val jointStates = states.getOrPut(bodyIndex) { mutableMapOf() }

        val smoothedLandmarks = body.landmarks.map { point ->
            val axisStates = jointStates[point.joint]
            if (axisStates == null) {
                jointStates[point.joint] = arrayOf(
                    AxisState(point.x, 0f),
                    AxisState(point.y, 0f),
                    AxisState(point.z, 0f),
                )
                point
            } else {
                val sx = filterAxis(point.x, axisStates[0], dt)
                val sy = filterAxis(point.y, axisStates[1], dt)
                val sz = filterAxis(point.z, axisStates[2], dt)
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

    private fun filterAxis(raw: Float, state: AxisState, dt: Float): Float {
        val dx = (raw - state.x) / dt
        val edx = lowPass(dx, state.dx, smoothingFactor(dt, dCutoff))
        val cutoff = minCutoff + beta * abs(edx)
        val filtered = lowPass(raw, state.x, smoothingFactor(dt, cutoff))
        state.x = filtered
        state.dx = edx
        return filtered
    }

    private fun smoothingFactor(dt: Float, cutoff: Float): Float {
        val r = 2 * PI.toFloat() * cutoff * dt
        return r / (r + 1f)
    }

    private fun lowPass(x: Float, xPrev: Float, alpha: Float): Float =
        alpha * x + (1f - alpha) * xPrev

    private class AxisState(var x: Float, var dx: Float)

    private companion object {
        private const val MS_PER_SECOND = 1000f
    }
}
