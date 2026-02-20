package io.github.kmpfacelink.model

/**
 * Recognized hand gestures, classified from landmark geometry.
 */
public enum class HandGesture {
    NONE,
    CLOSED_FIST,
    OPEN_PALM,
    POINTING_UP,
    THUMB_UP,
    THUMB_DOWN,
    VICTORY,
    I_LOVE_YOU,

    /** Pinch: thumb tip touching index finger tip. */
    PINCH,

    /** OK sign: thumb tip touching index finger tip, other fingers extended. */
    OK_SIGN,

    /** Rock / metal sign: index + pinky extended, others curled. */
    ROCK,

    /** One finger: index only extended (alias for POINTING_UP with different semantics). */
    FINGER_COUNT_ONE,

    /** Two fingers: index + middle extended (alias for VICTORY with counting semantics). */
    FINGER_COUNT_TWO,

    /** Three fingers: index + middle + ring extended. */
    FINGER_COUNT_THREE,

    /** Four fingers: all except thumb extended. */
    FINGER_COUNT_FOUR,

    /** Five fingers: all extended (alias for OPEN_PALM with counting semantics). */
    FINGER_COUNT_FIVE,
}
