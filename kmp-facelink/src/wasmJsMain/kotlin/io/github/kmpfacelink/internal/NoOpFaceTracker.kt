package io.github.kmpfacelink.internal

import io.github.kmpfacelink.api.FaceTracker
import io.github.kmpfacelink.api.TrackingState
import io.github.kmpfacelink.model.CameraFacing
import io.github.kmpfacelink.model.FaceTrackingData
import io.github.kmpfacelink.model.SmoothingConfig
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.emptyFlow

/**
 * No-op face tracker stub for Wasm/JS.
 *
 * Browser-based face tracking (e.g. via MediaPipe JS) will be implemented in a future phase.
 * This stub allows the core module to compile and publish a wasmJs artifact.
 */
internal class NoOpFaceTracker : FaceTracker {

    override val trackingData: Flow<FaceTrackingData> = emptyFlow()

    private val _state = MutableStateFlow(TrackingState.IDLE)
    override val state: StateFlow<TrackingState> = _state

    private val _errorMessage = MutableStateFlow<String?>(null)
    override val errorMessage: StateFlow<String?> = _errorMessage

    override suspend fun start() {
        _state.value = TrackingState.ERROR
        _errorMessage.value = "Wasm face tracking is not yet implemented"
    }

    override suspend fun stop() {
        _state.value = TrackingState.STOPPED
    }

    override fun release() {
        _state.value = TrackingState.RELEASED
    }

    override fun resetCalibration() {
        // No-op
    }

    override fun updateSmoothing(config: SmoothingConfig) {
        // No-op
    }

    override suspend fun switchCamera(facing: CameraFacing) {
        // No-op
    }
}
