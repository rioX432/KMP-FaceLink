package io.github.kmpfacelink.internal

import io.github.kmpfacelink.api.BodyTracker
import io.github.kmpfacelink.api.TrackingState
import io.github.kmpfacelink.model.BodyTrackingData
import io.github.kmpfacelink.model.CameraFacing
import io.github.kmpfacelink.model.SmoothingConfig
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.emptyFlow

/**
 * No-op body tracker stub for non-mobile targets.
 *
 * Platform-specific body tracking will be implemented in a future phase.
 * This stub allows the module to compile and publish artifacts for JVM and Wasm.
 */
internal class NoOpBodyTracker : BodyTracker {

    override val trackingData: Flow<BodyTrackingData> = emptyFlow()

    private val _state = MutableStateFlow(TrackingState.IDLE)
    override val state: StateFlow<TrackingState> = _state

    private val _errorMessage = MutableStateFlow<String?>(null)
    override val errorMessage: StateFlow<String?> = _errorMessage

    override suspend fun start() {
        _state.value = TrackingState.ERROR
        _errorMessage.value = "Body tracking is not yet implemented on this platform"
    }

    override suspend fun stop() {
        _state.value = TrackingState.STOPPED
    }

    override fun release() {
        _state.value = TrackingState.RELEASED
    }

    override fun updateSmoothing(config: SmoothingConfig) {
        // No-op
    }

    override fun resetSmoothing() {
        // No-op
    }

    override suspend fun switchCamera(facing: CameraFacing) {
        // No-op
    }
}
