package io.github.kmpfacelink.actions.record

import io.github.kmpfacelink.model.FaceTrackingData
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Records face tracking data frames into a [TrackingSession].
 *
 * Thread-safe: multiple coroutines can call [record] concurrently.
 *
 * Usage:
 * ```
 * val recorder = TrackingRecorder()
 * recorder.start()
 * // Feed frames...
 * recorder.record(data)
 * // Or use flow extension:
 * faceTracker.trackingData.record(recorder).collect { ... }
 * // Stop and get session
 * val session = recorder.stop()
 * ```
 *
 * @param maxFrames Maximum number of frames to record. 0 = unlimited.
 */
public class TrackingRecorder(
    private val maxFrames: Int = 0,
) {
    private val mutex = Mutex()
    private val frames = mutableListOf<FaceTrackingData>()
    private var recording = false

    /** Whether the recorder is currently recording. */
    public val isRecording: Boolean get() = recording

    /** Number of frames recorded so far. */
    public val currentFrameCount: Int get() = frames.size

    /**
     * Start recording. Clears any previously recorded frames.
     */
    public suspend fun start() {
        mutex.withLock {
            frames.clear()
            recording = true
        }
    }

    /**
     * Record a single frame. Only records if [isRecording] is true.
     * Skips frames where [FaceTrackingData.isTracking] is false.
     */
    public suspend fun record(data: FaceTrackingData) {
        if (!recording || !data.isTracking) return
        mutex.withLock {
            if (!recording) return
            if (maxFrames > 0 && frames.size >= maxFrames) return
            frames.add(data)
        }
    }

    /**
     * Stop recording and return the recorded session.
     */
    public suspend fun stop(): TrackingSession {
        mutex.withLock {
            recording = false
            return TrackingSession(frames.toList())
        }
    }

    /**
     * Discard all recorded frames without stopping.
     */
    public suspend fun clear() {
        mutex.withLock {
            frames.clear()
        }
    }
}

/**
 * Records each [FaceTrackingData] frame into the [TrackingRecorder] as a side-effect.
 * The data flows through unchanged.
 */
public fun Flow<FaceTrackingData>.record(
    recorder: TrackingRecorder,
): Flow<FaceTrackingData> = onEach { recorder.record(it) }
