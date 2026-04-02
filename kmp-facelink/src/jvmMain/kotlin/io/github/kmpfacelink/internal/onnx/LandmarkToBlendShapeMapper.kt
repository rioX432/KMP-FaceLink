package io.github.kmpfacelink.internal.onnx

import io.github.kmpfacelink.model.BlendShape
import io.github.kmpfacelink.model.BlendShapeData

/**
 * Maps OpenSeeFace 66-point landmarks to ARKit-compatible blend shape values.
 *
 * Delegates to specialized solver objects for each facial region.
 * Produces a subset of the 52 ARKit blend shapes — shapes that cannot
 * be reliably estimated from 66 landmarks are set to 0.
 */
internal object LandmarkToBlendShapeMapper {

    /**
     * Compute ARKit blend shapes from landmark positions.
     *
     * @param landmarks Flat array of 66 (x, y) pairs in [0,1] normalized coordinates.
     * @return Map of all 52 ARKit blend shapes with estimated values.
     */
    fun map(landmarks: FloatArray): BlendShapeData {
        val output = HashMap<BlendShape, Float>(BlendShape.entries.size)

        // Initialize all shapes to 0
        for (shape in BlendShape.entries) {
            output[shape] = 0f
        }

        // Let each solver populate its shapes
        EyeBlendShapeSolver.solve(landmarks, output)
        MouthBlendShapeSolver.solve(landmarks, output)
        BrowBlendShapeSolver.solve(landmarks, output)

        return output
    }
}
