package io.github.kmpfacelink.api

import io.github.kmpfacelink.model.BodyTrackingData
import io.github.kmpfacelink.model.SmoothingConfig
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/**
 * Unified body tracking interface.
 *
 * Platform implementations:
 * - Android: MediaPipe Pose Landmarker + CameraX
 * - iOS: Apple Vision VNDetectHumanBodyPoseRequest + AVCaptureSession
 *
 * ## Threading model
 *
 * - [trackingData] and [state] are safe to collect from any thread.
 * - [start] and [stop] are suspend functions intended to be called from the main thread.
 * - [updateSmoothing] and [release] are thread-safe and can be called from any thread.
 * - Calling any method after [release] throws [IllegalStateException].
 */
public interface BodyTracker : Releasable {

    /**
     * Stream of body tracking data, emitted for each processed frame.
     * Emits [BodyTrackingData.notTracking] when no bodies are detected.
     */
    public val trackingData: Flow<BodyTrackingData>

    /**
     * Current tracking state.
     */
    public val state: StateFlow<TrackingState>

    /**
     * Human-readable error message when [state] is [TrackingState.ERROR], null otherwise.
     */
    public val errorMessage: StateFlow<String?>

    /**
     * Start body tracking (camera + processing pipeline).
     * No-op if already started.
     *
     * @throws IllegalStateException if called after [release]
     */
    public suspend fun start()

    /**
     * Stop body tracking. Can be restarted with [start].
     */
    public suspend fun stop()

    /**
     * Release all resources. The tracker cannot be used after this.
     */
    public override fun release()

    /**
     * Change the smoothing filter at runtime.
     *
     * @param config The new smoothing configuration to apply
     * @throws IllegalStateException if called after [release]
     */
    public fun updateSmoothing(config: SmoothingConfig)
}
