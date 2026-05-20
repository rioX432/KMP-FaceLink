package io.github.kmpfacelink.api

import io.github.kmpfacelink.model.CameraFacing
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
 * ## Threading model
 *
 * - [trackingData] and [state] are safe to collect from any thread.
 * - [start] and [stop] are suspend functions intended to be called from the main thread.
 * - [updateSmoothing], [resetCalibration], and [release] are thread-safe and can be called
 *   from any thread. Internally, the processing pipeline and mutation methods share a single
 *   lock to ensure data consistency.
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
     * Stop face tracking and release resources.
     * Can be restarted with [start].
     */
    public suspend fun stop()

    /**
     * Release all resources. The tracker cannot be used after this.
     */
    public override fun release()

    /**
     * Reset calibration data to defaults.
     *
     * @throws IllegalStateException if called after [release]
     */
    public fun resetCalibration()

    /**
     * Change the smoothing filter at runtime.
     *
     * @param config The new smoothing configuration to apply
     * @throws IllegalStateException if called after [release]
     */
    public fun updateSmoothing(config: SmoothingConfig)

    /**
     * Switch to a different camera at runtime.
     *
     * If the tracker is currently tracking, it will stop, switch the camera, and restart.
     * If the tracker is stopped or idle, the new facing will be applied on the next [start].
     *
     * @param facing The camera direction to switch to
     * @throws UnsupportedOperationException if the requested camera is not available
     *         (e.g., iOS ARKit face tracking only supports the front TrueDepth camera)
     * @throws IllegalStateException if called after [release]
     */
    public suspend fun switchCamera(facing: CameraFacing)
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

    /** Resources released — tracker cannot be used */
    RELEASED,
}
