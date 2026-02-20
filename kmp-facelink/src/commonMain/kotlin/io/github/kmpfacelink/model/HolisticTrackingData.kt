package io.github.kmpfacelink.model

/**
 * Combined tracking data from face, hand, and body trackers for a single point in time.
 *
 * Null fields indicate that the corresponding modality is either disabled in
 * [HolisticTrackerConfig] or that no subject was detected for that modality.
 *
 * @property face Face tracking data, or null if face tracking is disabled or no face detected
 * @property hand Hand tracking data, or null if hand tracking is disabled or no hands detected
 * @property body Body tracking data, or null if body tracking is disabled or no bodies detected
 * @property timestampMs Timestamp in milliseconds (monotonic clock)
 * @property activeModalities Set of modalities that are enabled and actively producing data
 */
public data class HolisticTrackingData(
    val face: FaceTrackingData?,
    val hand: HandTrackingData?,
    val body: BodyTrackingData?,
    val timestampMs: Long,
    val activeModalities: Set<TrackingModality>,
) {
    /** Whether any modality is actively tracking a subject. */
    val isTracking: Boolean
        get() = (face?.isTracking == true) ||
            (hand?.isTracking == true) ||
            (body?.isTracking == true)

    public companion object {
        /**
         * Returns a "nothing detected" data instance with the given active modalities.
         */
        public fun notTracking(
            activeModalities: Set<TrackingModality> = emptySet(),
            timestampMs: Long = 0L,
        ): HolisticTrackingData = HolisticTrackingData(
            face = null,
            hand = null,
            body = null,
            timestampMs = timestampMs,
            activeModalities = activeModalities,
        )
    }
}
