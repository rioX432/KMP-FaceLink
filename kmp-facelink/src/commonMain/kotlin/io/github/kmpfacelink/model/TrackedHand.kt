package io.github.kmpfacelink.model

/**
 * Tracking result for a single detected hand.
 *
 * @property handedness Whether the hand is left or right
 * @property landmarks The 21 hand landmark points
 * @property gesture Classified gesture (requires gesture recognition enabled)
 * @property gestureConfidence Confidence of the gesture classification (0.0–1.0)
 */
public data class TrackedHand(
    val handedness: Handedness,
    val landmarks: List<HandLandmarkPoint>,
    val gesture: HandGesture = HandGesture.NONE,
    val gestureConfidence: Float = 0f,
)
