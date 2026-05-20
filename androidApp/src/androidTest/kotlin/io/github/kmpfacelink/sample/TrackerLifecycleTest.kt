package io.github.kmpfacelink.sample

import android.content.Context
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.kmpfacelink.api.PlatformContext
import io.github.kmpfacelink.api.TrackingState
import io.github.kmpfacelink.api.createBodyTracker
import io.github.kmpfacelink.api.createFaceTracker
import io.github.kmpfacelink.api.createHandTracker
import io.github.kmpfacelink.api.createHolisticTracker
import io.github.kmpfacelink.model.SmoothingConfig
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Tests for tracker lifecycle state transitions (without camera/model).
 *
 * These tests exercise release, stop-on-idle, and post-release guards
 * that do not require an active camera session.
 */
@RunWith(AndroidJUnit4::class)
class TrackerLifecycleTest {

    private lateinit var platformContext: PlatformContext

    @Before
    fun setUp() {
        val appContext: Context = ApplicationProvider.getApplicationContext()
        val lifecycleOwner = object : LifecycleOwner {
            val registry = LifecycleRegistry(this)
            override val lifecycle: Lifecycle get() = registry
        }
        platformContext = PlatformContext(appContext, lifecycleOwner)
    }

    @Test
    fun faceTrackerReleaseTransitionsToReleased() {
        val tracker = createFaceTracker(platformContext)
        tracker.release()
        assertEquals(TrackingState.RELEASED, tracker.state.value)
    }

    @Test
    fun handTrackerReleaseTransitionsToReleased() {
        val tracker = createHandTracker(platformContext)
        tracker.release()
        assertEquals(TrackingState.RELEASED, tracker.state.value)
    }

    @Test
    fun bodyTrackerReleaseTransitionsToReleased() {
        val tracker = createBodyTracker(platformContext)
        tracker.release()
        assertEquals(TrackingState.RELEASED, tracker.state.value)
    }

    @Test
    fun holisticTrackerReleaseTransitionsToReleased() {
        val tracker = createHolisticTracker(platformContext)
        tracker.release()
        assertEquals(TrackingState.RELEASED, tracker.state.value)
    }

    @Test
    fun doubleReleaseDoesNotCrash() {
        val tracker = createFaceTracker(platformContext)
        tracker.release()
        // Second release should not throw
        tracker.release()
        assertEquals(TrackingState.RELEASED, tracker.state.value)
    }

    @Test
    fun handTrackerDoubleReleaseDoesNotCrash() {
        val tracker = createHandTracker(platformContext)
        tracker.release()
        tracker.release()
        assertEquals(TrackingState.RELEASED, tracker.state.value)
    }

    @Test
    fun resetCalibrationAfterReleaseThrows() {
        val tracker = createFaceTracker(platformContext)
        tracker.release()
        try {
            tracker.resetCalibration()
            fail("Expected IllegalStateException")
        } catch (e: IllegalStateException) {
            // Expected
        }
    }

    @Test
    fun updateSmoothingAfterReleaseThrows() {
        val tracker = createFaceTracker(platformContext)
        tracker.release()
        try {
            tracker.updateSmoothing(SmoothingConfig.None)
            fail("Expected IllegalStateException")
        } catch (e: IllegalStateException) {
            // Expected
        }
    }

    @Test
    fun resetSmoothingAfterReleaseThrows() {
        val tracker = createFaceTracker(platformContext)
        tracker.release()
        try {
            tracker.resetSmoothing()
            fail("Expected IllegalStateException")
        } catch (e: IllegalStateException) {
            // Expected
        }
    }

    @Test
    fun startAfterReleaseThrows() = runTest {
        val tracker = createFaceTracker(platformContext)
        tracker.release()
        try {
            tracker.start()
            fail("Expected IllegalStateException")
        } catch (e: IllegalStateException) {
            // Expected
        }
    }

    @Test
    fun handTrackerUpdateSmoothingAfterReleaseThrows() {
        val tracker = createHandTracker(platformContext)
        tracker.release()
        try {
            tracker.updateSmoothing(SmoothingConfig.None)
            fail("Expected IllegalStateException")
        } catch (e: IllegalStateException) {
            // Expected
        }
    }

    @Test
    fun bodyTrackerResetSmoothingAfterReleaseThrows() {
        val tracker = createBodyTracker(platformContext)
        tracker.release()
        try {
            tracker.resetSmoothing()
            fail("Expected IllegalStateException")
        } catch (e: IllegalStateException) {
            // Expected
        }
    }

    @Test
    fun holisticTrackerUpdateSmoothingAfterReleaseThrows() {
        val tracker = createHolisticTracker(platformContext)
        tracker.release()
        try {
            tracker.updateSmoothing(SmoothingConfig.None)
            fail("Expected IllegalStateException")
        } catch (e: IllegalStateException) {
            // Expected
        }
    }

    @Test
    fun holisticTrackerResetSmoothingAfterReleaseThrows() {
        val tracker = createHolisticTracker(platformContext)
        tracker.release()
        try {
            tracker.resetSmoothing()
            fail("Expected IllegalStateException")
        } catch (e: IllegalStateException) {
            // Expected
        }
    }
}
