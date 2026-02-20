package io.github.kmpfacelink.api

import io.github.kmpfacelink.model.HolisticTrackingData
import io.github.kmpfacelink.model.SmoothingConfig
import io.github.kmpfacelink.model.TrackingModality
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/**
 * Unified holistic tracking interface combining face, hand, and body tracking
 * in a single pipeline with a shared camera session.
 *
 * Platform implementations:
 * - Android: Composes MediaPipe Face/Hand/Body Landmarkers with a shared CameraX session
 * - iOS: Composes ARKit (face) + Vision framework (hand/body) with shared camera sessions
 *
 * ## Threading model
 *
 * - [trackingData] and [state] are safe to collect from any thread.
 * - [start] and [stop] are suspend functions intended to be called from the main thread.
 * - [updateSmoothing] and [release] are thread-safe and can be called from any thread.
 * - Calling any method after [release] throws [IllegalStateException].
 *
 * ## Modality configuration
 *
 * Enable or disable individual modalities via [io.github.kmpfacelink.model.HolisticTrackerConfig].
 * Disabled modalities emit null in [HolisticTrackingData] and consume no resources.
 *
 * Usage:
 * ```kotlin
 * val tracker = createHolisticTracker(platformContext, HolisticTrackerConfig(
 *     enableFace = true,
 *     enableHand = true,
 *     enableBody = false,
 * ))
 * tracker.start()
 * tracker.trackingData.collect { data ->
 *     data.face?.let { /* face blend shapes */ }
 *     data.hand?.let { /* hand landmarks */ }
 * }
 * tracker.stop()
 * ```
 */
public interface HolisticTracker : Releasable {

    /**
     * Stream of combined tracking data from all enabled modalities.
     * Emits whenever any enabled tracker produces a new frame.
     */
    public val trackingData: Flow<HolisticTrackingData>

    /**
     * Current tracking state.
     * Reports [TrackingState.TRACKING] when any enabled modality is actively tracking.
     */
    public val state: StateFlow<TrackingState>

    /**
     * Human-readable error message when [state] is [TrackingState.ERROR], null otherwise.
     */
    public val errorMessage: StateFlow<String?>

    /**
     * Set of modalities that are enabled in this tracker's configuration.
     */
    public val enabledModalities: Set<TrackingModality>

    /**
     * Start holistic tracking (shared camera session + all enabled processing pipelines).
     * No-op if already started.
     *
     * @throws IllegalStateException if called after [release]
     */
    public suspend fun start()

    /**
     * Stop holistic tracking. Can be restarted with [start].
     */
    public suspend fun stop()

    /**
     * Release all resources. The tracker cannot be used after this.
     */
    public override fun release()

    /**
     * Change the smoothing filter at runtime for all enabled modalities.
     *
     * @param config The new smoothing configuration to apply
     * @throws IllegalStateException if called after [release]
     */
    public fun updateSmoothing(config: SmoothingConfig)
}
