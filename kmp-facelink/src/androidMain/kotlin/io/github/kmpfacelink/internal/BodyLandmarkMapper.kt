package io.github.kmpfacelink.internal

import io.github.kmpfacelink.model.BodyJoint
import io.github.kmpfacelink.model.BodyLandmarkPoint

/**
 * Maps MediaPipe PoseLandmarker output to KMP-FaceLink model types.
 */
internal object BodyLandmarkMapper {

    private val indexToJoint: Map<Int, BodyJoint> = BodyJoint.entries.associateBy { it.index }

    /**
     * Map MediaPipe landmark index (0-32) to [BodyJoint].
     */
    fun mapJoint(index: Int): BodyJoint? = indexToJoint[index]

    /**
     * Convert a list of MediaPipe normalized landmarks to [BodyLandmarkPoint] list.
     *
     * @param landmarks MediaPipe landmark list (x, y, z normalized)
     */
    fun mapLandmarks(
        landmarks: List<com.google.mediapipe.tasks.components.containers.NormalizedLandmark>,
    ): List<BodyLandmarkPoint> = landmarks.mapIndexedNotNull { index, landmark ->
        val joint = mapJoint(index) ?: return@mapIndexedNotNull null
        BodyLandmarkPoint(
            joint = joint,
            x = landmark.x(),
            y = landmark.y(),
            z = landmark.z(),
            visibility = landmark.visibility().orElse(0f),
        )
    }
}
