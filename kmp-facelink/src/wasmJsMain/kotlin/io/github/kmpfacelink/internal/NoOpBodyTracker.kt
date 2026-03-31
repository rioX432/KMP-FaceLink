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
 * No-op body tracker stub for Wasm/JS.
 *
 * Browser-based body tracking will be implemented in a future phase.
 * This stub allows the core module to compile and publish a wasmJs artifact.
 */
internal class NoOpBodyTracker : BodyTracker {

    override val trackingData: Flow<BodyTrackingData> = emptyFlow()

    private val _state = MutableStateFlow(TrackingState.IDLE)
    override val state: StateFlow<TrackingState> = _state

    private val _errorMessage = MutableStateFlow<String?>(null)
    override val errorMessage: StateFlow<String?> = _errorMessage

    override suspend fun start() {
        _state.value = TrackingState.ERROR
        _errorMessage.value = "Wasm body tracking is not yet implemented"
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
