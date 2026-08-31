@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package io.github.kmpfacelink

import io.github.kmpfacelink.api.FaceTracker
import io.github.kmpfacelink.api.HandTracker
import io.github.kmpfacelink.api.HolisticTracker
import io.github.kmpfacelink.api.TrackingState
import io.github.kmpfacelink.api.getARSession
import io.github.kmpfacelink.api.getCaptureSession
import io.github.kmpfacelink.model.CameraFacing
import io.github.kmpfacelink.model.FaceTrackingData
import io.github.kmpfacelink.model.HandTrackingData
import io.github.kmpfacelink.model.HolisticTrackingData
import io.github.kmpfacelink.model.SmoothingConfig
import io.github.kmpfacelink.model.TrackerOperationStatus
import io.github.kmpfacelink.model.TrackingModality
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlin.test.Test
import kotlin.test.assertNull

/**
 * Tests for iOS-specific public session APIs.
 *
 * These extension functions cast to internal implementations; custom/fake
 * implementations should return null, which is what we verify here.
 */
class IosSessionApiTest {

    // -- FaceTracker.getARSession() --

    @Test
    fun getARSessionReturnsNullForCustomImplementation() {
        val tracker = IosTestFakeFaceTracker()
        assertNull(tracker.getARSession())
    }

    // -- HandTracker.getCaptureSession() --

    @Test
    fun getCaptureSessionReturnsNullForCustomImplementation() {
        val tracker = IosTestFakeHandTracker()
        assertNull(tracker.getCaptureSession())
    }

    // -- HolisticTracker.getARSession() / getCaptureSession() --

    @Test
    fun holisticGetARSessionReturnsNullForCustomImplementation() {
        val tracker = IosTestFakeHolisticTracker()
        assertNull(tracker.getARSession())
    }

    @Test
    fun holisticGetCaptureSessionReturnsNullForCustomImplementation() {
        val tracker = IosTestFakeHolisticTracker()
        assertNull(tracker.getCaptureSession())
    }
}

// -- Fake implementations for testing --

private class IosTestFakeFaceTracker : FaceTracker {
    override val trackingData: Flow<FaceTrackingData> = emptyFlow()
    override val state: StateFlow<TrackingState> = MutableStateFlow(TrackingState.IDLE)
    override val errorMessage: StateFlow<String?> = MutableStateFlow(null)

    override suspend fun start() = Unit
    override suspend fun stop() = Unit
    override fun release() = Unit
    override fun resetCalibration(): TrackerOperationStatus = TrackerOperationStatus.APPLIED
    override fun resetSmoothing(): TrackerOperationStatus = TrackerOperationStatus.APPLIED
    override fun updateSmoothing(config: SmoothingConfig): TrackerOperationStatus = TrackerOperationStatus.APPLIED
    override suspend fun switchCamera(facing: CameraFacing) = Unit
}

private class IosTestFakeHandTracker : HandTracker {
    override val trackingData: Flow<HandTrackingData> = emptyFlow()
    override val state: StateFlow<TrackingState> = MutableStateFlow(TrackingState.IDLE)
    override val errorMessage: StateFlow<String?> = MutableStateFlow(null)

    override suspend fun start() = Unit
    override suspend fun stop() = Unit
    override fun release() = Unit
    override fun updateSmoothing(config: SmoothingConfig): TrackerOperationStatus = TrackerOperationStatus.APPLIED
    override fun resetSmoothing(): TrackerOperationStatus = TrackerOperationStatus.APPLIED
    override suspend fun switchCamera(facing: CameraFacing) = Unit
}

private class IosTestFakeHolisticTracker : HolisticTracker {
    override val trackingData: Flow<HolisticTrackingData> = emptyFlow()
    override val state: StateFlow<TrackingState> = MutableStateFlow(TrackingState.IDLE)
    override val errorMessage: StateFlow<String?> = MutableStateFlow(null)
    override val enabledModalities: Set<TrackingModality> = emptySet()

    override suspend fun start() = Unit
    override suspend fun stop() = Unit
    override fun release() = Unit
    override fun updateSmoothing(config: SmoothingConfig): TrackerOperationStatus = TrackerOperationStatus.APPLIED
    override fun resetSmoothing(): TrackerOperationStatus = TrackerOperationStatus.APPLIED
    override suspend fun switchCamera(facing: CameraFacing) = Unit
}
