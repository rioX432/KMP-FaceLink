package io.github.kmpfacelink.internal

import io.github.kmpfacelink.api.HolisticTracker
import io.github.kmpfacelink.api.TrackingState
import io.github.kmpfacelink.model.CameraFacing
import io.github.kmpfacelink.model.HolisticTrackingData
import io.github.kmpfacelink.model.SmoothingConfig
import io.github.kmpfacelink.model.TrackerOperationStatus
import io.github.kmpfacelink.model.TrackingModality
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.emptyFlow

/**
 * No-op holistic tracker stub for non-mobile targets.
 *
 * Platform-specific holistic tracking will be implemented in a future phase.
 * This stub allows the module to compile and publish artifacts for JVM and Wasm.
 */
internal class NoOpHolisticTracker : HolisticTracker {

    override val trackingData: Flow<HolisticTrackingData> = emptyFlow()

    private val _state = MutableStateFlow(TrackingState.IDLE)
    override val state: StateFlow<TrackingState> = _state

    private val _errorMessage = MutableStateFlow<String?>(null)
    override val errorMessage: StateFlow<String?> = _errorMessage

    override val enabledModalities: Set<TrackingModality> = emptySet()

    override suspend fun start() {
        _state.value = TrackingState.ERROR
        _errorMessage.value = "Holistic tracking is not yet implemented on this platform"
    }

    override suspend fun stop() {
        _state.value = TrackingState.STOPPED
    }

    override fun release() {
        _state.value = TrackingState.RELEASED
    }

    override fun updateSmoothing(config: SmoothingConfig): TrackerOperationStatus {
        // No-op
        return TrackerOperationStatus.UNSUPPORTED
    }

    override fun resetSmoothing(): TrackerOperationStatus {
        // No-op
        return TrackerOperationStatus.UNSUPPORTED
    }

    override suspend fun switchCamera(facing: CameraFacing) {
        // No-op
    }
}
