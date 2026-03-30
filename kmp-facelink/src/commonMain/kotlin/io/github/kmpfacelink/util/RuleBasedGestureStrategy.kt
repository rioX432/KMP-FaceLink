package io.github.kmpfacelink.util

import io.github.kmpfacelink.api.GestureClassificationStrategy
import io.github.kmpfacelink.model.GestureClassificationResult
import io.github.kmpfacelink.model.HandGesture
import io.github.kmpfacelink.model.HandLandmarkPoint

/**
 * Strategy that delegates to the existing geometric [GestureClassifier].
 *
 * This wraps the original rule-based classifier as a [GestureClassificationStrategy]
 * so it can be used interchangeably with ML-based strategies.
 */
public class RuleBasedGestureStrategy : GestureClassificationStrategy {

    override fun classify(landmarks: List<HandLandmarkPoint>): GestureClassificationResult {
        val (gesture, confidence) = GestureClassifier.classify(landmarks)
        return if (gesture == HandGesture.NONE) {
            GestureClassificationResult.NONE
        } else {
            GestureClassificationResult(
                gestureName = gesture.name,
                confidence = confidence,
                builtInGesture = gesture,
            )
        }
    }
}
