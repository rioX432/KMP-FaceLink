package io.github.kmpfacelink.util

import io.github.kmpfacelink.model.HandJoint
import io.github.kmpfacelink.model.HandLandmarkPoint
import io.github.kmpfacelink.model.Handedness
import io.github.kmpfacelink.model.TrackedHand
import kotlin.math.PI
import kotlin.math.abs

/**
 * One Euro adaptive low-pass filter for hand landmark smoothing.
 *
 * Maintains separate filter state per (Handedness, HandJoint, axis).
 * Resets when a previously tracked hand is lost.
 *
 * @param minCutoff Minimum cutoff frequency in Hz (lower = more smoothing at rest)
 * @param beta Speed coefficient (higher = less lag during fast movements)
 * @param dCutoff Cutoff frequency for the derivative filter
 */
internal class HandOneEuroFilter(
    private val minCutoff: Float = 1.0f,
    private val beta: Float = 0.007f,
    private val dCutoff: Float = 1.0f,
) : HandLandmarkSmoother {

    // Key: Handedness -> Joint -> AxisState[3] (x, y, z)
    private val states = mutableMapOf<Handedness, MutableMap<HandJoint, Array<AxisState>>>()
    private var lastTimestampMs: Long = -1L
    private var previousHandedness = emptySet<Handedness>()

    override fun smooth(hands: List<TrackedHand>, timestampMs: Long): List<TrackedHand> {
        val currentHandedness = hands.map { it.handedness }.toSet()

        // Reset state for hands that were lost
        val lostHands = previousHandedness - currentHandedness
        for (hand in lostHands) {
            states.remove(hand)
        }
        previousHandedness = currentHandedness

        // First frame or same timestamp — pass through
        if (lastTimestampMs < 0 || timestampMs <= lastTimestampMs) {
            lastTimestampMs = timestampMs
            // Initialize states
            for (hand in hands) {
                val jointStates = states.getOrPut(hand.handedness) { mutableMapOf() }
                for (point in hand.landmarks) {
                    jointStates[point.joint] = arrayOf(
                        AxisState(point.x, 0f),
                        AxisState(point.y, 0f),
                        AxisState(point.z, 0f),
                    )
                }
            }
            return hands
        }

        val dt = (timestampMs - lastTimestampMs) / 1000f
        lastTimestampMs = timestampMs

        return hands.map { hand -> smoothHand(hand, dt) }
    }

    override fun reset() {
        states.clear()
        lastTimestampMs = -1L
        previousHandedness = emptySet()
    }

    private fun smoothHand(hand: TrackedHand, dt: Float): TrackedHand {
        val jointStates = states.getOrPut(hand.handedness) { mutableMapOf() }

        val smoothedLandmarks = hand.landmarks.map { point ->
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
                HandLandmarkPoint(joint = point.joint, x = sx, y = sy, z = sz)
            }
        }

        return hand.copy(landmarks = smoothedLandmarks)
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
}
