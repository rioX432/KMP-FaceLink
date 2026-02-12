package io.github.kmpfacelink.util

import io.github.kmpfacelink.model.HandJoint
import io.github.kmpfacelink.model.HandLandmarkPoint
import io.github.kmpfacelink.model.Handedness
import io.github.kmpfacelink.model.TrackedHand

/**
 * Exponential Moving Average filter for hand landmark smoothing.
 *
 * Maintains separate state per (Handedness, HandJoint, axis) and resets
 * when a previously tracked hand is lost.
 *
 * @param alpha Smoothing factor (0.0–1.0). Higher = less smoothing.
 */
internal class HandEmaFilter(private val alpha: Float = 0.5f) : HandLandmarkSmoother {

    // Key: Handedness -> Joint -> (prevX, prevY, prevZ)
    private val previousValues = mutableMapOf<Handedness, MutableMap<HandJoint, FloatArray>>()
    private var previousHandedness = emptySet<Handedness>()

    override fun smooth(hands: List<TrackedHand>, timestampMs: Long): List<TrackedHand> {
        val currentHandedness = hands.map { it.handedness }.toSet()

        // Reset state for hands that were lost
        val lostHands = previousHandedness - currentHandedness
        for (hand in lostHands) {
            previousValues.remove(hand)
        }
        previousHandedness = currentHandedness

        return hands.map { hand -> smoothHand(hand) }
    }

    override fun reset() {
        previousValues.clear()
        previousHandedness = emptySet()
    }

    private fun smoothHand(hand: TrackedHand): TrackedHand {
        val prevJoints = previousValues.getOrPut(hand.handedness) { mutableMapOf() }

        val smoothedLandmarks = hand.landmarks.map { point ->
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
                HandLandmarkPoint(joint = point.joint, x = sx, y = sy, z = sz)
            }
        }

        return hand.copy(landmarks = smoothedLandmarks)
    }
}
