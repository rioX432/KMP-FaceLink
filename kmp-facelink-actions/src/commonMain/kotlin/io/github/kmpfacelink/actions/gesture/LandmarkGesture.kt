package io.github.kmpfacelink.actions.gesture

import io.github.kmpfacelink.model.HandJoint
import io.github.kmpfacelink.model.HandLandmarkPoint
import io.github.kmpfacelink.model.HandTrackingData
import io.github.kmpfacelink.model.TrackedHand
import kotlin.math.sqrt

/**
 * A custom hand gesture defined by landmark geometry conditions.
 *
 * Use [LandmarkGestureBuilder] to construct gestures from distance, angle,
 * and relative position checks between hand joints.
 *
 * @property name A unique name for this gesture.
 * @property conditions The geometric conditions that must all be true for the gesture to match.
 */
public data class LandmarkGesture(
    val name: String,
    val conditions: List<LandmarkCondition>,
) {
    /**
     * Evaluates whether the given hand landmarks satisfy all conditions.
     *
     * @return Confidence score (0.0 if any condition fails, otherwise average of condition scores).
     */
    public fun evaluate(landmarks: List<HandLandmarkPoint>): Float {
        if (conditions.isEmpty()) return 0f
        val landmarkMap = landmarks.associateBy { it.joint }
        val scores = conditions.map { it.evaluate(landmarkMap) }
        return if (scores.any { it == 0f }) 0f else scores.average().toFloat()
    }
}

/**
 * A geometric condition between hand landmarks.
 */
public sealed class LandmarkCondition {
    internal abstract fun evaluate(landmarks: Map<HandJoint, HandLandmarkPoint>): Float

    /**
     * Two joints must be within a maximum distance.
     */
    public data class DistanceLessThan(
        val jointA: HandJoint,
        val jointB: HandJoint,
        val maxDistance: Float,
    ) : LandmarkCondition() {
        override fun evaluate(landmarks: Map<HandJoint, HandLandmarkPoint>): Float {
            val a = landmarks[jointA] ?: return 0f
            val b = landmarks[jointB] ?: return 0f
            return if (distance(a, b) < maxDistance) HIGH_SCORE else 0f
        }
    }

    /**
     * Two joints must be separated by a minimum distance.
     */
    public data class DistanceGreaterThan(
        val jointA: HandJoint,
        val jointB: HandJoint,
        val minDistance: Float,
    ) : LandmarkCondition() {
        override fun evaluate(landmarks: Map<HandJoint, HandLandmarkPoint>): Float {
            val a = landmarks[jointA] ?: return 0f
            val b = landmarks[jointB] ?: return 0f
            return if (distance(a, b) > minDistance) HIGH_SCORE else 0f
        }
    }

    /**
     * A joint's Y coordinate must be above (less than) another joint's Y coordinate.
     * In image coordinates, lower Y = higher position.
     */
    public data class Above(
        val joint: HandJoint,
        val referenceJoint: HandJoint,
    ) : LandmarkCondition() {
        override fun evaluate(landmarks: Map<HandJoint, HandLandmarkPoint>): Float {
            val point = landmarks[joint] ?: return 0f
            val reference = landmarks[referenceJoint] ?: return 0f
            return if (point.y < reference.y) HIGH_SCORE else 0f
        }
    }

    /**
     * A joint's Y coordinate must be below (greater than) another joint's Y coordinate.
     */
    public data class Below(
        val joint: HandJoint,
        val referenceJoint: HandJoint,
    ) : LandmarkCondition() {
        override fun evaluate(landmarks: Map<HandJoint, HandLandmarkPoint>): Float {
            val point = landmarks[joint] ?: return 0f
            val reference = landmarks[referenceJoint] ?: return 0f
            return if (point.y > reference.y) HIGH_SCORE else 0f
        }
    }

    internal companion object {
        const val HIGH_SCORE = 0.9f

        fun distance(a: HandLandmarkPoint, b: HandLandmarkPoint): Float {
            val dx = a.x - b.x
            val dy = a.y - b.y
            val dz = a.z - b.z
            return sqrt(dx * dx + dy * dy + dz * dz)
        }
    }
}

/**
 * Evaluates custom landmark gestures against hand tracking data.
 *
 * @property gestures Custom gestures to evaluate.
 * @property minConfidence Minimum confidence to consider a gesture matched.
 */
public class LandmarkGestureDetector(
    private val gestures: List<LandmarkGesture>,
    private val minConfidence: Float = DEFAULT_MIN_CONFIDENCE,
) {
    /**
     * Evaluates all registered gestures against a tracked hand.
     *
     * @return List of matched gestures with their confidence scores, sorted by confidence descending.
     */
    public fun detect(hand: TrackedHand): List<GestureMatch> =
        gestures.mapNotNull { gesture ->
            val confidence = gesture.evaluate(hand.landmarks)
            if (confidence >= minConfidence) GestureMatch(gesture.name, confidence) else null
        }.sortedByDescending { it.confidence }

    /**
     * Evaluates all registered gestures against all hands in the tracking data.
     *
     * @return List of matched gestures across all hands.
     */
    public fun detect(data: HandTrackingData): List<GestureMatch> {
        if (!data.isTracking) return emptyList()
        return data.hands.flatMap { detect(it) }
    }

    private companion object {
        const val DEFAULT_MIN_CONFIDENCE = 0.5f
    }
}

/**
 * A matched custom gesture with its confidence score.
 */
public data class GestureMatch(
    val gestureName: String,
    val confidence: Float,
)
