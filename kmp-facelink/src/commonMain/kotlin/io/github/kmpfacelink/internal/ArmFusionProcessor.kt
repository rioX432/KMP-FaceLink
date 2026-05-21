package io.github.kmpfacelink.internal

import io.github.kmpfacelink.model.ArmJointPoint
import io.github.kmpfacelink.model.ArmSource
import io.github.kmpfacelink.model.ArmTrackingData
import io.github.kmpfacelink.model.BodyJoint
import io.github.kmpfacelink.model.BodyTrackingData
import io.github.kmpfacelink.model.HandTrackingData
import io.github.kmpfacelink.model.Handedness
import io.github.kmpfacelink.model.TrackedArm

/**
 * Fuses body pose arm joints with hand tracker wrist landmarks
 * to produce unified [ArmTrackingData].
 *
 * Fusion strategy:
 * - **Body only**: Uses body pose shoulder, elbow, wrist when hand tracker has no data.
 * - **Hand only**: Uses hand tracker wrist when body pose is unavailable.
 * - **Fused**: Body provides shoulder/elbow, hand tracker refines wrist (higher precision).
 *
 * Applies exponential moving average (EMA) smoothing during signal transitions
 * to avoid sudden jumps when one source appears or disappears.
 */
internal class ArmFusionProcessor {

    private var previousLeftArm: TrackedArm? = null
    private var previousRightArm: TrackedArm? = null

    /**
     * Process a frame of body and hand tracking data to produce fused arm data.
     *
     * @param body Body tracking data, or null if body tracking is not available
     * @param hand Hand tracking data, or null if hand tracking is not available
     * @return Fused arm tracking data, or null if neither source has data
     */
    internal fun process(body: BodyTrackingData?, hand: HandTrackingData?): ArmTrackingData? {
        if (body == null && hand == null) {
            previousLeftArm = null
            previousRightArm = null
            return null
        }
        if (!hasAnyArmData(body, hand)) {
            previousLeftArm = null
            previousRightArm = null
            return null
        }

        val leftArm = fuseArm(body, hand, ArmSide.LEFT)
        val rightArm = fuseArm(body, hand, ArmSide.RIGHT)

        val smoothedLeft = smoothTransition(leftArm, previousLeftArm)
        val smoothedRight = smoothTransition(rightArm, previousRightArm)

        previousLeftArm = smoothedLeft
        previousRightArm = smoothedRight

        if (smoothedLeft == null && smoothedRight == null) return null

        return ArmTrackingData(
            leftArm = smoothedLeft,
            rightArm = smoothedRight,
        )
    }

    /** Reset smoother state (e.g. after tracking loss or scene change). */
    internal fun reset() {
        previousLeftArm = null
        previousRightArm = null
    }
}

private enum class ArmSide { LEFT, RIGHT }

private fun hasAnyArmData(body: BodyTrackingData?, hand: HandTrackingData?): Boolean {
    val hasBody = body?.isTracking == true && body.bodies.isNotEmpty()
    val hasHand = hand?.isTracking == true && hand.hands.isNotEmpty()
    return hasBody || hasHand
}

private fun fuseArm(
    body: BodyTrackingData?,
    hand: HandTrackingData?,
    side: ArmSide,
): TrackedArm? {
    val bodyArm = extractBodyArm(body, side)
    val handWrist = extractHandWrist(hand, side)

    return when {
        bodyArm != null && handWrist != null -> fusedArm(bodyArm, handWrist)
        bodyArm != null -> bodyArm
        handWrist != null -> handOnlyArm(handWrist)
        else -> null
    }
}

private fun extractBodyArm(body: BodyTrackingData?, side: ArmSide): TrackedArm? {
    if (body == null || !body.isTracking || body.bodies.isEmpty()) return null
    val landmarks = body.bodies.first().landmarks
    val joints = resolveArmJoints(side)

    val shoulder = landmarks.find { it.joint == joints.first }
    val elbow = landmarks.find { it.joint == joints.second }
    val wrist = landmarks.find { it.joint == joints.third }

    // All three joints must be found and shoulder must meet minimum visibility
    if (shoulder == null || elbow == null || wrist == null) return null
    if (shoulder.visibility < MIN_BODY_VISIBILITY) return null

    return TrackedArm(
        shoulder = ArmJointPoint(shoulder.x, shoulder.y, shoulder.z, shoulder.visibility),
        elbow = ArmJointPoint(elbow.x, elbow.y, elbow.z, elbow.visibility),
        wrist = ArmJointPoint(wrist.x, wrist.y, wrist.z, wrist.visibility),
        source = ArmSource.BODY_ONLY,
    )
}

private fun resolveArmJoints(side: ArmSide): Triple<BodyJoint, BodyJoint, BodyJoint> =
    when (side) {
        ArmSide.LEFT -> Triple(BodyJoint.LEFT_SHOULDER, BodyJoint.LEFT_ELBOW, BodyJoint.LEFT_WRIST)
        ArmSide.RIGHT -> Triple(
            BodyJoint.RIGHT_SHOULDER,
            BodyJoint.RIGHT_ELBOW,
            BodyJoint.RIGHT_WRIST,
        )
    }

private fun extractHandWrist(hand: HandTrackingData?, side: ArmSide): ArmJointPoint? {
    if (hand == null || !hand.isTracking || hand.hands.isEmpty()) return null

    val targetHandedness = when (side) {
        ArmSide.LEFT -> Handedness.LEFT
        ArmSide.RIGHT -> Handedness.RIGHT
    }

    val trackedHand = hand.hands.find { it.handedness == targetHandedness } ?: return null
    val wrist = trackedHand.landmarks.firstOrNull() ?: return null

    return ArmJointPoint(wrist.x, wrist.y, wrist.z, confidence = 1f)
}

private fun fusedArm(bodyArm: TrackedArm, handWrist: ArmJointPoint): TrackedArm {
    return bodyArm.copy(
        wrist = handWrist,
        source = ArmSource.FUSED,
    )
}

private fun handOnlyArm(wrist: ArmJointPoint): TrackedArm {
    // With only hand tracker data, we only know the wrist position.
    // Shoulder and elbow are set to wrist position with zero confidence.
    return TrackedArm(
        shoulder = ArmJointPoint(wrist.x, wrist.y, wrist.z, confidence = 0f),
        elbow = ArmJointPoint(wrist.x, wrist.y, wrist.z, confidence = 0f),
        wrist = wrist,
        source = ArmSource.HAND_ONLY,
    )
}

/**
 * Smooth transitions when an arm signal appears or disappears.
 * Uses exponential moving average to avoid sudden jumps.
 */
private fun smoothTransition(current: TrackedArm?, previous: TrackedArm?): TrackedArm? {
    if (current == null) return null
    if (previous == null) return current

    return TrackedArm(
        shoulder = lerpJoint(previous.shoulder, current.shoulder),
        elbow = lerpJoint(previous.elbow, current.elbow),
        wrist = lerpJoint(previous.wrist, current.wrist),
        source = current.source,
    )
}

private fun lerpJoint(previous: ArmJointPoint, current: ArmJointPoint): ArmJointPoint {
    return ArmJointPoint(
        x = emaLerp(previous.x, current.x),
        y = emaLerp(previous.y, current.y),
        z = emaLerp(previous.z, current.z),
        confidence = current.confidence,
    )
}

private fun emaLerp(previous: Float, current: Float): Float {
    return previous + EMA_ALPHA * (current - previous)
}

/** EMA smoothing factor. Higher = less smoothing, more responsive. */
private const val EMA_ALPHA = 0.7f

/** Minimum body landmark visibility to consider the arm data valid. */
private const val MIN_BODY_VISIBILITY = 0.3f
