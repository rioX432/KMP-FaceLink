package io.github.kmpfacelink.model

/**
 * Result of a gesture classification attempt.
 *
 * @property gestureName Name of the recognized gesture. For built-in gestures this is
 *           [HandGesture.name]; for custom gestures it is the user-supplied name.
 * @property confidence Classification confidence in range 0.0–1.0.
 * @property builtInGesture The [HandGesture] if this is a built-in gesture, or [HandGesture.NONE]
 *           for custom gestures.
 */
public data class GestureClassificationResult(
    val gestureName: String,
    val confidence: Float,
    val builtInGesture: HandGesture = HandGesture.NONE,
) {
    public companion object {
        /** No gesture recognized. */
        public val NONE: GestureClassificationResult = GestureClassificationResult(
            gestureName = HandGesture.NONE.name,
            confidence = 0f,
            builtInGesture = HandGesture.NONE,
        )
    }
}
