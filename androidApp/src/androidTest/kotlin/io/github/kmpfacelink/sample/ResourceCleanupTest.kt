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
import io.github.kmpfacelink.model.CameraFacing
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Tests for resource cleanup and edge-case resilience.
 */
@RunWith(AndroidJUnit4::class)
class ResourceCleanupTest {

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
    fun faceTrackerReleaseSetsReleasedState() {
        val tracker = createFaceTracker(platformContext)
        tracker.release()
        assertEquals(TrackingState.RELEASED, tracker.state.value)
    }

    @Test
    fun handTrackerReleaseSetsReleasedState() {
        val tracker = createHandTracker(platformContext)
        tracker.release()
        assertEquals(TrackingState.RELEASED, tracker.state.value)
    }

    @Test
    fun bodyTrackerReleaseSetsReleasedState() {
        val tracker = createBodyTracker(platformContext)
        tracker.release()
        assertEquals(TrackingState.RELEASED, tracker.state.value)
    }

    @Test
    fun holisticTrackerReleaseSetsReleasedState() {
        val tracker = createHolisticTracker(platformContext)
        tracker.release()
        assertEquals(TrackingState.RELEASED, tracker.state.value)
    }

    @Test
    fun createAndReleaseMultipleFaceTrackers() {
        // Verify no resource leaks from sequential create/release
        repeat(3) {
            val tracker = createFaceTracker(platformContext)
            assertEquals(TrackingState.IDLE, tracker.state.value)
            tracker.release()
            assertEquals(TrackingState.RELEASED, tracker.state.value)
        }
    }

    @Test
    fun createAndReleaseMultipleHandTrackers() {
        repeat(3) {
            val tracker = createHandTracker(platformContext)
            tracker.release()
            assertEquals(TrackingState.RELEASED, tracker.state.value)
        }
    }

    @Test
    fun switchCameraOnIdleFaceTrackerDoesNotCrash() = runTest {
        val tracker = createFaceTracker(platformContext)
        try {
            // switchCamera on IDLE tracker should just update the facing, not crash
            tracker.switchCamera(CameraFacing.BACK)
        } finally {
            tracker.release()
        }
    }

    @Test
    fun switchCameraOnIdleHandTrackerDoesNotCrash() = runTest {
        val tracker = createHandTracker(platformContext)
        try {
            tracker.switchCamera(CameraFacing.BACK)
        } finally {
            tracker.release()
        }
    }

    @Test
    fun switchCameraOnReleasedFaceTrackerThrows() = runTest {
        val tracker = createFaceTracker(platformContext)
        tracker.release()
        try {
            tracker.switchCamera(CameraFacing.BACK)
            fail("Expected IllegalStateException")
        } catch (e: IllegalStateException) {
            // Expected
        }
    }

    @Test
    fun switchCameraOnReleasedHolisticTrackerThrows() = runTest {
        val tracker = createHolisticTracker(platformContext)
        tracker.release()
        try {
            tracker.switchCamera(CameraFacing.BACK)
            fail("Expected IllegalStateException")
        } catch (e: IllegalStateException) {
            // Expected
        }
    }

    @Test
    fun switchCameraToSameFacingIsNoOp() = runTest {
        val tracker = createFaceTracker(platformContext)
        try {
            // Switching to the same facing (FRONT -> FRONT) should be a no-op
            tracker.switchCamera(CameraFacing.FRONT)
            assertEquals(TrackingState.IDLE, tracker.state.value)
        } finally {
            tracker.release()
        }
    }
}
