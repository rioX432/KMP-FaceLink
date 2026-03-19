package io.github.kmpfacelink.model

/**
 * Per-face tracking data within a multi-face frame.
 *
 * @property faceIndex Zero-based index of this face in the detection result.
 * @property blendShapes 52 ARKit-compatible blend shape values.
 * @property headTransform 6DoF head position and rotation.
 * @property gazeData Estimated gaze direction, or null if unavailable.
 * @property landmarks Raw face landmarks (478 MediaPipe points on Android, empty on iOS).
 * @property confidence Detection confidence (0.0–1.0).
 */
public data class TrackedFace(
    val faceIndex: Int,
    val blendShapes: BlendShapeData,
    val headTransform: HeadTransform,
    val gazeData: GazeData? = null,
    val landmarks: List<FaceLandmark> = emptyList(),
    val confidence: Float = 1.0f,
)
