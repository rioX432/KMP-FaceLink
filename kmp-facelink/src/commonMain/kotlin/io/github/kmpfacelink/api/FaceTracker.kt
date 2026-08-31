package io.github.kmpfacelink.api

import io.github.kmpfacelink.model.CameraFacing
import io.github.kmpfacelink.model.FacePipelinePerformanceEvent
import io.github.kmpfacelink.model.FaceTrackingData
import io.github.kmpfacelink.model.SmoothingConfig
import io.github.kmpfacelink.model.TrackerOperationStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.emptyFlow

/**
 * Unified face tracking interface.
 *
 * Platform implementations:
 * - Android: MediaPipe Face Landmarker + CameraX
 * - iOS: ARKit ARFaceTrackingConfiguration
 *
 * ## Threading model
 *
 * - [trackingData] and [state] are safe to collect from any thread.
 * - [start] and [stop] are suspend functions intended to be called from the main thread.
 * - [updateSmoothing], [resetSmoothing], [resetCalibration], and [release] are thread-safe
 *   and can be called from any thread. Internally, the processing pipeline and mutation
 *   methods share a single lock to ensure data consistency.
 * - Calling any method after [release] throws [IllegalStateException].
 *
 * ## Related modules
 *
 * - **kmp-facelink-avatar**: Map [FaceTrackingData] to Live2D / VRM parameters via
 *   [io.github.kmpfacelink.avatar.AvatarParameterMapper].
 * - **kmp-facelink-actions**: Register gesture/expression triggers via
 *   [io.github.kmpfacelink.actions.ActionSystem].
 * - **kmp-facelink-pipeline**: Orchestrate voice, LLM, and avatar animation via
 *   [io.github.kmpfacelink.pipeline.AvatarPipeline].
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
public interface FaceTracker : Releasable {

    /**
     * Opt-in local evaluation events. Empty unless performance instrumentation was enabled in the
     * tracker configuration. iOS boundaries begin at ARKit callback entry; they are not ARKit
     * inference, camera, consumer, or render latency.
     */
    public val performanceEvents: Flow<FacePipelinePerformanceEvent>
        get() = emptyFlow()

    /**
     * Bounded snapshot for deterministic local aggregation after [stop]. Empty when instrumentation
     * is disabled. A new measurement tracker should reject a snapshot whose first sequence is not
     * one, because that means the configured buffer was too small for the run.
     */
    public fun performanceEventsSnapshot(): List<FacePipelinePerformanceEvent> = emptyList()

    /**
     * Stream of face tracking data, emitted for each processed frame.
     * Emits [FaceTrackingData.notTracking] when no face is detected.
     *
     * **Threading:** This Flow emits on a platform-specific background thread
     * (Android: CameraX executor thread, iOS: ARKit session delegate thread).
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
    public val trackingData: Flow<FaceTrackingData>

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
     * - iOS: ARKit session errors (`NSError.localizedDescription`), Vision framework failures,
     *   or unavailable ARKit face-tracking support.
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
     * Start face tracking (camera + processing pipeline).
     * No-op if already started.
     *
     * @throws IllegalStateException if called after [release]
     */
    public suspend fun start()

    /**
     * Stop face tracking and release the camera session.
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
     * native resources (ARKit session, buffers). Omitting this call causes
     * resource leaks across create/destroy cycles. See the
     * [iOS Lifecycle Management](https://github.com/AnotherBall/KMP-FaceLink/blob/master/docs/quick-start-ios.md#ios-lifecycle-management)
     * section for the full required sequence.
     */
    public override fun release()

    /**
     * Reset calibration data to defaults.
     *
     * This also resets the smoother state, since calibrated data feeds the smoother.
     * If you only want to reset the smoother without losing calibration baselines,
     * use [resetSmoothing] instead.
     *
     * Note: This method is specific to [FaceTracker] because only face tracking
     * uses calibration (baseline blend shape range estimation). [HandTracker] and
     * [BodyTracker] operate on raw landmark positions that do not require calibration.
     *
     * @throws IllegalStateException if called after [release]
     *
     * @return [TrackerOperationStatus.APPLIED] when this tracker performed the operation, or
     *   [TrackerOperationStatus.UNSUPPORTED] when it does not implement it.
     */
    public fun resetCalibration(): TrackerOperationStatus

    /**
     * Change the smoothing filter at runtime.
     *
     * @param config The new smoothing configuration to apply
     * @throws IllegalStateException if called after [release]
     *
     * @return [TrackerOperationStatus.APPLIED] when this tracker performed the operation, or
     *   [TrackerOperationStatus.UNSUPPORTED] when it does not implement it.
     */
    public fun updateSmoothing(config: SmoothingConfig): TrackerOperationStatus

    /**
     * Reset smoother internal state without changing the smoothing algorithm.
     *
     * Clears accumulated filter state (e.g. EMA history, One Euro filter state),
     * useful after tracking loss or scene change. Unlike [resetCalibration], this
     * does not affect calibration baselines.
     *
     * @throws IllegalStateException if called after [release]
     *
     * @return [TrackerOperationStatus.APPLIED] when this tracker performed the operation, or
     *   [TrackerOperationStatus.UNSUPPORTED] when it does not implement it.
     */
    public fun resetSmoothing(): TrackerOperationStatus

    /**
     * Switch to a different camera at runtime.
     *
     * If the tracker is currently tracking, it will stop, switch the camera, and restart.
     * If the tracker is stopped or idle, the new facing will be applied on the next [start].
     *
     * @param facing The camera direction to switch to
     * @throws UnsupportedOperationException if the requested camera is not available
     *         (e.g., this iOS ARKit face tracker uses the front camera only)
     * @throws IllegalStateException if called after [release]
     */
    public suspend fun switchCamera(facing: CameraFacing)
}

/**
 * Tracker lifecycle state.
 *
 * ## State transition diagram
 *
 * ```
 * IDLE ──start()──> STARTING ──success──> TRACKING
 *                       │                     │
 *                     failure               stop()
 *                       │                     │
 *                       v                     v
 *                     ERROR               STOPPED
 *                       │                     │
 *                     start()               start()
 *                       │                     │
 *                       v                     v
 *                   STARTING             STARTING
 *
 * Any state ──release()──> RELEASED (terminal)
 * ```
 *
 * ## Recovery rules
 *
 * - **ERROR**: Calling [FaceTracker.start] is allowed and transitions back to STARTING.
 *   Whether the retry succeeds depends on the error cause: temporary failures (camera
 *   in use by another app) may recover, while permanent failures (no camera hardware)
 *   will transition to ERROR again.
 * - **RELEASED**: Terminal state. All method calls throw [IllegalStateException].
 *   Create a new tracker instance to continue tracking.
 */
public enum class TrackingState {
    /** Tracker created but not yet started. Transitions to [STARTING] on [FaceTracker.start]. */
    IDLE,

    /** Camera and processing pipeline are initializing. Transitions to [TRACKING] on success or [ERROR] on failure. */
    STARTING,

    /** Actively tracking. Transitions to [STOPPED] on [FaceTracker.stop] or [ERROR] on runtime failure. */
    TRACKING,

    /** Stopped by the caller. Transitions to [STARTING] on [FaceTracker.start]. */
    STOPPED,

    /**
     * An error occurred during startup or tracking.
     * Calling [FaceTracker.start] is permitted and transitions back to [STARTING].
     * Check [FaceTracker.errorMessage] for a description of what went wrong.
     */
    ERROR,

    /** Resources released. Terminal state — the tracker cannot be reused. */
    RELEASED,
}
