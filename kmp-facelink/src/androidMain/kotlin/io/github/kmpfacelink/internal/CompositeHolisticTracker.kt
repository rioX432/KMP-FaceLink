package io.github.kmpfacelink.internal

import android.util.Log
import androidx.camera.core.Preview
import io.github.kmpfacelink.api.HolisticTracker
import io.github.kmpfacelink.api.PlatformContext
import io.github.kmpfacelink.api.TrackingState
import io.github.kmpfacelink.model.BodyTrackingData
import io.github.kmpfacelink.model.CameraFacing
import io.github.kmpfacelink.model.FaceTrackingData
import io.github.kmpfacelink.model.HandTrackingData
import io.github.kmpfacelink.model.HolisticTrackerConfig
import io.github.kmpfacelink.model.HolisticTrackingData
import io.github.kmpfacelink.model.SmoothingConfig
import io.github.kmpfacelink.model.TrackingModality
import io.github.kmpfacelink.util.PlatformLock
import io.github.kmpfacelink.util.withLock
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlin.concurrent.atomics.AtomicInt
import kotlin.concurrent.atomics.ExperimentalAtomicApi

/**
 * Android implementation of [HolisticTracker] that composes individual
 * MediaPipe trackers with a [SharedCameraSession] so all modalities
 * share one CameraX camera session.
 */
@OptIn(ExperimentalAtomicApi::class)
internal class CompositeHolisticTracker(
    private val platformContext: PlatformContext,
    private val config: HolisticTrackerConfig,
) : HolisticTracker, PreviewableHolisticTracker {

    private val sharedCamera = SharedCameraSession(platformContext)

    private val faceTracker = if (config.enableFace) {
        MediaPipeFaceTracker(
            platformContext,
            config.faceConfig.copy(
                cameraFacing = config.cameraFacing,
                thermalPolicy = config.thermalPolicy,
            ),
            sharedCameraSession = sharedCamera,
        )
    } else {
        null
    }

    private val handTracker = if (config.enableHand) {
        MediaPipeHandTracker(
            platformContext,
            config.handConfig.copy(
                cameraFacing = config.cameraFacing,
                thermalPolicy = config.thermalPolicy,
            ),
            sharedCameraSession = sharedCamera,
        )
    } else {
        null
    }

    private val bodyTracker = if (config.enableBody) {
        MediaPipeBodyTracker(
            platformContext,
            config.bodyConfig.copy(
                cameraFacing = config.cameraFacing,
                thermalPolicy = config.thermalPolicy,
            ),
            sharedCameraSession = sharedCamera,
        )
    } else {
        null
    }

    override val enabledModalities: Set<TrackingModality> = buildSet {
        if (config.enableFace) add(TrackingModality.FACE)
        if (config.enableHand) add(TrackingModality.HAND)
        if (config.enableBody) add(TrackingModality.BODY)
    }

    override val trackingData: Flow<HolisticTrackingData> = createCombinedFlow()

    private val _state = MutableStateFlow(TrackingState.IDLE)
    override val state: StateFlow<TrackingState> = _state.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    override val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private val pipelineLock = PlatformLock()
    private val released = AtomicInt(0)

    @Volatile
    private var currentCameraFacing: CameraFacing = config.cameraFacing

    override fun setSurfaceProvider(surfaceProvider: Preview.SurfaceProvider?) {
        sharedCamera.setSurfaceProvider(surfaceProvider)
    }

    @Suppress("TooGenericExceptionCaught")
    override suspend fun start() {
        pipelineLock.withLock {
            check(released.load() == 0) { "Cannot start a released tracker" }
            if (_state.value == TrackingState.TRACKING || _state.value == TrackingState.STARTING) return
            _state.value = TrackingState.STARTING
        }

        try {
            // Start each tracker (initializes MediaPipe + registers ImageAnalysis with shared camera)
            faceTracker?.start()
            handTracker?.start()
            bodyTracker?.start()

            // Start the shared camera session (binds all registered ImageAnalysis use cases)
            sharedCamera.start(config.cameraFacing)

            _state.value = TrackingState.TRACKING
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start holistic tracking", e)
            _errorMessage.value = e.message ?: e.toString()
            _state.value = TrackingState.ERROR
            throw e
        }
    }

    override suspend fun stop() {
        pipelineLock.withLock {
            sharedCamera.stop()
            faceTracker?.stop()
            handTracker?.stop()
            bodyTracker?.stop()
            _state.value = TrackingState.STOPPED
        }
    }

    override fun release() {
        released.store(1)
        pipelineLock.withLock {
            sharedCamera.release()
            faceTracker?.release()
            handTracker?.release()
            bodyTracker?.release()
            _state.value = TrackingState.RELEASED
        }
    }

    override fun updateSmoothing(config: SmoothingConfig) {
        pipelineLock.withLock {
            check(released.load() == 0) { "Cannot update smoothing on a released tracker" }
            faceTracker?.updateSmoothing(config)
            handTracker?.updateSmoothing(config)
            bodyTracker?.updateSmoothing(config)
        }
    }

    override suspend fun switchCamera(facing: CameraFacing) {
        pipelineLock.withLock {
            check(released.load() == 0) { "Cannot switch camera on a released tracker" }
            if (currentCameraFacing == facing) return
            val wasTracking = _state.value == TrackingState.TRACKING
            if (wasTracking) {
                sharedCamera.stop()
            }
            currentCameraFacing = facing
            faceTracker?.let { it.currentCameraFacing = facing }
            handTracker?.let { it.currentCameraFacing = facing }
            bodyTracker?.let { it.currentCameraFacing = facing }
            if (wasTracking) {
                sharedCamera.start(facing)
            }
        }
    }

    private fun createCombinedFlow(): Flow<HolisticTrackingData> {
        val faceFlow: Flow<FaceTrackingData?> = faceTracker?.trackingData ?: flowOf(null)
        val handFlow: Flow<HandTrackingData?> = handTracker?.trackingData ?: flowOf(null)
        val bodyFlow: Flow<BodyTrackingData?> = bodyTracker?.trackingData ?: flowOf(null)

        return combine(faceFlow, handFlow, bodyFlow) { face, hand, body ->
            val timestampMs = maxOf(
                face?.timestampMs ?: 0L,
                hand?.timestampMs ?: 0L,
                body?.timestampMs ?: 0L,
            )
            // Use the most severe thermal state from any sub-tracker
            val thermalState = maxOf(
                face?.thermalState?.ordinal ?: 0,
                hand?.thermalState?.ordinal ?: 0,
                body?.thermalState?.ordinal ?: 0,
            ).let { io.github.kmpfacelink.model.ThermalState.entries[it] }

            HolisticTrackingData(
                face = face,
                hand = hand,
                body = body,
                timestampMs = timestampMs,
                activeModalities = enabledModalities,
                thermalState = thermalState,
            )
        }
    }

    companion object {
        private const val TAG = "CompositeHolisticTracker"
    }
}
