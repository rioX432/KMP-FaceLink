package io.github.kmpfacelink.model

/**
 * Complete face tracking data for a single frame.
 *
 * @property blendShapes Map of 52 ARKit-compatible blend shape values (0.0–1.0)
 * @property headTransform 6DoF head position and rotation
 * @property timestampMs Frame timestamp in milliseconds (monotonic clock)
 * @property isTracking Whether a face is currently being tracked
 */
public data class FaceTrackingData(
    val blendShapes: BlendShapeData,
    val headTransform: HeadTransform,
    val timestampMs: Long,
    val isTracking: Boolean,
) {
    public companion object {
        /**
         * Returns a "no face detected" data instance.
         */
        public fun notTracking(timestampMs: Long = 0L): FaceTrackingData = FaceTrackingData(
            blendShapes = emptyBlendShapeData(),
            headTransform = HeadTransform(),
            timestampMs = timestampMs,
            isTracking = false,
        )
    }
}
