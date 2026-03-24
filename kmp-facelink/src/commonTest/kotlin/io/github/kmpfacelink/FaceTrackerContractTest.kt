package io.github.kmpfacelink

import io.github.kmpfacelink.api.FaceTracker
import io.github.kmpfacelink.api.TrackingState
import io.github.kmpfacelink.model.CameraFacing
import io.github.kmpfacelink.model.FaceTrackingData
import io.github.kmpfacelink.model.HeadTransform
import io.github.kmpfacelink.model.SmoothingConfig
import io.github.kmpfacelink.model.emptyBlendShapeData
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
 * Contract tests for the [FaceTracker] interface.
 *
 * Verifies expected behavior using a fake implementation to ensure
 * that any conforming implementation obeys the documented contracts.
 */
class FaceTrackerContractTest {

    private fun createTracker(): FakeFaceTracker = FakeFaceTracker()

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
    fun startAfterStopRestartsTracking() = runTest {
        val tracker = createTracker()
        tracker.start()
        tracker.stop()
        tracker.start()
        assertEquals(TrackingState.TRACKING, tracker.state.value)
    }

    @Test
    fun releaseTransitionsToReleased() = runTest {
        val tracker = createTracker()
        tracker.start()
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
    fun resetCalibrationAfterReleaseThrows() {
        val tracker = createTracker()
        tracker.release()
        assertFailsWith<IllegalStateException> {
            tracker.resetCalibration()
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
    fun trackingDataEmitsFrames() = runTest {
        val tracker = createTracker()
        tracker.start()
        tracker.emitFrame(
            FaceTrackingData(
                blendShapes = emptyBlendShapeData(),
                headTransform = HeadTransform(),
                timestampMs = 100L,
                isTracking = true,
            ),
        )
        val frame = tracker.trackingData.first()
        assertTrue(frame.isTracking)
        assertEquals(100L, frame.timestampMs)
    }

    @Test
    fun startIsNoOpWhenAlreadyTracking() = runTest {
        val tracker = createTracker()
        tracker.start()
        assertEquals(1, tracker.startCount)
        tracker.start()
        // Second start should be a no-op
        assertEquals(1, tracker.startCount)
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

/**
 * Fake [FaceTracker] that follows the documented behavioral contracts.
 */
private class FakeFaceTracker : FaceTracker {
    private val _state = MutableStateFlow(TrackingState.IDLE)
    private val _errorMessage = MutableStateFlow<String?>(null)
    private val _trackingData = MutableSharedFlow<FaceTrackingData>(replay = 1)
    private var released = false

    var startCount = 0
        private set

    override val trackingData: Flow<FaceTrackingData> = _trackingData
    override val state: StateFlow<TrackingState> = _state.asStateFlow()
    override val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    override suspend fun start() {
        checkNotReleased()
        if (_state.value == TrackingState.TRACKING) return
        startCount++
        _state.value = TrackingState.TRACKING
    }

    override suspend fun stop() {
        _state.value = TrackingState.STOPPED
    }

    override fun release() {
        released = true
        _state.value = TrackingState.RELEASED
    }

    override fun resetCalibration() {
        checkNotReleased()
    }

    override fun updateSmoothing(config: SmoothingConfig) {
        checkNotReleased()
    }

    override suspend fun switchCamera(facing: CameraFacing) {
        checkNotReleased()
    }

    fun emitFrame(data: FaceTrackingData) {
        _trackingData.tryEmit(data)
    }

    private fun checkNotReleased() {
        check(!released) { "Tracker has been released" }
    }
}
