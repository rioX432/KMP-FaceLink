package io.github.kmpfacelink.api

import io.github.kmpfacelink.model.GestureClassificationResult
import io.github.kmpfacelink.model.HandLandmarkPoint

/**
 * Strategy interface for gesture classification.
 *
 * Implementations can use geometric rules, template matching, neural networks,
 * or any other classification approach. The classifier receives 21 hand landmarks
 * and returns a [GestureClassificationResult].
 */
public interface GestureClassificationStrategy {

    /**
     * Classify a gesture from hand landmarks.
     *
     * @param landmarks The 21 hand landmark points (one per [io.github.kmpfacelink.model.HandJoint])
     * @return Classification result with gesture name and confidence
     */
    public fun classify(landmarks: List<HandLandmarkPoint>): GestureClassificationResult
}
