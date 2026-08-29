package io.github.kmpfacelink.model

/**
 * Complete face tracking data for a single frame.
 *
 * For single-face use cases, the top-level properties ([blendShapes], [headTransform],
 * [gazeData], [landmarks]) represent the primary (first) detected face and are kept for
 * backward compatibility.
 *
 * For multi-face use cases, use [faces] which contains per-face data for every detected
 * face. When a face is detected, [faces] will have at least one entry matching the primary
 * face data.
 *
 * @property blendShapes Map of 52 ARKit-compatible blend shape values (0.0–1.0) for the primary face.
 * @property headTransform 6DoF head position and rotation for the primary face.
 * @property gazeData Eye gaze direction derived from blend shapes for the primary face. Null if not computed.
 * @property landmarks Face landmark points with normalized coordinates (0.0–1.0) for the primary face.
 *           Android (MediaPipe): 478 points when a face is detected, empty otherwise.
 *           iOS (ARKit): Always empty — ARKit does not expose raw face mesh vertices.
 * @property sourceImageWidth Width of the source image that landmarks are normalized to (pixels, after rotation)
 * @property sourceImageHeight Height of the source image that landmarks are normalized to (pixels, after rotation)
 * @property timestampMs Frame timestamp in milliseconds (monotonic clock)
 * @property isTracking Whether a face is currently being tracked
 * @property faces All detected faces in this frame. Empty when no face is detected.
 *           When tracking, contains at least one entry; index 0 corresponds to the primary face
 *           represented by the top-level single-face properties.
 * @property thermalState Current device thermal state at the time of this frame.
 * @property contextEpoch Immutable runtime context captured with the estimator input.
 *           Tracking frames produced by provenance-aware implementations include this snapshot;
 *           pre-start, error, and legacy frames may omit it and fail closed in capability matching.
 */
public data class FaceTrackingData(
    val blendShapes: BlendShapeData,
    val headTransform: HeadTransform,
    val gazeData: GazeData? = null,
    val landmarks: List<FaceLandmark> = emptyList(),
    val sourceImageWidth: Int = 0,
    val sourceImageHeight: Int = 0,
    val timestampMs: Long,
    val isTracking: Boolean,
    val faces: List<TrackedFace> = emptyList(),
    val thermalState: ThermalState = ThermalState.NOMINAL,
    val contextEpoch: TrackingContextEpoch? = null,
) {
    public companion object {
        /**
         * Returns a "no face detected" data instance.
         */
        public fun notTracking(timestampMs: Long = 0L): FaceTrackingData = FaceTrackingData(
            blendShapes = emptyBlendShapeData(),
            headTransform = HeadTransform(),
            landmarks = emptyList(),
            timestampMs = timestampMs,
            isTracking = false,
            faces = emptyList(),
        )
    }
}
