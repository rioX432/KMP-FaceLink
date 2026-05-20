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
import io.github.kmpfacelink.model.BodyTrackerConfig
import io.github.kmpfacelink.model.FaceTrackerConfig
import io.github.kmpfacelink.model.HandTrackerConfig
import io.github.kmpfacelink.model.HolisticTrackerConfig
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Tests for tracker factory functions — creation and initial state.
 */
@RunWith(AndroidJUnit4::class)
class TrackerFactoryTest {

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

    // Trackers created in tests that need cleanup
    private val trackersToRelease = mutableListOf<io.github.kmpfacelink.api.Releasable>()

    @After
    fun tearDown() {
        trackersToRelease.forEach { it.release() }
        trackersToRelease.clear()
    }

    @Test
    fun createFaceTrackerReturnsNonNull() {
        val tracker = createFaceTracker(platformContext)
        trackersToRelease.add(tracker)
        assertNotNull("createFaceTracker should return non-null", tracker)
    }

    @Test
    fun createHandTrackerReturnsNonNull() {
        val tracker = createHandTracker(platformContext)
        trackersToRelease.add(tracker)
        assertNotNull("createHandTracker should return non-null", tracker)
    }

    @Test
    fun createBodyTrackerReturnsNonNull() {
        val tracker = createBodyTracker(platformContext)
        trackersToRelease.add(tracker)
        assertNotNull("createBodyTracker should return non-null", tracker)
    }

    @Test
    fun createHolisticTrackerReturnsNonNull() {
        val tracker = createHolisticTracker(platformContext)
        trackersToRelease.add(tracker)
        assertNotNull("createHolisticTracker should return non-null", tracker)
    }

    @Test
    fun faceTrackerInitialStateIsIdle() {
        val tracker = createFaceTracker(platformContext)
        trackersToRelease.add(tracker)
        assertEquals(TrackingState.IDLE, tracker.state.value)
    }

    @Test
    fun handTrackerInitialStateIsIdle() {
        val tracker = createHandTracker(platformContext)
        trackersToRelease.add(tracker)
        assertEquals(TrackingState.IDLE, tracker.state.value)
    }

    @Test
    fun bodyTrackerInitialStateIsIdle() {
        val tracker = createBodyTracker(platformContext)
        trackersToRelease.add(tracker)
        assertEquals(TrackingState.IDLE, tracker.state.value)
    }

    @Test
    fun holisticTrackerInitialStateIsIdle() {
        val tracker = createHolisticTracker(platformContext)
        trackersToRelease.add(tracker)
        assertEquals(TrackingState.IDLE, tracker.state.value)
    }

    @Test
    fun createFaceTrackerWithCustomConfig() {
        val config = FaceTrackerConfig(
            smoothingConfig = io.github.kmpfacelink.model.SmoothingConfig.None,
        )
        val tracker = createFaceTracker(platformContext, config)
        trackersToRelease.add(tracker)
        assertNotNull("createFaceTracker with custom config should succeed", tracker)
        assertEquals(TrackingState.IDLE, tracker.state.value)
    }

    @Test
    fun createHandTrackerWithCustomConfig() {
        val config = HandTrackerConfig(maxHands = 1, enableGestureRecognition = false)
        val tracker = createHandTracker(platformContext, config)
        trackersToRelease.add(tracker)
        assertNotNull("createHandTracker with custom config should succeed", tracker)
        assertEquals(TrackingState.IDLE, tracker.state.value)
    }

    @Test
    fun createBodyTrackerWithCustomConfig() {
        val config = BodyTrackerConfig(maxBodies = 2)
        val tracker = createBodyTracker(platformContext, config)
        trackersToRelease.add(tracker)
        assertNotNull("createBodyTracker with custom config should succeed", tracker)
        assertEquals(TrackingState.IDLE, tracker.state.value)
    }
}
