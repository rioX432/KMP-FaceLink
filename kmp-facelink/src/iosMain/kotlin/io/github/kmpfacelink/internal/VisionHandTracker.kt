@file:OptIn(
    kotlinx.cinterop.ExperimentalForeignApi::class,
    kotlinx.cinterop.BetaInteropApi::class,
)

package io.github.kmpfacelink.internal

import io.github.kmpfacelink.api.HandTracker
import io.github.kmpfacelink.api.TrackingState
import io.github.kmpfacelink.model.HandGesture
import io.github.kmpfacelink.model.HandTrackerConfig
import io.github.kmpfacelink.model.HandTrackingData
import io.github.kmpfacelink.model.SmoothingConfig
import io.github.kmpfacelink.model.TrackedHand
import io.github.kmpfacelink.util.GestureClassifier
import io.github.kmpfacelink.util.HandLandmarkSmoother
import io.github.kmpfacelink.util.PlatformLock
import io.github.kmpfacelink.util.createHandSmoother
import io.github.kmpfacelink.util.withLock
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import platform.AVFoundation.AVCaptureConnection
import platform.AVFoundation.AVCaptureDevice
import platform.AVFoundation.AVCaptureDeviceInput
import platform.AVFoundation.AVCaptureOutput
import platform.AVFoundation.AVCaptureSession
import platform.AVFoundation.AVCaptureSessionPresetMedium
import platform.AVFoundation.AVCaptureVideoDataOutput
import platform.AVFoundation.AVCaptureVideoDataOutputSampleBufferDelegateProtocol
import platform.AVFoundation.AVMediaTypeVideo
import platform.CoreMedia.CMSampleBufferGetImageBuffer
import platform.CoreMedia.CMSampleBufferRef
import platform.Foundation.NSDate
import platform.Foundation.timeIntervalSince1970
import platform.Vision.VNDetectHumanHandPoseRequest
import platform.Vision.VNHumanHandPoseObservation
import platform.Vision.VNImageRequestHandler
import platform.darwin.NSObject
import platform.darwin.dispatch_queue_create
import kotlin.concurrent.atomics.AtomicInt
import kotlin.concurrent.atomics.ExperimentalAtomicApi

@OptIn(ExperimentalAtomicApi::class)
internal class VisionHandTracker(
    private val config: HandTrackerConfig,
) : HandTracker {

    private val _trackingData = MutableSharedFlow<HandTrackingData>(
        replay = 1,
        extraBufferCapacity = 1,
    )
    override val trackingData: Flow<HandTrackingData> = _trackingData.asSharedFlow()

    private val _state = MutableStateFlow(TrackingState.IDLE)
    override val state: StateFlow<TrackingState> = _state.asStateFlow()

    private val pipelineLock = PlatformLock()
    private val released = AtomicInt(0)

    private var smoother: HandLandmarkSmoother? = config.smoothingConfig.createHandSmoother()
    private var captureSession: AVCaptureSession? = null
    private val videoOutputDelegate = VideoOutputDelegate()
    private val videoQueue = dispatch_queue_create("io.github.kmpfacelink.hand.video", null)

    override suspend fun start() {
        pipelineLock.withLock {
            check(released.load() == 0) { "Cannot start a released tracker" }
            if (_state.value == TrackingState.TRACKING || _state.value == TrackingState.STARTING) return
            _state.value = TrackingState.STARTING
        }

        try {
            setupCaptureSession()
            captureSession?.startRunning()
            _state.value = TrackingState.TRACKING
        } catch (e: Exception) {
            _state.value = TrackingState.ERROR
            throw e
        }
    }

    override suspend fun stop() {
        captureSession?.stopRunning()
        _state.value = TrackingState.STOPPED
    }

    override fun release() {
        released.store(1)
        pipelineLock.withLock {
            captureSession?.stopRunning()
            captureSession = null
            _state.value = TrackingState.STOPPED
        }
    }

    override fun updateSmoothing(config: SmoothingConfig) {
        pipelineLock.withLock {
            smoother = config.createHandSmoother()
        }
    }

    private fun setupCaptureSession() {
        val session = AVCaptureSession()
        session.sessionPreset = AVCaptureSessionPresetMedium

        val device = AVCaptureDevice.defaultDeviceWithMediaType(AVMediaTypeVideo)
            ?: throw UnsupportedOperationException("No camera available")

        val input = AVCaptureDeviceInput.deviceInputWithDevice(device, null)
            ?: throw UnsupportedOperationException("Cannot create camera input")

        if (session.canAddInput(input)) {
            session.addInput(input)
        }

        val videoOutput = AVCaptureVideoDataOutput()
        videoOutput.setSampleBufferDelegate(videoOutputDelegate, videoQueue)
        videoOutput.alwaysDiscardsLateVideoFrames = true

        if (session.canAddOutput(videoOutput)) {
            session.addOutput(videoOutput)
        }

        captureSession = session
    }

    @Suppress("ReturnCount")
    private fun processVideoFrame(sampleBuffer: CMSampleBufferRef?) {
        if (sampleBuffer == null || _state.value != TrackingState.TRACKING) return

        val pixelBuffer = CMSampleBufferGetImageBuffer(sampleBuffer) ?: return
        val timestampMs = currentTimeMillis()

        val observations = detectHands(pixelBuffer, timestampMs) ?: return

        var hands = observations.map { observation -> mapObservation(observation) }

        // Apply smoothing (protected by lock)
        val processedHands = pipelineLock.withLock {
            if (released.load() != 0) return
            smoother?.let { hands = it.smooth(hands, timestampMs) }
            hands
        }

        _trackingData.tryEmit(
            HandTrackingData(
                hands = processedHands,
                timestampMs = timestampMs,
                isTracking = true,
            ),
        )
    }

    private fun detectHands(
        pixelBuffer: platform.CoreVideo.CVPixelBufferRef,
        timestampMs: Long,
    ): List<VNHumanHandPoseObservation>? {
        val request = VNDetectHumanHandPoseRequest()
        @Suppress("CAST_NEVER_SUCCEEDS")
        request.maximumHandCount = config.maxHands.toULong()

        val handler = VNImageRequestHandler(pixelBuffer, options = emptyMap<Any?, Any>())

        try {
            handler.performRequests(listOf(request), null)
        } catch (_: Exception) {
            _trackingData.tryEmit(HandTrackingData.notTracking(timestampMs))
            return null
        }

        @Suppress("UNCHECKED_CAST")
        val observations = request.results as? List<VNHumanHandPoseObservation>

        if (observations.isNullOrEmpty()) {
            _trackingData.tryEmit(HandTrackingData.notTracking(timestampMs))
            return null
        }

        return observations
    }

    private fun mapObservation(observation: VNHumanHandPoseObservation): TrackedHand {
        val landmarks = VisionHandLandmarkMapper.mapLandmarks(observation)
        val handedness = VisionHandLandmarkMapper.mapHandedness(observation.chirality)

        val (gesture, confidence) = if (config.enableGestureRecognition) {
            GestureClassifier.classify(landmarks)
        } else {
            HandGesture.NONE to 0f
        }

        return TrackedHand(
            handedness = handedness,
            landmarks = landmarks,
            gesture = gesture,
            gestureConfidence = confidence,
        )
    }

    private inner class VideoOutputDelegate :
        NSObject(),
        AVCaptureVideoDataOutputSampleBufferDelegateProtocol {
        override fun captureOutput(
            output: AVCaptureOutput,
            didOutputSampleBuffer: CMSampleBufferRef?,
            fromConnection: AVCaptureConnection,
        ) {
            processVideoFrame(didOutputSampleBuffer)
        }
    }

    private companion object {
        private const val MS_PER_SECOND = 1000

        fun currentTimeMillis(): Long =
            (NSDate().timeIntervalSince1970 * MS_PER_SECOND).toLong()
    }
}
