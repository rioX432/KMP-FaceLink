package io.github.kmpfacelink.model

/**
 * Configuration for [io.github.kmpfacelink.api.HolisticTracker].
 *
 * Enables simultaneous face, hand, and body tracking through a single
 * unified pipeline sharing one camera session.
 *
 * @property enableFace Whether to enable face tracking
 * @property enableHand Whether to enable hand tracking
 * @property enableBody Whether to enable body tracking
 * @property faceConfig Configuration for the face tracker (used when [enableFace] is true)
 * @property handConfig Configuration for the hand tracker (used when [enableHand] is true)
 * @property bodyConfig Configuration for the body tracker (used when [enableBody] is true)
 * @property cameraFacing Which camera to use. Overrides per-tracker camera facing settings.
 */
public data class HolisticTrackerConfig(
    val enableFace: Boolean = true,
    val enableHand: Boolean = true,
    val enableBody: Boolean = true,
    val faceConfig: FaceTrackerConfig = FaceTrackerConfig(),
    val handConfig: HandTrackerConfig = HandTrackerConfig(),
    val bodyConfig: BodyTrackerConfig = BodyTrackerConfig(),
    val cameraFacing: CameraFacing = CameraFacing.FRONT,
) {
    init {
        require(enableFace || enableHand || enableBody) {
            "At least one tracking modality must be enabled"
        }
    }
}

/**
 * Tracking modality that can be enabled in holistic tracking.
 */
public enum class TrackingModality {
    /** Face blend shapes, head transform, and landmarks */
    FACE,

    /** Hand landmarks and gestures */
    HAND,

    /** Body pose landmarks */
    BODY,
}
