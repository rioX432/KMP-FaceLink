package io.github.kmpfacelink.internal

import io.github.kmpfacelink.model.HandJoint
import io.github.kmpfacelink.model.HandLandmarkPoint
import io.github.kmpfacelink.model.Handedness

/**
 * Maps MediaPipe HandLandmarker output to KMP-FaceLink model types.
 */
internal object HandLandmarkMapper {

    /**
     * Map MediaPipe landmark index (0–20) to [HandJoint].
     * MediaPipe indices directly correspond to [HandJoint] ordinals.
     */
    fun mapJoint(index: Int): HandJoint? = HandJoint.entries.getOrNull(index)

    /**
     * Map MediaPipe handedness category name to [Handedness].
     * MediaPipe reports handedness from the camera's perspective,
     * so for front camera we flip: "Left" → RIGHT, "Right" → LEFT.
     *
     * @param categoryName MediaPipe handedness string ("Left" or "Right")
     * @param isFrontCamera Whether the front camera is active
     */
    fun mapHandedness(categoryName: String, isFrontCamera: Boolean): Handedness {
        val raw = when (categoryName) {
            "Left" -> Handedness.LEFT
            "Right" -> Handedness.RIGHT
            else -> Handedness.UNKNOWN
        }

        // Front camera shows a mirrored image, so MediaPipe reports
        // the hand as seen (mirrored). Flip to get the actual hand.
        return if (isFrontCamera && raw != Handedness.UNKNOWN) {
            if (raw == Handedness.LEFT) Handedness.RIGHT else Handedness.LEFT
        } else {
            raw
        }
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
