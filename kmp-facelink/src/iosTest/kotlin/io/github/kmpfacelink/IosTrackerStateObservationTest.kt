package io.github.kmpfacelink

import io.github.kmpfacelink.api.PlatformContext
import io.github.kmpfacelink.api.TrackingState
import io.github.kmpfacelink.api.createBodyTracker
import io.github.kmpfacelink.api.createFaceTracker
import io.github.kmpfacelink.api.createHandTracker
import io.github.kmpfacelink.api.createHolisticTracker
import io.github.kmpfacelink.model.TrackingModality
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Tests for iOS tracker state and error observation via StateFlow.
 */
class IosTrackerStateObservationTest {

    private val context = PlatformContext()

    // -- Initial state --

    @Test
    fun faceTrackerStateFlowEmitsIdleInitially() {
        val tracker = createFaceTracker(context)
        assertEquals(TrackingState.IDLE, tracker.state.value)
        tracker.release()
    }

    @Test
    fun handTrackerStateFlowEmitsIdleInitially() {
        val tracker = createHandTracker(context)
        assertEquals(TrackingState.IDLE, tracker.state.value)
        tracker.release()
    }

    @Test
    fun bodyTrackerStateFlowEmitsIdleInitially() {
        val tracker = createBodyTracker(context)
        assertEquals(TrackingState.IDLE, tracker.state.value)
        tracker.release()
    }

    @Test
    fun holisticTrackerStateFlowEmitsIdleInitially() {
        val tracker = createHolisticTracker(context)
        assertEquals(TrackingState.IDLE, tracker.state.value)
        tracker.release()
    }

    // -- Initial error message --

    @Test
    fun faceTrackerErrorMessageIsNullInitially() {
        val tracker = createFaceTracker(context)
        assertNull(tracker.errorMessage.value)
        tracker.release()
    }

    @Test
    fun handTrackerErrorMessageIsNullInitially() {
        val tracker = createHandTracker(context)
        assertNull(tracker.errorMessage.value)
        tracker.release()
    }

    @Test
    fun bodyTrackerErrorMessageIsNullInitially() {
        val tracker = createBodyTracker(context)
        assertNull(tracker.errorMessage.value)
        tracker.release()
    }

    @Test
    fun holisticTrackerErrorMessageIsNullInitially() {
        val tracker = createHolisticTracker(context)
        assertNull(tracker.errorMessage.value)
        tracker.release()
    }

    // -- Enabled modalities --

    @Test
    fun holisticTrackerReportsDefaultModalities() {
        val tracker = createHolisticTracker(context)
        val modalities = tracker.enabledModalities
        assertTrue(modalities.contains(TrackingModality.FACE))
        assertTrue(modalities.contains(TrackingModality.HAND))
        // Body tracking defaults to false (see HolisticTrackerConfig.enableBody KDoc).
        assertTrue(!modalities.contains(TrackingModality.BODY))
        tracker.release()
    }

    // -- TrackingData flow --

    @Test
    fun faceTrackerTrackingDataFlowIsNotNull() {
        val tracker = createFaceTracker(context)
        assertNotNull(tracker.trackingData)
        tracker.release()
    }

    @Test
    fun handTrackerTrackingDataFlowIsNotNull() {
        val tracker = createHandTracker(context)
        assertNotNull(tracker.trackingData)
        tracker.release()
    }
}
