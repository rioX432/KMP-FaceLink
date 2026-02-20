package io.github.kmpfacelink.actions.gesture

import io.github.kmpfacelink.model.HandJoint

/**
 * DSL builder for defining custom hand gestures from landmark geometry.
 *
 * Example usage:
 * ```
 * val wave = landmarkGesture("wave") {
 *     distanceLessThan(HandJoint.THUMB_TIP, HandJoint.INDEX_FINGER_TIP, maxDistance = 0.05f)
 *     above(HandJoint.INDEX_FINGER_TIP, reference = HandJoint.WRIST)
 * }
 * ```
 */
public class LandmarkGestureBuilder(private val name: String) {
    private val conditions = mutableListOf<LandmarkCondition>()

    /** Two joints must be within a maximum distance. */
    public fun distanceLessThan(
        jointA: HandJoint,
        jointB: HandJoint,
        maxDistance: Float,
    ) {
        conditions.add(LandmarkCondition.DistanceLessThan(jointA, jointB, maxDistance))
    }

    /** Two joints must be separated by a minimum distance. */
    public fun distanceGreaterThan(
        jointA: HandJoint,
        jointB: HandJoint,
        minDistance: Float,
    ) {
        conditions.add(LandmarkCondition.DistanceGreaterThan(jointA, jointB, minDistance))
    }

    /** A joint must be above (lower Y value) the reference joint. */
    public fun above(joint: HandJoint, reference: HandJoint) {
        conditions.add(LandmarkCondition.Above(joint, reference))
    }

    /** A joint must be below (higher Y value) the reference joint. */
    public fun below(joint: HandJoint, reference: HandJoint) {
        conditions.add(LandmarkCondition.Below(joint, reference))
    }

    internal fun build(): LandmarkGesture = LandmarkGesture(name, conditions.toList())
}

/**
 * Creates a custom [LandmarkGesture] using the DSL builder.
 *
 * @param name Unique name for the gesture.
 * @param block Builder configuration.
 */
public fun landmarkGesture(name: String, block: LandmarkGestureBuilder.() -> Unit): LandmarkGesture =
    LandmarkGestureBuilder(name).apply(block).build()
