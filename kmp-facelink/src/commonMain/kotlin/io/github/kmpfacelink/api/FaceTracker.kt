package io.github.kmpfacelink.api

import io.github.kmpfacelink.model.FaceTrackingData
import io.github.kmpfacelink.model.SmoothingConfig
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/**
 * Unified face tracking interface.
 *
 * Platform implementations:
 * - Android: MediaPipe Face Landmarker + CameraX
 * - iOS: ARKit ARFaceTrackingConfiguration
 *
 * Usage:
 * ```kotlin
 * val tracker = FaceTracker.create(config)
 * tracker.start()
 * tracker.trackingData.collect { data ->
 *     // use data.blendShapes, data.headTransform
 * }
 * tracker.stop()
 * ```
 */
public interface FaceTracker {

    /**
     * Stream of face tracking data, emitted for each processed frame.
     * Emits [FaceTrackingData.notTracking] when no face is detected.
     */
    public val trackingData: Flow<FaceTrackingData>

    /**
     * Current tracking state.
     */
    public val state: StateFlow<TrackingState>

    /**
     * Start face tracking (camera + processing pipeline).
     * No-op if already started.
     */
    public suspend fun start()

    /**
     * Stop face tracking and release resources.
     * Can be restarted with [start].
     */
    public suspend fun stop()

    /**
     * Release all resources. The tracker cannot be used after this.
     */
    public fun release()

    /**
     * Reset calibration data to defaults.
     */
    public fun resetCalibration()

    /**
     * Change the smoothing filter at runtime.
     *
     * @param config The new smoothing configuration to apply
     */
    public fun updateSmoothing(config: SmoothingConfig)
}

/**
 * Face tracker lifecycle state.
 */
public enum class TrackingState {
    /** Tracker created but not started */
    IDLE,

    /** Starting camera and processing pipeline */
    STARTING,

    /** Actively tracking */
    TRACKING,

    /** Stopped (can be restarted) */
    STOPPED,

    /** Unrecoverable error occurred */
    ERROR,
}
