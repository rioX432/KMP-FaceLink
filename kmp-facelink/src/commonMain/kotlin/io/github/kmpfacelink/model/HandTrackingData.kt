package io.github.kmpfacelink.model

/**
 * Complete hand tracking data for a single frame.
 *
 * @property hands List of tracked hands (up to [HandTrackerConfig.maxHands])
 * @property sourceImageWidth Width of the source image in pixels (after rotation)
 * @property sourceImageHeight Height of the source image in pixels (after rotation)
 * @property timestampMs Frame timestamp in milliseconds
 * @property isTracking Whether any hands are currently being tracked
 * @property thermalState Current device thermal state at the time of this frame.
 * @property activeHandPoseRevision The hand pose model revision that produced this frame's data.
 *           On iOS, this reflects the Vision framework revision (V1 for the original model,
 *           V2 for the iOS 26+ improved model). On Android (MediaPipe), this is always [HandPoseRevision.V1].
 *           Useful for consumers that need to adjust gesture classification thresholds
 *           based on which model produced the landmarks.
 */
public data class HandTrackingData(
    val hands: List<TrackedHand>,
    val sourceImageWidth: Int = 0,
    val sourceImageHeight: Int = 0,
    val timestampMs: Long,
    val isTracking: Boolean,
    val thermalState: ThermalState = ThermalState.NOMINAL,
    val activeHandPoseRevision: HandPoseRevision = HandPoseRevision.V1,
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
