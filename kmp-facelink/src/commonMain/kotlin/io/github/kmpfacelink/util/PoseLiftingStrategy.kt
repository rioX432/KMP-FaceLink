package io.github.kmpfacelink.util

import io.github.kmpfacelink.model.BodyLandmarkPoint

/**
 * Strategy for estimating 3D depth (z-axis) from 2D body pose landmarks.
 *
 * Implementations provide different approaches to infer depth information
 * from 2D joint positions, ranging from simple heuristics to ML-based methods.
 */
public interface PoseLiftingStrategy {

    /**
     * Estimate z-depth for each landmark based on the full set of 2D pose points.
     *
     * @param landmarks The 2D body landmarks with x, y populated (z may be 0 or from MediaPipe)
     * @return A new list of [BodyLandmarkPoint] with estimated z values
     */
    public fun estimateDepth(landmarks: List<BodyLandmarkPoint>): List<BodyLandmarkPoint>
}
