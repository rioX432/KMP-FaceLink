package io.github.kmpfacelink.model

/**
 * The 33 body pose landmarks defined by MediaPipe Pose Landmarker.
 *
 * Enum ordinals match MediaPipe landmark indices (0-32).
 * iOS Vision framework provides a subset (17 joints); unmapped joints
 * will have visibility = 0.
 *
 * @see <a href="https://developers.google.com/mediapipe/solutions/vision/pose_landmarker">MediaPipe Pose Landmarker</a>
 */
public enum class BodyJoint(
    /** MediaPipe landmark index (0-32). */
    public val index: Int,
) {
    NOSE(0),
    LEFT_EYE_INNER(1),
    LEFT_EYE(2),
    LEFT_EYE_OUTER(3),
    RIGHT_EYE_INNER(4),
    RIGHT_EYE(5),
    RIGHT_EYE_OUTER(6),
    LEFT_EAR(7),
    RIGHT_EAR(8),
    MOUTH_LEFT(9),
    MOUTH_RIGHT(10),
    LEFT_SHOULDER(11),
    RIGHT_SHOULDER(12),
    LEFT_ELBOW(13),
    RIGHT_ELBOW(14),
    LEFT_WRIST(15),
    RIGHT_WRIST(16),
    LEFT_PINKY(17),
    RIGHT_PINKY(18),
    LEFT_INDEX(19),
    RIGHT_INDEX(20),
    LEFT_THUMB(21),
    RIGHT_THUMB(22),
    LEFT_HIP(23),
    RIGHT_HIP(24),
    LEFT_KNEE(25),
    RIGHT_KNEE(26),
    LEFT_ANKLE(27),
    RIGHT_ANKLE(28),
    LEFT_HEEL(29),
    RIGHT_HEEL(30),
    LEFT_FOOT_INDEX(31),
    RIGHT_FOOT_INDEX(32),
}
