package io.github.kmpfacelink

import io.github.kmpfacelink.api.BodyTracker
import io.github.kmpfacelink.api.TrackingState
import io.github.kmpfacelink.model.BodyTrackingData
import io.github.kmpfacelink.model.CameraFacing
import io.github.kmpfacelink.model.SmoothingConfig
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
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Contract tests for the [BodyTracker] interface.
 */
class BodyTrackerContractTest {

    private fun createTracker(): FakeBodyTracker = FakeBodyTracker()

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
    fun trackingDataEmitsFrames() = runTest {
        val tracker = createTracker()
        tracker.start()
        val frame = BodyTrackingData(
            bodies = emptyList(),
            timestampMs = 200L,
            isTracking = true,
        )
        tracker.emitFrame(frame)
        val received = tracker.trackingData.first()
        assertTrue(received.isTracking)
        assertEquals(200L, received.timestampMs)
    }

    @Test
    fun switchCameraAfterReleaseThrows() = runTest {
        val tracker = createTracker()
        tracker.release()
        assertFailsWith<IllegalStateException> {
            tracker.switchCamera(CameraFacing.BACK)
        }
    }
}

private class FakeBodyTracker : BodyTracker {
    private val _state = MutableStateFlow(TrackingState.IDLE)
    private val _errorMessage = MutableStateFlow<String?>(null)
    private val _trackingData = MutableSharedFlow<BodyTrackingData>(replay = 1)
    private var released = false

    override val trackingData: Flow<BodyTrackingData> = _trackingData
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

    override fun updateSmoothing(config: SmoothingConfig) {
        checkNotReleased()
    }

    override fun resetSmoothing() {
        checkNotReleased()
    }

    override suspend fun switchCamera(facing: CameraFacing) {
        checkNotReleased()
    }

    fun emitFrame(data: BodyTrackingData) {
        _trackingData.tryEmit(data)
    }

    private fun checkNotReleased() {
        check(!released) { "Tracker has been released" }
    }
}
