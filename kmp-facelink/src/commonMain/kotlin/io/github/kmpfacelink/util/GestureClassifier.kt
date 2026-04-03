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
 *
 * **Revision resilience**: All thresholds use *relative* distance ratios
 * (e.g., tip-to-wrist / mcp-to-wrist) rather than absolute positions.
 * This makes gesture classification largely invariant to the iOS Vision
 * hand pose model revision (v1 vs v2), since the relative proportions of
 * a hand pose are preserved even when absolute joint locations shift.
 * The exception is [PINCH_DISTANCE_THRESHOLD] which uses an absolute
 * distance in normalized coordinates — if the v2 model changes the scale
 * of the coordinate space, this threshold may need recalibration.
 *
 * @see io.github.kmpfacelink.model.HandPoseRevision
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

    private fun matchGesture(
        fingers: FingerState,
        landmarks: Map<HandJoint, HandLandmarkPoint>,
    ): Pair<HandGesture, Float> {
        // Pinch/OK checked before pattern table: thumb-index proximity takes priority
        if (isPinching(landmarks)) {
            matchPinchGestures(fingers)?.let { return it }
        }

        // Encode finger state as a 5-bit key (thumb=16, index=8, middle=4, ring=2, pinky=1)
        val key = fingers.toBitKey()
        GESTURE_PATTERN_TABLE[key]?.let { return it to HIGH_CONFIDENCE }

        // Patterns that need special handling (thumb-agnostic or landmark-based)
        return matchSpecialPatterns(fingers, landmarks)
    }

    private fun matchPinchGestures(fingers: FingerState): Pair<HandGesture, Float>? =
        when (fingers.toBitKey() and NON_THUMB_INDEX_MASK) {
            // middle + ring + pinky extended → OK_SIGN
            MIDDLE_RING_PINKY_BITS -> HandGesture.OK_SIGN to HIGH_CONFIDENCE
            // middle + ring + pinky curled → PINCH
            0 -> HandGesture.PINCH to HIGH_CONFIDENCE
            else -> null
        }

    private fun matchSpecialPatterns(
        fingers: FingerState,
        landmarks: Map<HandJoint, HandLandmarkPoint>,
    ): Pair<HandGesture, Float> {
        val key = fingers.toBitKey()
        // Index only (thumb agnostic): key is INDEX_BIT or THUMB_BIT|INDEX_BIT
        val isIndexOnly = (key and NON_THUMB_INDEX_MASK) == 0 && fingers.index
        if (isIndexOnly) return classifyPointingUp(fingers)

        // Thumb only → THUMB_UP or THUMB_DOWN
        if (key == THUMB_BIT) return classifyThumb(landmarks)

        return HandGesture.NONE to 0f
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

    // Bit positions: thumb=16, index=8, middle=4, ring=2, pinky=1
    private const val THUMB_BIT = 16
    private const val INDEX_BIT = 8
    private const val MIDDLE_BIT = 4
    private const val RING_BIT = 2
    private const val PINKY_BIT = 1

    /** Mask for middle + ring + pinky bits (used in pinch sub-classification). */
    private const val NON_THUMB_INDEX_MASK = MIDDLE_BIT or RING_BIT or PINKY_BIT
    private const val MIDDLE_RING_PINKY_BITS = MIDDLE_BIT or RING_BIT or PINKY_BIT

    /**
     * Lookup table mapping exact 5-bit finger patterns to gestures.
     * Patterns that require landmark-based or thumb-agnostic logic are handled separately.
     */
    private val GESTURE_PATTERN_TABLE: Map<Int, HandGesture> = mapOf(
        // All curled → CLOSED_FIST
        0 to HandGesture.CLOSED_FIST,
        // All extended → OPEN_PALM
        (THUMB_BIT or INDEX_BIT or MIDDLE_BIT or RING_BIT or PINKY_BIT) to HandGesture.OPEN_PALM,
        // Rock/metal: index + pinky only
        (INDEX_BIT or PINKY_BIT) to HandGesture.ROCK,
        // I love you: thumb + index + pinky
        (THUMB_BIT or INDEX_BIT or PINKY_BIT) to HandGesture.I_LOVE_YOU,
        // Victory: index + middle
        (INDEX_BIT or MIDDLE_BIT) to HandGesture.VICTORY,
        // Finger count 3: index + middle + ring
        (INDEX_BIT or MIDDLE_BIT or RING_BIT) to HandGesture.FINGER_COUNT_THREE,
        // Finger count 4: index + middle + ring + pinky
        (INDEX_BIT or MIDDLE_BIT or RING_BIT or PINKY_BIT) to HandGesture.FINGER_COUNT_FOUR,
    )

    private data class FingerState(
        val thumb: Boolean,
        val index: Boolean,
        val middle: Boolean,
        val ring: Boolean,
        val pinky: Boolean,
    ) {
        fun toBitKey(): Int {
            var key = 0
            if (thumb) key = key or THUMB_BIT
            if (index) key = key or INDEX_BIT
            if (middle) key = key or MIDDLE_BIT
            if (ring) key = key or RING_BIT
            if (pinky) key = key or PINKY_BIT
            return key
        }
    }
}
