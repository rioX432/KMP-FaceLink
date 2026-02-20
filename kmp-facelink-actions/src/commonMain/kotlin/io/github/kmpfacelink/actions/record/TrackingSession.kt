package io.github.kmpfacelink.actions.record

import io.github.kmpfacelink.model.FaceTrackingData

/**
 * A recorded session of face tracking data.
 *
 * @property frames The recorded frames in chronological order.
 * @property durationMs Total duration of the recording in milliseconds.
 * @property frameCount Number of recorded frames.
 * @property startTimestampMs Timestamp of the first frame.
 * @property endTimestampMs Timestamp of the last frame.
 */
public data class TrackingSession(
    val frames: List<FaceTrackingData>,
) {
    /** Total duration of the recording in milliseconds. */
    public val durationMs: Long
        get() = if (frames.size < 2) 0L else frames.last().timestampMs - frames.first().timestampMs

    /** Number of recorded frames. */
    public val frameCount: Int get() = frames.size

    /** Timestamp of the first frame, or 0 if empty. */
    public val startTimestampMs: Long get() = frames.firstOrNull()?.timestampMs ?: 0L

    /** Timestamp of the last frame, or 0 if empty. */
    public val endTimestampMs: Long get() = frames.lastOrNull()?.timestampMs ?: 0L

    /** Whether this session contains any frames. */
    public val isEmpty: Boolean get() = frames.isEmpty()

    /** Average frames per second, or 0 if duration is 0. */
    public val averageFps: Float
        get() {
            if (durationMs <= 0L || frames.size < 2) return 0f
            return (frames.size - 1).toFloat() / (durationMs.toFloat() / MILLIS_PER_SECOND)
        }

    private companion object {
        const val MILLIS_PER_SECOND = 1000f
    }
}
