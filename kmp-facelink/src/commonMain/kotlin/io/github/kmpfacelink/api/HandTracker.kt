package io.github.kmpfacelink.api

import io.github.kmpfacelink.model.CameraFacing
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
     *
     * **Threading:** This Flow emits on a platform-specific background thread
     * (Android: CameraX executor thread, iOS: AVCaptureSession delegate thread).
     * Consumers **must** switch to the Main dispatcher before updating UI:
     *
     * ```kotlin
     * tracker.trackingData
     *     .flowOn(Dispatchers.Default) // process on background
     *     .collect { data ->
     *         withContext(Dispatchers.Main) {
     *             // update UI here
     *         }
     *     }
     * ```
     */
    public val trackingData: Flow<HandTrackingData>

    /**
     * Current tracking state.
     *
     * **Threading:** Emits on a background thread. Use `Dispatchers.Main` when
     * binding to UI components.
     */
    public val state: StateFlow<TrackingState>

    /**
     * Human-readable error message when [state] is [TrackingState.ERROR], null otherwise.
     *
     * **Threading:** Emits on a background thread. Use `Dispatchers.Main` when
     * binding to UI components.
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

    /**
     * Reset smoother internal state.
     *
     * Clears accumulated filter state, useful after tracking loss or scene change.
     *
     * @throws IllegalStateException if called after [release]
     */
    public fun resetSmoothing()

    /**
     * Switch to a different camera at runtime.
     *
     * If the tracker is currently tracking, it will stop, switch the camera, and restart.
     * If the tracker is stopped or idle, the new facing will be applied on the next [start].
     *
     * @param facing The camera direction to switch to
     * @throws UnsupportedOperationException if the requested camera is not available
     * @throws IllegalStateException if called after [release]
     */
    public suspend fun switchCamera(facing: CameraFacing)
}
