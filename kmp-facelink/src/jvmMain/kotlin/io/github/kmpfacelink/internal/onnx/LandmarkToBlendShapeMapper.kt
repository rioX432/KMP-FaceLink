package io.github.kmpfacelink.internal.onnx

import io.github.kmpfacelink.model.BlendShape
import io.github.kmpfacelink.model.BlendShapeData

/**
 * Maps OpenSeeFace 66-point landmarks to ARKit-compatible blend shape values.
 *
 * Delegates to specialized solver objects for each facial region. The result holds only the shapes
 * a solver actually measured. A shape this estimator does not produce is absent from the map rather
 * than present as `0f`, so a downstream consumer can tell an observed zero from a shape that was
 * never measured. The exact key set is pinned by `LandmarkToBlendShapeMapperTest`.
 */
internal object LandmarkToBlendShapeMapper {

    /**
     * Compute ARKit blend shapes from landmark positions.
     *
     * @param landmarks Flat array of 66 (x, y) pairs in [0,1] normalized coordinates.
     * @return The shapes this estimator measured, and only those.
     */
    fun map(landmarks: FloatArray): BlendShapeData {
        val output = HashMap<BlendShape, Float>()

        EyeBlendShapeSolver.solve(landmarks, output)
        MouthBlendShapeSolver.solve(landmarks, output)
        BrowBlendShapeSolver.solve(landmarks, output)

        return output
    }
}
