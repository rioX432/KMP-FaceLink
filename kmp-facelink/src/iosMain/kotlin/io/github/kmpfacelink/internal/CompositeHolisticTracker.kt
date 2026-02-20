package io.github.kmpfacelink.internal

import io.github.kmpfacelink.api.HolisticTracker
import io.github.kmpfacelink.api.TrackingState
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
import kotlinx.coroutines.flow.map
import platform.ARKit.ARSession
import platform.AVFoundation.AVCaptureSession
import kotlin.concurrent.atomics.AtomicInt
import kotlin.concurrent.atomics.ExperimentalAtomicApi

/**
 * iOS implementation of [HolisticTracker] that composes:
 * - ARKit face tracker (TrueDepth sensor, independent)
 * - Vision hand + body trackers sharing one [SharedVisionCaptureSession] (RGB camera)
 *
 * ARKit and AVCaptureSession use different camera sensors, so they can run simultaneously.
 */
@OptIn(ExperimentalAtomicApi::class)
internal class CompositeHolisticTracker(
    private val config: HolisticTrackerConfig,
) : HolisticTracker {

    // Hand + Body share a single AVCaptureSession if both are enabled
    private val sharedVisionSession: SharedVisionCaptureSession? =
        if (config.enableHand || config.enableBody) {
            SharedVisionCaptureSession(config.cameraFacing)
        } else {
            null
        }

    private val faceTracker = if (config.enableFace) {
        ARKitFaceTracker(config.faceConfig)
    } else {
        null
    }

    private val handTracker = if (config.enableHand) {
        VisionHandTracker(
            config.handConfig.copy(cameraFacing = config.cameraFacing),
            sharedVisionSession = sharedVisionSession,
        )
    } else {
        null
    }

    private val bodyTracker = if (config.enableBody) {
        VisionBodyTracker(
            config.bodyConfig.copy(cameraFacing = config.cameraFacing),
            sharedVisionSession = sharedVisionSession,
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

    /**
     * The underlying ARSession from the face tracker, for camera preview.
     * Only available after [start] when face tracking is enabled.
     */
    val arSession: ARSession?
        get() = faceTracker?.arSession

    /**
     * The underlying AVCaptureSession from the shared Vision session, for camera preview.
     * Only available after [start] when hand or body tracking is enabled.
     */
    val visionCaptureSession: AVCaptureSession?
        get() = sharedVisionSession?.captureSession

    @Suppress("TooGenericExceptionCaught")
    override suspend fun start() {
        pipelineLock.withLock {
            check(released.load() == 0) { "Cannot start a released tracker" }
            if (_state.value == TrackingState.TRACKING || _state.value == TrackingState.STARTING) return
            _state.value = TrackingState.STARTING
        }

        try {
            // Start trackers (registers frame handlers with shared session)
            faceTracker?.start()
            handTracker?.start()
            bodyTracker?.start()

            // Start the shared Vision capture session (feeds frames to hand + body)
            sharedVisionSession?.start()

            _state.value = TrackingState.TRACKING
        } catch (e: Exception) {
            _errorMessage.value = e.message ?: e.toString()
            _state.value = TrackingState.ERROR
            throw e
        }
    }

    override suspend fun stop() {
        pipelineLock.withLock {
            sharedVisionSession?.stop()
            faceTracker?.stop()
            handTracker?.stop()
            bodyTracker?.stop()
            _state.value = TrackingState.STOPPED
        }
    }

    override fun release() {
        released.store(1)
        pipelineLock.withLock {
            sharedVisionSession?.release()
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

    private fun createCombinedFlow(): Flow<HolisticTrackingData> {
        val faceFlow = faceTracker?.trackingData?.map { it } ?: flowOf(null)
        val handFlow = handTracker?.trackingData?.map { it } ?: flowOf(null)
        val bodyFlow = bodyTracker?.trackingData?.map { it } ?: flowOf(null)

        return combine(faceFlow, handFlow, bodyFlow) { face, hand, body ->
            val timestampMs = maxOf(
                face?.timestampMs ?: 0L,
                hand?.timestampMs ?: 0L,
                body?.timestampMs ?: 0L,
            )
            HolisticTrackingData(
                face = face,
                hand = hand,
                body = body,
                timestampMs = timestampMs,
                activeModalities = enabledModalities,
            )
        }
    }
}
