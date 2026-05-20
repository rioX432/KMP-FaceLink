package io.github.kmpfacelink

import io.github.kmpfacelink.api.PlatformContext
import io.github.kmpfacelink.api.TrackingState
import io.github.kmpfacelink.api.createBodyTracker
import io.github.kmpfacelink.api.createFaceTracker
import io.github.kmpfacelink.api.createHandTracker
import io.github.kmpfacelink.api.createHolisticTracker
import io.github.kmpfacelink.model.HolisticTrackerConfig
import io.github.kmpfacelink.model.SmoothingConfig
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/**
 * Tests for iOS tracker lifecycle state transitions.
 *
 * These tests exercise release and post-release behavior on real iOS
 * tracker instances without requiring camera or ARKit hardware.
 */
class IosTrackerLifecycleTest {

    private val context = PlatformContext()

    // -- FaceTracker lifecycle --

    @Test
    fun faceTrackerReleaseTransitionsToReleased() {
        val tracker = createFaceTracker(context)
        tracker.release()
        assertEquals(TrackingState.RELEASED, tracker.state.value)
    }

    @Test
    fun faceTrackerDoubleReleaseDoesNotCrash() {
        val tracker = createFaceTracker(context)
        tracker.release()
        tracker.release() // Second release should not throw
    }

    @Test
    fun faceTrackerResetCalibrationAfterReleaseThrows() {
        val tracker = createFaceTracker(context)
        tracker.release()
        assertFailsWith<IllegalStateException> {
            tracker.resetCalibration()
        }
    }

    @Test
    fun faceTrackerUpdateSmoothingAfterReleaseThrows() {
        val tracker = createFaceTracker(context)
        tracker.release()
        assertFailsWith<IllegalStateException> {
            tracker.updateSmoothing(SmoothingConfig.None)
        }
    }

    @Test
    fun faceTrackerResetSmoothingAfterReleaseThrows() {
        val tracker = createFaceTracker(context)
        tracker.release()
        assertFailsWith<IllegalStateException> {
            tracker.resetSmoothing()
        }
    }

    // -- HandTracker lifecycle --

    @Test
    fun handTrackerReleaseTransitionsToReleased() {
        val tracker = createHandTracker(context)
        tracker.release()
        assertEquals(TrackingState.RELEASED, tracker.state.value)
    }

    @Test
    fun handTrackerDoubleReleaseDoesNotCrash() {
        val tracker = createHandTracker(context)
        tracker.release()
        tracker.release()
    }

    @Test
    fun handTrackerUpdateSmoothingAfterReleaseThrows() {
        val tracker = createHandTracker(context)
        tracker.release()
        assertFailsWith<IllegalStateException> {
            tracker.updateSmoothing(SmoothingConfig.None)
        }
    }

    @Test
    fun handTrackerResetSmoothingAfterReleaseThrows() {
        val tracker = createHandTracker(context)
        tracker.release()
        assertFailsWith<IllegalStateException> {
            tracker.resetSmoothing()
        }
    }

    // -- BodyTracker lifecycle --

    @Test
    fun bodyTrackerReleaseTransitionsToReleased() {
        val tracker = createBodyTracker(context)
        tracker.release()
        assertEquals(TrackingState.RELEASED, tracker.state.value)
    }

    @Test
    fun bodyTrackerDoubleReleaseDoesNotCrash() {
        val tracker = createBodyTracker(context)
        tracker.release()
        tracker.release()
    }

    @Test
    fun bodyTrackerUpdateSmoothingAfterReleaseThrows() {
        val tracker = createBodyTracker(context)
        tracker.release()
        assertFailsWith<IllegalStateException> {
            tracker.updateSmoothing(SmoothingConfig.None)
        }
    }

    // -- HolisticTracker lifecycle --

    @Test
    fun holisticTrackerReleaseTransitionsToReleased() {
        val tracker = createHolisticTracker(context)
        tracker.release()
        assertEquals(TrackingState.RELEASED, tracker.state.value)
    }

    @Test
    fun holisticTrackerDoubleReleaseDoesNotCrash() {
        val tracker = createHolisticTracker(context)
        tracker.release()
        tracker.release()
    }

    @Test
    fun holisticTrackerHandOnlyReleaseWorks() {
        val config = HolisticTrackerConfig(
            enableFace = false,
            enableHand = true,
            enableBody = false,
        )
        val tracker = createHolisticTracker(context, config)
        tracker.release()
        assertEquals(TrackingState.RELEASED, tracker.state.value)
    }
}
