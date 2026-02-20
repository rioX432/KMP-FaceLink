package io.github.kmpfacelink.api

import io.github.kmpfacelink.model.HandTrackingData
import io.github.kmpfacelink.model.SmoothingConfig
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/**
 * Unified hand tracking interface.
 *
 * Platform implementations:
 * - Android: MediaPipe Hand Landmarker + CameraX
 * - iOS: Apple Vision VNDetectHumanHandPoseRequest + AVCaptureSession
 *
 * ## Threading model
 *
 * - [trackingData] and [state] are safe to collect from any thread.
 * - [start] and [stop] are suspend functions intended to be called from the main thread.
 * - [updateSmoothing] and [release] are thread-safe and can be called from any thread.
 * - Calling any method after [release] throws [IllegalStateException].
 */
public interface HandTracker : Releasable {

    /**
     * Stream of hand tracking data, emitted for each processed frame.
     * Emits [HandTrackingData.notTracking] when no hands are detected.
     */
    public val trackingData: Flow<HandTrackingData>

    /**
     * Current tracking state.
     */
    public val state: StateFlow<TrackingState>

    /**
     * Human-readable error message when [state] is [TrackingState.ERROR], null otherwise.
     */
    public val errorMessage: StateFlow<String?>

    /**
     * Start hand tracking (camera + processing pipeline).
     * No-op if already started.
     *
     * @throws IllegalStateException if called after [release]
     */
    public suspend fun start()

    /**
     * Stop hand tracking. Can be restarted with [start].
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
