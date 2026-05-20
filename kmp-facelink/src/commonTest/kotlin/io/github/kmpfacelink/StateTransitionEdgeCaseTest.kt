package io.github.kmpfacelink

import io.github.kmpfacelink.api.TrackingState
import io.github.kmpfacelink.model.CameraFacing
import io.github.kmpfacelink.model.SmoothingConfig
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/**
 * Tests for invalid, unexpected, and rapid state transitions.
 *
 * Verifies that the tracker's state machine handles edge cases gracefully
 * without crashing, per the [TrackingState] contract.
 */
class StateTransitionEdgeCaseTest {

    private fun createTracker(): ErrorCapableFakeTracker = ErrorCapableFakeTracker()

    // -- stop() from unexpected states --

    @Test
    fun stopWhenIdleDoesNotCrash() = runTest {
        val tracker = createTracker()
        assertEquals(TrackingState.IDLE, tracker.state.value)
        // stop() before start() should not crash
        tracker.stop()
        assertEquals(TrackingState.STOPPED, tracker.state.value)
    }

    @Test
    fun stopWhenAlreadyStoppedIsIdempotent() = runTest {
        val tracker = createTracker()
        tracker.start()
        tracker.stop()
        assertEquals(TrackingState.STOPPED, tracker.state.value)
        // Second stop should not crash
        tracker.stop()
        assertEquals(TrackingState.STOPPED, tracker.state.value)
    }

    // -- start() idempotency --

    @Test
    fun startWhenAlreadyTrackingIsNoOp() = runTest {
        val tracker = createTracker()
        tracker.start()
        assertEquals(TrackingState.TRACKING, tracker.state.value)
        // Second start should be a no-op
        tracker.start()
        assertEquals(TrackingState.TRACKING, tracker.state.value)
    }

    // -- Double start safety --

    @Test
    fun doubleStartThenStopSequence() = runTest {
        val tracker = createTracker()
        tracker.start()
        tracker.start()
        tracker.stop()
        assertEquals(TrackingState.STOPPED, tracker.state.value)
    }

    // -- release() from every state --

    @Test
    fun releaseFromIdle() {
        val tracker = createTracker()
        assertEquals(TrackingState.IDLE, tracker.state.value)
        tracker.release()
        assertEquals(TrackingState.RELEASED, tracker.state.value)
    }

    @Test
    fun releaseFromTracking() = runTest {
        val tracker = createTracker()
        tracker.start()
        assertEquals(TrackingState.TRACKING, tracker.state.value)
        tracker.release()
        assertEquals(TrackingState.RELEASED, tracker.state.value)
    }

    @Test
    fun releaseFromStopped() = runTest {
        val tracker = createTracker()
        tracker.start()
        tracker.stop()
        assertEquals(TrackingState.STOPPED, tracker.state.value)
        tracker.release()
        assertEquals(TrackingState.RELEASED, tracker.state.value)
    }

    @Test
    fun releaseFromError() = runTest {
        val tracker = createTracker()
        tracker.start()
        tracker.simulateError("Some error")
        assertEquals(TrackingState.ERROR, tracker.state.value)
        tracker.release()
        assertEquals(TrackingState.RELEASED, tracker.state.value)
    }

    // -- Operations after RELEASED throw IllegalStateException --

    @Test
    fun startAfterReleaseThrows() = runTest {
        val tracker = createTracker()
        tracker.release()
        assertFailsWith<IllegalStateException> {
            tracker.start()
        }
    }

    @Test
    fun stopAfterReleaseThrows() = runTest {
        val tracker = createTracker()
        tracker.release()
        assertFailsWith<IllegalStateException> {
            tracker.stop()
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
    fun resetSmoothingAfterReleaseThrows() {
        val tracker = createTracker()
        tracker.release()
        assertFailsWith<IllegalStateException> {
            tracker.resetSmoothing()
        }
    }

    @Test
    fun switchCameraAfterReleaseThrows() = runTest {
        val tracker = createTracker()
        tracker.release()
        assertFailsWith<IllegalStateException> {
            tracker.switchCamera(CameraFacing.BACK)
        }
    }

    // -- Rapid state changes --

    @Test
    fun rapidStartStopCycles() = runTest {
        val tracker = createTracker()
        repeat(RAPID_CYCLE_COUNT) {
            tracker.start()
            assertEquals(TrackingState.TRACKING, tracker.state.value)
            tracker.stop()
            assertEquals(TrackingState.STOPPED, tracker.state.value)
        }
        // Should still be usable after rapid cycles
        tracker.start()
        assertEquals(TrackingState.TRACKING, tracker.state.value)
    }

    @Test
    fun rapidStartStopWithErrorRecovery() = runTest {
        val tracker = createTracker()
        repeat(RAPID_CYCLE_COUNT) {
            tracker.start()
            tracker.simulateError("Error #$it")
            assertEquals(TrackingState.ERROR, tracker.state.value)
        }
        // Should still recover after repeated errors
        tracker.start()
        assertEquals(TrackingState.TRACKING, tracker.state.value)
    }

    companion object {
        private const val RAPID_CYCLE_COUNT = 10
    }
}
