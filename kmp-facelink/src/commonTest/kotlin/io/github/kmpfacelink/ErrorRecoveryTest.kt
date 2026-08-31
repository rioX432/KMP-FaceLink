package io.github.kmpfacelink

import io.github.kmpfacelink.api.FaceTracker
import io.github.kmpfacelink.api.TrackingState
import io.github.kmpfacelink.model.CameraFacing
import io.github.kmpfacelink.model.FaceTrackingData
import io.github.kmpfacelink.model.SmoothingConfig
import io.github.kmpfacelink.model.TrackerOperationStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Tests for error state transitions and recovery behavior.
 *
 * Uses an error-capable fake tracker to verify that the documented
 * state machine (see [TrackingState]) handles errors correctly.
 */
class ErrorRecoveryTest {

    private fun createTracker(): ErrorCapableFakeTracker = ErrorCapableFakeTracker()

    @Test
    fun errorStateIsReachable() = runTest {
        val tracker = createTracker()
        tracker.start()
        tracker.simulateError("Camera disconnected")
        assertEquals(TrackingState.ERROR, tracker.state.value)
    }

    @Test
    fun errorMessageIsPopulated() = runTest {
        val tracker = createTracker()
        tracker.start()
        tracker.simulateError("Camera disconnected")
        assertEquals("Camera disconnected", tracker.errorMessage.value)
    }

    @Test
    fun recoveryFromErrorToTracking() = runTest {
        val tracker = createTracker()
        tracker.start()
        tracker.simulateError("Temporary failure")
        assertEquals(TrackingState.ERROR, tracker.state.value)

        // Recovery: start() from ERROR should transition back to TRACKING
        tracker.start()
        assertEquals(TrackingState.TRACKING, tracker.state.value)
    }

    @Test
    fun errorToStopTransition() = runTest {
        val tracker = createTracker()
        tracker.start()
        tracker.simulateError("Camera error")
        assertEquals(TrackingState.ERROR, tracker.state.value)

        tracker.stop()
        assertEquals(TrackingState.STOPPED, tracker.state.value)
    }

    @Test
    fun errorToReleaseTransition() = runTest {
        val tracker = createTracker()
        tracker.start()
        tracker.simulateError("Fatal error")
        assertEquals(TrackingState.ERROR, tracker.state.value)

        tracker.release()
        assertEquals(TrackingState.RELEASED, tracker.state.value)
    }

    @Test
    fun doubleErrorDoesNotCrash() = runTest {
        val tracker = createTracker()
        tracker.start()
        tracker.simulateError("First error")
        assertEquals(TrackingState.ERROR, tracker.state.value)

        // Second error while already in ERROR should not throw
        tracker.simulateError("Second error")
        assertEquals(TrackingState.ERROR, tracker.state.value)
        assertEquals("Second error", tracker.errorMessage.value)
    }

    @Test
    fun errorDuringStartingState() {
        val tracker = createTracker()
        // Tracker in IDLE, simulate start failure
        tracker.simulateError("Model loading failed")
        assertEquals(TrackingState.ERROR, tracker.state.value)
        assertEquals("Model loading failed", tracker.errorMessage.value)
    }

    @Test
    fun errorMessageRetainedAfterRecovery() = runTest {
        val tracker = createTracker()
        tracker.start()
        tracker.simulateError("Temporary glitch")

        // Per documentation: errorMessage is NOT automatically cleared on start()
        tracker.start()
        assertEquals(TrackingState.TRACKING, tracker.state.value)
        // The error message from the previous error should still be accessible
        assertEquals("Temporary glitch", tracker.errorMessage.value)
    }

    @Test
    fun errorMessageInitiallyNull() {
        val tracker = createTracker()
        assertNull(tracker.errorMessage.value)
    }

    @Test
    fun multipleRecoveryCycles() = runTest {
        val tracker = createTracker()

        // Cycle 1: start -> error -> recover
        tracker.start()
        tracker.simulateError("Error 1")
        assertEquals(TrackingState.ERROR, tracker.state.value)
        tracker.start()
        assertEquals(TrackingState.TRACKING, tracker.state.value)

        // Cycle 2: error -> recover
        tracker.simulateError("Error 2")
        assertEquals(TrackingState.ERROR, tracker.state.value)
        tracker.start()
        assertEquals(TrackingState.TRACKING, tracker.state.value)

        // Verify final state is stable
        assertTrue(tracker.state.value == TrackingState.TRACKING)
    }
}

/**
 * Fake [FaceTracker] that can simulate error states for testing recovery paths.
 *
 * Unlike the basic FakeFaceTracker in contract tests, this implementation supports:
 * - Transitioning to ERROR state via [simulateError]
 * - Recovery from ERROR via [start]
 * - Error message persistence (matches documented behavior)
 */
internal class ErrorCapableFakeTracker : FaceTracker {
    private val _state = MutableStateFlow(TrackingState.IDLE)
    private val _errorMessage = MutableStateFlow<String?>(null)
    private val _trackingData = MutableSharedFlow<FaceTrackingData>(replay = 1)
    private var released = false

    override val trackingData: Flow<FaceTrackingData> = _trackingData
    override val state: StateFlow<TrackingState> = _state.asStateFlow()
    override val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    override suspend fun start() {
        checkNotReleased()
        if (_state.value == TrackingState.TRACKING) return
        _state.value = TrackingState.TRACKING
    }

    override suspend fun stop() {
        checkNotReleased()
        _state.value = TrackingState.STOPPED
    }

    override fun release() {
        released = true
        _state.value = TrackingState.RELEASED
    }

    override fun resetCalibration(): TrackerOperationStatus {
        checkNotReleased()
        return TrackerOperationStatus.APPLIED
    }

    override fun resetSmoothing(): TrackerOperationStatus {
        checkNotReleased()
        return TrackerOperationStatus.APPLIED
    }

    override fun updateSmoothing(config: SmoothingConfig): TrackerOperationStatus {
        checkNotReleased()
        return TrackerOperationStatus.APPLIED
    }

    override suspend fun switchCamera(facing: CameraFacing) {
        checkNotReleased()
    }

    /**
     * Simulate a runtime error, transitioning to ERROR state.
     */
    fun simulateError(message: String) {
        _errorMessage.value = message
        _state.value = TrackingState.ERROR
    }

    private fun checkNotReleased() {
        check(!released) { "Tracker has been released" }
    }
}
