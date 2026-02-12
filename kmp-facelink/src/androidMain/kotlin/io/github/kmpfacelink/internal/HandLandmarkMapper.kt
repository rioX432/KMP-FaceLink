package io.github.kmpfacelink.internal

import io.github.kmpfacelink.model.HandJoint
import io.github.kmpfacelink.model.HandLandmarkPoint
import io.github.kmpfacelink.model.Handedness

/**
 * Maps MediaPipe HandLandmarker output to KMP-FaceLink model types.
 */
internal object HandLandmarkMapper {

    private val indexToJoint: Map<Int, HandJoint> = HandJoint.entries.associateBy { it.index }

    /**
     * Map MediaPipe landmark index (0–20) to [HandJoint].
     */
    fun mapJoint(index: Int): HandJoint? = indexToJoint[index]

    /**
     * Map MediaPipe handedness category name to [Handedness].
     *
     * The image is pre-mirrored before MediaPipe processing for the front camera,
     * so MediaPipe sees an already-mirrored image and reports the correct handedness
     * from the user's perspective. No flip is needed.
     *
     * @param categoryName MediaPipe handedness string ("Left" or "Right")
     * @param isFrontCamera Retained for API compatibility (unused after pre-mirror)
     */
    @Suppress("UNUSED_PARAMETER")
    fun mapHandedness(categoryName: String, isFrontCamera: Boolean): Handedness =
        when (categoryName) {
            "Left" -> Handedness.LEFT
            "Right" -> Handedness.RIGHT
            else -> Handedness.UNKNOWN
        }

    /**
     * Convert a list of MediaPipe normalized landmarks to [HandLandmarkPoint] list.
     *
     * @param landmarks MediaPipe landmark list (x, y, z normalized)
     */
    fun mapLandmarks(
        landmarks: List<com.google.mediapipe.tasks.components.containers.NormalizedLandmark>,
    ): List<HandLandmarkPoint> = landmarks.mapIndexedNotNull { index, landmark ->
        val joint = mapJoint(index) ?: return@mapIndexedNotNull null
        HandLandmarkPoint(
            joint = joint,
            x = landmark.x(),
            y = landmark.y(),
            z = landmark.z(),
        )
    }
}
