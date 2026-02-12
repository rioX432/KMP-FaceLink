package io.github.kmpfacelink.model

/**
 * Complete hand tracking data for a single frame.
 *
 * @property hands List of tracked hands (up to [HandTrackerConfig.maxHands])
 * @property sourceImageWidth Width of the source image in pixels (after rotation)
 * @property sourceImageHeight Height of the source image in pixels (after rotation)
 * @property timestampMs Frame timestamp in milliseconds
 * @property isTracking Whether any hands are currently being tracked
 */
public data class HandTrackingData(
    val hands: List<TrackedHand>,
    val sourceImageWidth: Int = 0,
    val sourceImageHeight: Int = 0,
    val timestampMs: Long,
    val isTracking: Boolean,
) {
    public companion object {
        /**
         * Returns a "no hands detected" data instance.
         */
        public fun notTracking(timestampMs: Long = 0L): HandTrackingData = HandTrackingData(
            hands = emptyList(),
            timestampMs = timestampMs,
            isTracking = false,
        )
    }
}
