package io.github.kmpfacelink.internal

import io.github.kmpfacelink.api.TrackingState
import io.github.kmpfacelink.util.FrameThrottler
import io.github.kmpfacelink.util.PlatformLock
import io.github.kmpfacelink.util.ThermalMonitor
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.concurrent.atomics.AtomicInt
import kotlin.concurrent.atomics.ExperimentalAtomicApi

/**
 * Base class that holds the common infrastructure shared by all tracker implementations.
 *
 * Provides:
 * - Tracking data flow ([trackingData] / [mutableTrackingData])
 * - State flow ([state] / [mutableState])
 * - Error message flow ([errorMessage] / [mutableErrorMessage])
 * - Thread-safety primitives ([pipelineLock] / [released])
 * - Thermal monitoring ([thermalMonitor] / [frameThrottler])
 * - Common lifecycle guard helpers ([checkNotReleased] / [guardStart])
 *
 * @param T The tracking data type (e.g., FaceTrackingData, HandTrackingData, BodyTrackingData)
 */
@OptIn(ExperimentalAtomicApi::class)
internal abstract class BaseTracker<T>(
    internal val thermalMonitor: ThermalMonitor,
    internal val frameThrottler: FrameThrottler,
) {

    // -- Flows --

    protected val mutableTrackingData: MutableSharedFlow<T> = MutableSharedFlow(
        replay = 1,
        extraBufferCapacity = 1,
    )
    internal open val trackingData: Flow<T> = mutableTrackingData.asSharedFlow()

    protected val mutableState: MutableStateFlow<TrackingState> = MutableStateFlow(TrackingState.IDLE)
    internal open val state: StateFlow<TrackingState> = mutableState.asStateFlow()

    protected val mutableErrorMessage: MutableStateFlow<String?> = MutableStateFlow(null)
    internal open val errorMessage: StateFlow<String?> = mutableErrorMessage.asStateFlow()

    // -- Thread-safety primitives --

    internal val pipelineLock: PlatformLock = PlatformLock()
    internal val released: AtomicInt = AtomicInt(0)

    // -- Lifecycle guard helpers --

    /**
     * Checks that the tracker has not been released.
     *
     * @param action Description of the action being attempted, used in the error message
     * @throws IllegalStateException if the tracker has been released
     */
    protected fun checkNotReleased(action: String) {
        check(released.load() == 0) { "Cannot $action on a released tracker" }
    }

    /**
     * Guards the beginning of [start]: checks not released, returns false if already
     * started/starting, and transitions to [TrackingState.STARTING].
     *
     * Must be called inside [pipelineLock].
     *
     * @return true if [start] should proceed, false if it should return early (already started)
     */
    protected fun guardStart(): Boolean {
        checkNotReleased("start")
        if (mutableState.value == TrackingState.TRACKING ||
            mutableState.value == TrackingState.STARTING
        ) {
            return false
        }
        mutableState.value = TrackingState.STARTING
        return true
    }

    /**
     * Transitions to [TrackingState.TRACKING] if the tracker has not been released.
     *
     * Must be called inside [pipelineLock].
     */
    protected fun transitionToTracking() {
        if (released.load() == 0) {
            mutableState.value = TrackingState.TRACKING
        }
    }

    /**
     * Sets error state and error message. Called when [start] or other operations fail.
     */
    protected fun setError(message: String?) {
        mutableErrorMessage.value = message
        mutableState.value = TrackingState.ERROR
    }

    /**
     * Returns true if the tracker is currently released.
     */
    internal fun isReleased(): Boolean = released.load() != 0
}
