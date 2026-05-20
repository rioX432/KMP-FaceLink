package io.github.kmpfacelink

import io.github.kmpfacelink.api.PlatformContext
import io.github.kmpfacelink.api.TrackingState
import io.github.kmpfacelink.api.createBodyTracker
import io.github.kmpfacelink.api.createFaceTracker
import io.github.kmpfacelink.api.createHandTracker
import io.github.kmpfacelink.api.createHolisticTracker
import io.github.kmpfacelink.model.BodyTrackerConfig
import io.github.kmpfacelink.model.FaceTrackerConfig
import io.github.kmpfacelink.model.HandTrackerConfig
import io.github.kmpfacelink.model.HolisticTrackerConfig
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/**
 * Tests that iOS tracker factory functions create valid tracker instances.
 *
 * These tests verify object construction only. Camera/ARKit initialization
 * happens in [start], so these tests work on the iOS simulator without
 * hardware access.
 */
class IosTrackerFactoryTest {

    private val context = PlatformContext()

    @Test
    fun createFaceTrackerReturnsNonNull() {
        val tracker = createFaceTracker(context)
        assertNotNull(tracker)
        tracker.release()
    }

    @Test
    fun createFaceTrackerWithCustomConfig() {
        val config = FaceTrackerConfig(
            calibrationConfig = io.github.kmpfacelink.model.CalibrationConfig(enabled = true),
        )
        val tracker = createFaceTracker(context, config)
        assertNotNull(tracker)
        tracker.release()
    }

    @Test
    fun createHandTrackerReturnsNonNull() {
        val tracker = createHandTracker(context)
        assertNotNull(tracker)
        tracker.release()
    }

    @Test
    fun createHandTrackerWithCustomConfig() {
        val config = HandTrackerConfig(maxHands = 1)
        val tracker = createHandTracker(context, config)
        assertNotNull(tracker)
        tracker.release()
    }

    @Test
    fun createBodyTrackerReturnsNonNull() {
        val tracker = createBodyTracker(context)
        assertNotNull(tracker)
        tracker.release()
    }

    @Test
    fun createBodyTracker3DReturnsNonNull() {
        val config = BodyTrackerConfig(enable3D = true)
        val tracker = createBodyTracker(context, config)
        assertNotNull(tracker)
        tracker.release()
    }

    @Test
    fun createHolisticTrackerReturnsNonNull() {
        val tracker = createHolisticTracker(context)
        assertNotNull(tracker)
        tracker.release()
    }

    @Test
    fun createHolisticTrackerWithFaceOnlyConfig() {
        val config = HolisticTrackerConfig(
            enableFace = true,
            enableHand = false,
            enableBody = false,
        )
        val tracker = createHolisticTracker(context, config)
        assertNotNull(tracker)
        tracker.release()
    }

    @Test
    fun faceTrackerInitialStateIsIdle() {
        val tracker = createFaceTracker(context)
        assertEquals(TrackingState.IDLE, tracker.state.value)
        tracker.release()
    }

    @Test
    fun handTrackerInitialStateIsIdle() {
        val tracker = createHandTracker(context)
        assertEquals(TrackingState.IDLE, tracker.state.value)
        tracker.release()
    }

    @Test
    fun bodyTrackerInitialStateIsIdle() {
        val tracker = createBodyTracker(context)
        assertEquals(TrackingState.IDLE, tracker.state.value)
        tracker.release()
    }

    @Test
    fun holisticTrackerInitialStateIsIdle() {
        val tracker = createHolisticTracker(context)
        assertEquals(TrackingState.IDLE, tracker.state.value)
        tracker.release()
    }
}
