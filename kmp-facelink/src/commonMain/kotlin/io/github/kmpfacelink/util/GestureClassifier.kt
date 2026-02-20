package io.github.kmpfacelink.util

import io.github.kmpfacelink.model.HandGesture
import io.github.kmpfacelink.model.HandJoint
import io.github.kmpfacelink.model.HandLandmarkPoint
import kotlin.math.sqrt

/**
 * Pure Kotlin geometric gesture classifier.
 *
 * Classifies hand gestures from 21 landmark positions by computing
 * finger extension states and matching against known gesture patterns.
 */
internal object GestureClassifier {

    private const val FINGER_EXTENSION_THRESHOLD = 1.3f
    private const val THUMB_EXTENSION_THRESHOLD = 1.2f
    private const val DISTANCE_EPSILON = 1e-6f
    private const val HIGH_CONFIDENCE = 0.9f
    private const val MEDIUM_CONFIDENCE = 0.7f
    private const val PINCH_DISTANCE_THRESHOLD = 0.06f
    private const val PINCH_MIN_REACH_THRESHOLD = 0.2f

    /**
     * Classify a gesture from hand landmarks.
     *
     * @param landmarks The 21 hand landmark points
     * @return Pair of recognized gesture and confidence (0.0–1.0)
     */
    fun classify(landmarks: List<HandLandmarkPoint>): Pair<HandGesture, Float> {
        if (landmarks.size < HandJoint.entries.size) {
            return HandGesture.NONE to 0f
        }

        val landmarkMap = landmarks.associateBy { it.joint }
        val fingers = FingerState(
            thumb = isThumbExtended(landmarkMap),
            index = isFingerExtended(landmarkMap, HandJoint.INDEX_FINGER_MCP, HandJoint.INDEX_FINGER_TIP),
            middle = isFingerExtended(landmarkMap, HandJoint.MIDDLE_FINGER_MCP, HandJoint.MIDDLE_FINGER_TIP),
            ring = isFingerExtended(landmarkMap, HandJoint.RING_FINGER_MCP, HandJoint.RING_FINGER_TIP),
            pinky = isFingerExtended(landmarkMap, HandJoint.PINKY_MCP, HandJoint.PINKY_TIP),
        )

        return matchGesture(fingers, landmarkMap)
    }

    private fun isThumbExtended(landmarks: Map<HandJoint, HandLandmarkPoint>): Boolean {
        val thumbTip = landmarks[HandJoint.THUMB_TIP] ?: return false
        val thumbCmc = landmarks[HandJoint.THUMB_CMC] ?: return false
        val indexMcp = landmarks[HandJoint.INDEX_FINGER_MCP] ?: return false

        val tipToIndex = distance(thumbTip, indexMcp)
        val cmcToIndex = distance(thumbCmc, indexMcp)

        if (cmcToIndex < DISTANCE_EPSILON) return false
        return tipToIndex / cmcToIndex > THUMB_EXTENSION_THRESHOLD
    }

    private fun isFingerExtended(
        landmarks: Map<HandJoint, HandLandmarkPoint>,
        mcp: HandJoint,
        tip: HandJoint,
    ): Boolean {
        val wrist = landmarks[HandJoint.WRIST] ?: return false
        val mcpPoint = landmarks[mcp] ?: return false
        val tipPoint = landmarks[tip] ?: return false

        val tipToWrist = distance(tipPoint, wrist)
        val mcpToWrist = distance(mcpPoint, wrist)

        if (mcpToWrist < DISTANCE_EPSILON) return false
        return tipToWrist / mcpToWrist > FINGER_EXTENSION_THRESHOLD
    }

    @Suppress("CyclomaticComplexMethod")
    private fun matchGesture(
        fingers: FingerState,
        landmarks: Map<HandJoint, HandLandmarkPoint>,
    ): Pair<HandGesture, Float> {
        val allCurled = !fingers.thumb && !fingers.index && !fingers.middle && !fingers.ring && !fingers.pinky
        val allExtended = fingers.thumb && fingers.index && fingers.middle && fingers.ring && fingers.pinky
        val pinching = isPinching(landmarks)

        return when {
            allCurled -> HandGesture.CLOSED_FIST to HIGH_CONFIDENCE
            // Pinch/OK checked before OPEN_PALM: thumb-index proximity takes priority
            pinching && fingers.middle && fingers.ring && fingers.pinky ->
                HandGesture.OK_SIGN to HIGH_CONFIDENCE
            pinching && !fingers.middle && !fingers.ring && !fingers.pinky ->
                HandGesture.PINCH to HIGH_CONFIDENCE
            allExtended -> HandGesture.OPEN_PALM to HIGH_CONFIDENCE
            // Rock/metal: index + pinky only
            !fingers.thumb && fingers.index && !fingers.middle && !fingers.ring && fingers.pinky ->
                HandGesture.ROCK to HIGH_CONFIDENCE
            fingers.thumb && fingers.index && !fingers.middle && !fingers.ring && fingers.pinky ->
                HandGesture.I_LOVE_YOU to HIGH_CONFIDENCE
            !fingers.thumb && fingers.index && fingers.middle && !fingers.ring && !fingers.pinky ->
                HandGesture.VICTORY to HIGH_CONFIDENCE
            // Finger counting: 3, 4 (1 and 2 are handled by POINTING_UP and VICTORY aliases)
            !fingers.thumb && fingers.index && fingers.middle && fingers.ring && !fingers.pinky ->
                HandGesture.FINGER_COUNT_THREE to HIGH_CONFIDENCE
            !fingers.thumb && fingers.index && fingers.middle && fingers.ring && fingers.pinky ->
                HandGesture.FINGER_COUNT_FOUR to HIGH_CONFIDENCE
            fingers.index && !fingers.middle && !fingers.ring && !fingers.pinky ->
                classifyPointingUp(fingers)
            fingers.thumb && !fingers.index && !fingers.middle && !fingers.ring && !fingers.pinky ->
                classifyThumb(landmarks)
            else -> HandGesture.NONE to 0f
        }
    }

    private fun isPinching(landmarks: Map<HandJoint, HandLandmarkPoint>): Boolean {
        val thumbTip = landmarks[HandJoint.THUMB_TIP] ?: return false
        val indexTip = landmarks[HandJoint.INDEX_FINGER_TIP] ?: return false
        val wrist = landmarks[HandJoint.WRIST] ?: return false

        // Tips must be close together
        if (distance(thumbTip, indexTip) >= PINCH_DISTANCE_THRESHOLD) return false

        // Both tips must be sufficiently far from wrist (not just curled together)
        val thumbToWrist = distance(thumbTip, wrist)
        val indexToWrist = distance(indexTip, wrist)
        val minReach = PINCH_MIN_REACH_THRESHOLD
        return thumbToWrist > minReach && indexToWrist > minReach
    }

    private fun classifyPointingUp(fingers: FingerState): Pair<HandGesture, Float> =
        if (!fingers.thumb) {
            HandGesture.POINTING_UP to HIGH_CONFIDENCE
        } else {
            HandGesture.POINTING_UP to MEDIUM_CONFIDENCE
        }

    private fun classifyThumb(landmarks: Map<HandJoint, HandLandmarkPoint>): Pair<HandGesture, Float> {
        val thumbTip = landmarks[HandJoint.THUMB_TIP]
        val wrist = landmarks[HandJoint.WRIST]
        return if (thumbTip != null && wrist != null && thumbTip.y > wrist.y) {
            HandGesture.THUMB_DOWN to HIGH_CONFIDENCE
        } else {
            HandGesture.THUMB_UP to HIGH_CONFIDENCE
        }
    }

    private fun distance(a: HandLandmarkPoint, b: HandLandmarkPoint): Float {
        val dx = a.x - b.x
        val dy = a.y - b.y
        val dz = a.z - b.z
        return sqrt(dx * dx + dy * dy + dz * dz)
    }

    private data class FingerState(
        val thumb: Boolean,
        val index: Boolean,
        val middle: Boolean,
        val ring: Boolean,
        val pinky: Boolean,
    )
}
