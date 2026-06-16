package io.github.kmpfacelink.api

import io.github.kmpfacelink.model.BodyTrackingData
import io.github.kmpfacelink.model.CameraFacing
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
 * - [updateSmoothing], [resetSmoothing], and [release] are thread-safe and can be called from any thread.
 * - Calling any method after [release] throws [IllegalStateException].
 */
public interface BodyTracker : Releasable {

    /**
     * Stream of body tracking data, emitted for each processed frame.
     * Emits [BodyTrackingData.notTracking] when no bodies are detected.
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
    public val trackingData: Flow<BodyTrackingData>

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
     * The message contains a platform-specific description of what went wrong:
     * - Android: MediaPipe model loading failures, CameraX binding errors, or missing camera hardware.
     * - iOS: Vision framework failures or AVCaptureSession configuration errors.
     *
     * This value is **not** automatically cleared when [start] is called again after an error.
     * Consumers should rely on [state] transitions (ERROR -> STARTING -> TRACKING) rather than
     * checking whether [errorMessage] is null.
     *
     * **Threading:** Emits on a background thread. Use `Dispatchers.Main` when
     * binding to UI components.
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
     * Stop body tracking and release the camera session.
     * Can be restarted with [start].
     *
     * **iOS lifecycle note:** On iOS, you **must** cancel any Swift `Task` that
     * collects [trackingData] or [state] **before** calling this method. Failing
     * to do so leaves an orphaned Task collecting from a stopped tracker and may
     * prevent the camera from being released. See the
     * [iOS Lifecycle Management](https://github.com/AnotherBall/KMP-FaceLink/blob/master/docs/quick-start-ios.md#ios-lifecycle-management)
     * section for the full required sequence.
     */
    public suspend fun stop()

    /**
     * Release all resources. The tracker cannot be used after this.
     *
     * **iOS lifecycle note:** Call this in your ViewModel's `deinit` to free
     * native resources (AVCaptureSession, buffers). Omitting this call causes
     * resource leaks across create/destroy cycles. See the
     * [iOS Lifecycle Management](https://github.com/AnotherBall/KMP-FaceLink/blob/master/docs/quick-start-ios.md#ios-lifecycle-management)
     * section for the full required sequence.
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
