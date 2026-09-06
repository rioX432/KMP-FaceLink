package io.github.kmpfacelink.internal.onnx

import io.github.kmpfacelink.model.FaceTrackerConfig
import io.github.kmpfacelink.model.SmoothingConfig
import io.github.kmpfacelink.model.TrackerOperationStatus
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The desktop tracker implements neither smoothing nor calibration.
 *
 * Before these operations returned a status they returned `Unit`, so a caller could set a smoothing
 * config here, get a normal return, and reasonably believe it had taken effect. These tests pin the
 * answer the caller now gets instead.
 */
class OnnxTrackerOperationStatusTest {

    private fun tracker() = OnnxFaceTracker(FaceTrackerConfig())

    @Test
    fun updateSmoothingReportsUnsupported() {
        assertEquals(
            TrackerOperationStatus.UNSUPPORTED,
            tracker().updateSmoothing(SmoothingConfig.Ema()),
        )
    }

    @Test
    fun resetSmoothingReportsUnsupported() {
        assertEquals(TrackerOperationStatus.UNSUPPORTED, tracker().resetSmoothing())
    }

    @Test
    fun resetCalibrationReportsUnsupported() {
        assertEquals(TrackerOperationStatus.UNSUPPORTED, tracker().resetCalibration())
    }

    @Test
    fun anUnsupportedOperationStillRejectsUseAfterRelease() {
        // Not implementing an operation and being used after release are different failures, and
        // the lifecycle one stays an exception rather than becoming a status.
        val tracker = tracker()
        tracker.release()
        val outcome = runCatching { tracker.resetSmoothing() }
        assertEquals(true, outcome.isFailure, "a released tracker must reject the call outright")
    }
}
