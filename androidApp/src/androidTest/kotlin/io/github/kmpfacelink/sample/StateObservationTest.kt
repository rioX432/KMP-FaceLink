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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Tests for state and error message Flow observation.
 */
@RunWith(AndroidJUnit4::class)
class StateObservationTest {

    private lateinit var platformContext: PlatformContext
    private val trackersToRelease = mutableListOf<io.github.kmpfacelink.api.Releasable>()

    @Before
    fun setUp() {
        val appContext: Context = ApplicationProvider.getApplicationContext()
        val lifecycleOwner = object : LifecycleOwner {
            val registry = LifecycleRegistry(this)
            override val lifecycle: Lifecycle get() = registry
        }
        platformContext = PlatformContext(appContext, lifecycleOwner)
    }

    @After
    fun tearDown() {
        trackersToRelease.forEach { it.release() }
        trackersToRelease.clear()
    }

    @Test
    fun faceTrackerStateFlowEmitsIdleInitially() = runTest {
        val tracker = createFaceTracker(platformContext)
        trackersToRelease.add(tracker)
        val initialState = tracker.state.first()
        assertEquals(TrackingState.IDLE, initialState)
    }

    @Test
    fun handTrackerStateFlowEmitsIdleInitially() = runTest {
        val tracker = createHandTracker(platformContext)
        trackersToRelease.add(tracker)
        val initialState = tracker.state.first()
        assertEquals(TrackingState.IDLE, initialState)
    }

    @Test
    fun bodyTrackerStateFlowEmitsIdleInitially() = runTest {
        val tracker = createBodyTracker(platformContext)
        trackersToRelease.add(tracker)
        val initialState = tracker.state.first()
        assertEquals(TrackingState.IDLE, initialState)
    }

    @Test
    fun holisticTrackerStateFlowEmitsIdleInitially() = runTest {
        val tracker = createHolisticTracker(platformContext)
        trackersToRelease.add(tracker)
        val initialState = tracker.state.first()
        assertEquals(TrackingState.IDLE, initialState)
    }

    @Test
    fun faceTrackerErrorMessageIsNullInitially() = runTest {
        val tracker = createFaceTracker(platformContext)
        trackersToRelease.add(tracker)
        val errorMessage = tracker.errorMessage.first()
        assertNull("Error message should be null initially", errorMessage)
    }

    @Test
    fun handTrackerErrorMessageIsNullInitially() = runTest {
        val tracker = createHandTracker(platformContext)
        trackersToRelease.add(tracker)
        assertNull(tracker.errorMessage.first())
    }

    @Test
    fun bodyTrackerErrorMessageIsNullInitially() = runTest {
        val tracker = createBodyTracker(platformContext)
        trackersToRelease.add(tracker)
        assertNull(tracker.errorMessage.first())
    }

    @Test
    fun holisticTrackerErrorMessageIsNullInitially() = runTest {
        val tracker = createHolisticTracker(platformContext)
        trackersToRelease.add(tracker)
        assertNull(tracker.errorMessage.first())
    }

    @Test
    fun stateFlowIsAccessibleAfterRelease() {
        val tracker = createFaceTracker(platformContext)
        tracker.release()
        // StateFlow should still be readable after release
        val state = tracker.state.value
        assertNotNull("State should still be readable", state)
        assertEquals(TrackingState.RELEASED, state)
    }

    @Test
    fun multipleCollectorsOnStateDontInterfere() = runTest {
        val tracker = createFaceTracker(platformContext)
        trackersToRelease.add(tracker)

        // Collect from state twice — both should get IDLE
        val state1 = tracker.state.first()
        val state2 = tracker.state.first()
        assertEquals(TrackingState.IDLE, state1)
        assertEquals(TrackingState.IDLE, state2)
    }

    @Test
    fun stateTransitionsToReleasedOnRelease() = runTest {
        val tracker = createHandTracker(platformContext)
        assertEquals(TrackingState.IDLE, tracker.state.first())
        tracker.release()
        assertEquals(TrackingState.RELEASED, tracker.state.first())
    }
}
