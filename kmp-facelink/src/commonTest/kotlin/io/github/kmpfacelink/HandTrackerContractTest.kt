package io.github.kmpfacelink

import io.github.kmpfacelink.api.HandTracker
import io.github.kmpfacelink.api.TrackingState
import io.github.kmpfacelink.model.CameraFacing
import io.github.kmpfacelink.model.HandTrackingData
import io.github.kmpfacelink.model.SmoothingConfig
import io.github.kmpfacelink.model.TrackerOperationStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Contract tests for the [HandTracker] interface.
 */
class HandTrackerContractTest {

    private fun createTracker(): FakeHandTracker = FakeHandTracker()

    @Test
    fun initialStateIsIdle() {
        val tracker = createTracker()
        assertEquals(TrackingState.IDLE, tracker.state.value)
    }

    @Test
    fun initialErrorMessageIsNull() {
        val tracker = createTracker()
        assertNull(tracker.errorMessage.value)
    }

    @Test
    fun startTransitionsToTracking() = runTest {
        val tracker = createTracker()
        tracker.start()
        assertEquals(TrackingState.TRACKING, tracker.state.value)
    }

    @Test
    fun stopTransitionsToStopped() = runTest {
        val tracker = createTracker()
        tracker.start()
        tracker.stop()
        assertEquals(TrackingState.STOPPED, tracker.state.value)
    }

    @Test
    fun releaseTransitionsToReleased() {
        val tracker = createTracker()
        tracker.release()
        assertEquals(TrackingState.RELEASED, tracker.state.value)
    }

    @Test
    fun startAfterReleaseThrows() = runTest {
        val tracker = createTracker()
        tracker.release()
        assertFailsWith<IllegalStateException> {
            tracker.start()
        }
    }

    @Test
    fun updateSmoothingAfterReleaseThrows() {
        val tracker = createTracker()
        tracker.release()
        assertFailsWith<IllegalStateException> {
            tracker.updateSmoothing(SmoothingConfig.None)
        }
    }

    @Test
    fun resetSmoothingAfterReleaseThrows() {
        val tracker = createTracker()
        tracker.release()
        assertFailsWith<IllegalStateException> {
            tracker.resetSmoothing()
        }
    }

    @Test
    fun notTrackingDataHasNoHands() {
        val data = HandTrackingData.notTracking()
        assertFalse(data.isTracking)
        assertTrue(data.hands.isEmpty())
    }

    @Test
    fun trackingDataEmitsFrames() = runTest {
        val tracker = createTracker()
        tracker.start()
        val frame = HandTrackingData(
            hands = emptyList(),
            timestampMs = 300L,
            isTracking = false,
        )
        tracker.emitFrame(frame)
        val received = tracker.trackingData.first()
        assertEquals(300L, received.timestampMs)
        assertFalse(received.isTracking)
    }

    @Test
    fun switchCameraAfterReleaseThrows() = runTest {
        val tracker = createTracker()
        tracker.release()
        assertFailsWith<IllegalStateException> {
            tracker.switchCamera(CameraFacing.FRONT)
        }
    }
}

private class FakeHandTracker : HandTracker {
    private val _state = MutableStateFlow(TrackingState.IDLE)
    private val _errorMessage = MutableStateFlow<String?>(null)
    private val _trackingData = MutableSharedFlow<HandTrackingData>(replay = 1)
    private var released = false

    override val trackingData: Flow<HandTrackingData> = _trackingData
    override val state: StateFlow<TrackingState> = _state.asStateFlow()
    override val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    override suspend fun start() {
        checkNotReleased()
        _state.value = TrackingState.TRACKING
    }

    override suspend fun stop() {
        _state.value = TrackingState.STOPPED
    }

    override fun release() {
        released = true
        _state.value = TrackingState.RELEASED
    }

    override fun updateSmoothing(config: SmoothingConfig): TrackerOperationStatus {
        checkNotReleased()
        return TrackerOperationStatus.APPLIED
    }

    override fun resetSmoothing(): TrackerOperationStatus {
        checkNotReleased()
        return TrackerOperationStatus.APPLIED
    }

    override suspend fun switchCamera(facing: CameraFacing) {
        checkNotReleased()
    }

    fun emitFrame(data: HandTrackingData) {
        _trackingData.tryEmit(data)
    }

    private fun checkNotReleased() {
        check(!released) { "Tracker has been released" }
    }
}
