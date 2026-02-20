package io.github.kmpfacelink.model

/**
 * Complete body tracking data for a single frame.
 *
 * @property bodies List of tracked bodies (up to [BodyTrackerConfig.maxBodies])
 * @property sourceImageWidth Width of the source image in pixels (after rotation)
 * @property sourceImageHeight Height of the source image in pixels (after rotation)
 * @property timestampMs Frame timestamp in milliseconds
 * @property isTracking Whether any bodies are currently being tracked
 */
public data class BodyTrackingData(
    val bodies: List<TrackedBody>,
    val sourceImageWidth: Int = 0,
    val sourceImageHeight: Int = 0,
    val timestampMs: Long,
    val isTracking: Boolean,
) {
    public companion object {
        /**
         * Returns a "no bodies detected" data instance.
         */
        public fun notTracking(timestampMs: Long = 0L): BodyTrackingData = BodyTrackingData(
            bodies = emptyList(),
            timestampMs = timestampMs,
            isTracking = false,
        )
    }
}
