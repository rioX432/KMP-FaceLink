package io.github.kmpfacelink.effects

import io.github.kmpfacelink.model.FaceLandmark
import io.github.kmpfacelink.model.HeadTransform

/**
 * Resolves a face landmark index to an [AnchorData] position.
 */
internal object AnchorResolver {

    /**
     * Resolves a landmark by index from the given list.
     *
     * @param landmarks Face landmark list (typically 478 points)
     * @param landmarkIndex Index into the landmark list
     * @param rotationSource How to compute rotation
     * @param headTransform Head transform for rotation (used when [rotationSource] is HEAD_TRANSFORM)
     * @return Resolved anchor data, or null if index is out of bounds or landmarks are empty
     */
    fun resolve(
        landmarks: List<FaceLandmark>,
        landmarkIndex: Int,
        rotationSource: RotationSource,
        headTransform: HeadTransform,
    ): AnchorData? {
        if (landmarks.isEmpty() || landmarkIndex < 0 || landmarkIndex >= landmarks.size) {
            return null
        }
        val landmark = landmarks[landmarkIndex]
        val rotation = when (rotationSource) {
            RotationSource.HEAD_TRANSFORM -> headTransform.roll
            RotationSource.NONE -> 0f
        }
        return AnchorData(
            position = Position2D(landmark.x, landmark.y),
            rotationDegrees = rotation,
        )
    }
}
