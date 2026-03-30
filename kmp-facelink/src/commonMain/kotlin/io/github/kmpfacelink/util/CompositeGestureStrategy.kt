package io.github.kmpfacelink.util

import io.github.kmpfacelink.api.GestureClassificationStrategy
import io.github.kmpfacelink.model.GestureClassificationResult
import io.github.kmpfacelink.model.HandLandmarkPoint

/**
 * A composite strategy that tries multiple strategies in order.
 *
 * Returns the first result with non-zero confidence. This allows layering
 * custom gesture recognition on top of built-in rules: the template matcher
 * is tried first, falling back to the rule-based classifier.
 *
 * @property strategies Ordered list of strategies to try. The first match wins.
 */
public class CompositeGestureStrategy(
    private val strategies: List<GestureClassificationStrategy>,
) : GestureClassificationStrategy {

    init {
        require(strategies.isNotEmpty()) { "At least one strategy is required" }
    }

    override fun classify(landmarks: List<HandLandmarkPoint>): GestureClassificationResult {
        for (strategy in strategies) {
            val result = strategy.classify(landmarks)
            if (result.confidence > 0f) return result
        }
        return GestureClassificationResult.NONE
    }
}
