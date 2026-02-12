package io.github.kmpfacelink.model

/**
 * The 21 hand landmarks defined by MediaPipe Hand Landmarker.
 *
 * Enum ordinals match MediaPipe landmark indices (0–20).
 *
 * @see <a href="https://developers.google.com/mediapipe/solutions/vision/hand_landmarker">MediaPipe Hand Landmarker</a>
 */
public enum class HandJoint {
    WRIST,
    THUMB_CMC,
    THUMB_MCP,
    THUMB_IP,
    THUMB_TIP,
    INDEX_FINGER_MCP,
    INDEX_FINGER_PIP,
    INDEX_FINGER_DIP,
    INDEX_FINGER_TIP,
    MIDDLE_FINGER_MCP,
    MIDDLE_FINGER_PIP,
    MIDDLE_FINGER_DIP,
    MIDDLE_FINGER_TIP,
    RING_FINGER_MCP,
    RING_FINGER_PIP,
    RING_FINGER_DIP,
    RING_FINGER_TIP,
    PINKY_MCP,
    PINKY_PIP,
    PINKY_DIP,
    PINKY_TIP,
}
