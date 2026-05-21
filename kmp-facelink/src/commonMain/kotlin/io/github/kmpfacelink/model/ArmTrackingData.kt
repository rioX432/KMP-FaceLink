package io.github.kmpfacelink.model

/**
 * Unified arm tracking data fused from body pose and hand tracker sources.
 *
 * When [io.github.kmpfacelink.model.HolisticTrackerConfig.fuseBodyAndHandArm] is enabled,
 * this combines body pose shoulder/elbow/wrist joints with hand tracker wrist landmarks
 * to produce a more accurate arm estimation.
 *
 * @property leftArm Tracking data for the left arm, or null if not detected
 * @property rightArm Tracking data for the right arm, or null if not detected
 */
public data class ArmTrackingData(
    val leftArm: TrackedArm?,
    val rightArm: TrackedArm?,
) {
    /** Whether any arm is currently being tracked. */
    public val isTracking: Boolean
        get() = leftArm != null || rightArm != null

    public companion object {
        /** Returns a "no arms detected" data instance. */
        public fun notTracking(): ArmTrackingData = ArmTrackingData(
            leftArm = null,
            rightArm = null,
        )
    }
}

/**
 * Tracking data for a single arm (left or right).
 *
 * @property shoulder Shoulder joint position in normalized image coordinates
 * @property elbow Elbow joint position in normalized image coordinates
 * @property wrist Wrist joint position in normalized image coordinates
 * @property source Indicates which tracker(s) contributed to this arm data
 */
public data class TrackedArm(
    val shoulder: ArmJointPoint,
    val elbow: ArmJointPoint,
    val wrist: ArmJointPoint,
    val source: ArmSource,
)

/**
 * A single arm joint point with normalized coordinates.
 *
 * Coordinates follow the same convention as [BodyLandmarkPoint]:
 * - (0, 0) is top-left, (1, 1) is bottom-right of the image
 *
 * @property x Horizontal position, normalized 0.0 (left) to 1.0 (right)
 * @property y Vertical position, normalized 0.0 (top) to 1.0 (bottom)
 * @property z Depth value (platform-dependent, 0 when not available)
 * @property confidence Confidence/visibility of this joint (0.0-1.0)
 */
public data class ArmJointPoint(
    val x: Float,
    val y: Float,
    val z: Float = 0f,
    val confidence: Float = 0f,
)

/**
 * Indicates which tracker source(s) contributed to the arm tracking data.
 */
public enum class ArmSource {
    /** Arm data sourced exclusively from body pose tracker (shoulder, elbow, wrist). */
    BODY_ONLY,

    /** Wrist position sourced exclusively from hand tracker. */
    HAND_ONLY,

    /** Body pose provides shoulder/elbow, hand tracker refines wrist position. */
    FUSED,
}
