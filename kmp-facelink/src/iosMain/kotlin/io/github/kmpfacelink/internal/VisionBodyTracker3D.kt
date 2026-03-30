@file:OptIn(
    kotlinx.cinterop.ExperimentalForeignApi::class,
    kotlinx.cinterop.BetaInteropApi::class,
)

package io.github.kmpfacelink.internal

import io.github.kmpfacelink.api.BodyTracker
import io.github.kmpfacelink.api.TrackingState
import io.github.kmpfacelink.model.BodyTrackerConfig
import io.github.kmpfacelink.model.BodyTrackingData
import io.github.kmpfacelink.model.CameraFacing
import io.github.kmpfacelink.model.SmoothingConfig
import io.github.kmpfacelink.model.TrackedBody
import io.github.kmpfacelink.util.BodyLandmarkSmoother
import io.github.kmpfacelink.util.FaceLinkLogger
import io.github.kmpfacelink.util.FrameThrottler
import io.github.kmpfacelink.util.PlatformLock
import io.github.kmpfacelink.util.ThermalMonitor
import io.github.kmpfacelink.util.createBodySmoother
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
import platform.AVFoundation.AVCaptureDevicePositionBack
import platform.AVFoundation.AVCaptureDevicePositionFront
import platform.AVFoundation.AVCaptureOutput
import platform.AVFoundation.AVCaptureSession
import platform.AVFoundation.AVCaptureSessionPreset640x480
import platform.AVFoundation.AVCaptureVideoDataOutput
import platform.AVFoundation.AVCaptureVideoDataOutputSampleBufferDelegateProtocol
import platform.AVFoundation.AVCaptureVideoOrientationPortrait
import platform.AVFoundation.AVMediaTypeVideo
import platform.AVFoundation.position
import platform.CoreMedia.CMSampleBufferGetImageBuffer
import platform.CoreMedia.CMSampleBufferRef
import platform.CoreMedia.CMTimeMake
import platform.CoreVideo.CVPixelBufferGetHeight
import platform.CoreVideo.CVPixelBufferGetWidth
import platform.CoreVideo.kCVPixelBufferPixelFormatTypeKey
import platform.CoreVideo.kCVPixelFormatType_420YpCbCr8BiPlanarFullRange
import platform.Foundation.NSNumber
import platform.Vision.VNDetectHumanBodyPose3DRequest
import platform.Vision.VNHumanBodyPose3DObservation
import platform.Vision.VNImageRequestHandler
import platform.darwin.NSObject
import platform.darwin.dispatch_queue_create
import kotlin.concurrent.atomics.AtomicInt
import kotlin.concurrent.atomics.ExperimentalAtomicApi

/**
 * iOS body tracker using VNDetectHumanBodyPose3DRequest (iOS 17+).
 *
 * Provides 3D joint positions in meters relative to the root joint
 * (center of hips). Requires higher resolution input than 2D for
 * accurate depth estimation.
 */
@OptIn(ExperimentalAtomicApi::class)
internal class VisionBodyTracker3D(
    private val config: BodyTrackerConfig,
    private val sharedVisionSession: SharedVisionCaptureSession? = null,
) : BodyTracker {

    private val _trackingData = MutableSharedFlow<BodyTrackingData>(
        replay = 1,
        extraBufferCapacity = 1,
    )
    override val trackingData: Flow<BodyTrackingData> = _trackingData.asSharedFlow()

    private val _state = MutableStateFlow(TrackingState.IDLE)
    override val state: StateFlow<TrackingState> = _state.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    override val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private val pipelineLock = PlatformLock()
    private val released = AtomicInt(0)
    private val processing = AtomicInt(0)

    private var smoother: BodyLandmarkSmoother? = config.smoothingConfig.createBodySmoother()
    private val thermalMonitor: ThermalMonitor = IosThermalMonitor()
    private val frameThrottler = FrameThrottler(config.thermalPolicy)

    private var currentCameraFacing: CameraFacing = config.cameraFacing
    private val bodyPose3DRequest = VNDetectHumanBodyPose3DRequest()

    /**
     * The underlying AVCaptureSession, exposed for camera preview in the iOS sample app.
     * Only available after [start] has been called.
     */
    var captureSession: AVCaptureSession? = null
        private set
    private val videoOutputDelegate = VideoOutputDelegate()
    private val videoQueue = dispatch_queue_create("io.github.kmpfacelink.body3d.video", null)

    override suspend fun start() {
        pipelineLock.withLock {
            check(released.load() == 0) { "Cannot start a released tracker" }
            if (_state.value == TrackingState.TRACKING || _state.value == TrackingState.STARTING) return
            _state.value = TrackingState.STARTING
        }

        try {
            if (sharedVisionSession != null) {
                sharedVisionSession.addFrameHandler(::processVideoFrame)
            } else {
                if (captureSession == null) {
                    setupCaptureSession()
                }
                captureSession?.startRunning()
            }
            pipelineLock.withLock {
                if (released.load() == 0) {
                    _state.value = TrackingState.TRACKING
                }
            }
        } catch (e: Exception) {
            _errorMessage.value = e.message ?: e.toString()
            _state.value = TrackingState.ERROR
            throw e
        }
    }

    override suspend fun stop() {
        pipelineLock.withLock {
            if (sharedVisionSession == null) captureSession?.stopRunning()
            _state.value = TrackingState.STOPPED
        }
    }

    override fun release() {
        pipelineLock.withLock {
            released.store(1)
            if (sharedVisionSession == null) {
                captureSession?.stopRunning()
                captureSession = null
            }
            thermalMonitor.release()
            _state.value = TrackingState.RELEASED
        }
    }

    override fun updateSmoothing(config: SmoothingConfig) {
        pipelineLock.withLock {
            check(released.load() == 0) { "Cannot update smoothing on a released tracker" }
            smoother = config.createBodySmoother()
        }
    }

    override fun resetSmoothing() {
        pipelineLock.withLock {
            check(released.load() == 0) { "Cannot reset smoothing on a released tracker" }
            smoother?.reset()
        }
    }

    override suspend fun switchCamera(facing: CameraFacing) {
        pipelineLock.withLock {
            check(released.load() == 0) { "Cannot switch camera on a released tracker" }
        }
        if (currentCameraFacing == facing) return
        val wasTracking = _state.value == TrackingState.TRACKING
        if (wasTracking && sharedVisionSession == null) {
            captureSession?.stopRunning()
            captureSession = null
        }
        currentCameraFacing = facing
        if (wasTracking && sharedVisionSession == null) {
            setupCaptureSession()
            captureSession?.startRunning()
        }
    }

    @Suppress("LongMethod")
    private fun setupCaptureSession() {
        val session = AVCaptureSession()
        // 3D pose needs higher resolution than 2D for depth estimation
        session.sessionPreset = AVCaptureSessionPreset640x480

        val position = when (currentCameraFacing) {
            CameraFacing.FRONT -> AVCaptureDevicePositionFront
            CameraFacing.BACK -> AVCaptureDevicePositionBack
        }

        val device = AVCaptureDevice.devicesWithMediaType(AVMediaTypeVideo)
            .filterIsInstance<AVCaptureDevice>()
            .firstOrNull { it.position == position }
            ?: AVCaptureDevice.defaultDeviceWithMediaType(AVMediaTypeVideo)
            ?: throw UnsupportedOperationException("No camera available")

        configureCameraFrameRate(device)

        val input = AVCaptureDeviceInput.deviceInputWithDevice(device, null)
            ?: throw UnsupportedOperationException("Cannot create camera input")

        if (session.canAddInput(input)) {
            session.addInput(input)
        }

        val videoOutput = AVCaptureVideoDataOutput()
        videoOutput.videoSettings = mapOf<Any?, Any>(
            kCVPixelBufferPixelFormatTypeKey to NSNumber(
                unsignedInt = kCVPixelFormatType_420YpCbCr8BiPlanarFullRange,
            ),
        )
        videoOutput.setSampleBufferDelegate(videoOutputDelegate, videoQueue)
        videoOutput.alwaysDiscardsLateVideoFrames = true

        if (session.canAddOutput(videoOutput)) {
            session.addOutput(videoOutput)
        }

        val connection = videoOutput.connectionWithMediaType(AVMediaTypeVideo)
        if (connection != null && connection.isVideoOrientationSupported()) {
            connection.videoOrientation = AVCaptureVideoOrientationPortrait
        }

        captureSession = session
    }

    private fun configureCameraFrameRate(device: AVCaptureDevice) {
        @Suppress("SwallowedException")
        val locked = try {
            device.lockForConfiguration(null)
            true
        } catch (e: Exception) {
            FaceLinkLogger.w(TAG, "failed to lock device for frame rate config — $e")
            false
        }
        if (!locked) return

        val frameDuration = CMTimeMake(value = 1, timescale = TARGET_FPS)
        device.setActiveVideoMinFrameDuration(frameDuration)
        device.setActiveVideoMaxFrameDuration(frameDuration)
        device.unlockForConfiguration()
    }

    @Suppress("ReturnCount")
    private fun processVideoFrame(sampleBuffer: CMSampleBufferRef?) {
        if (sampleBuffer == null || _state.value != TrackingState.TRACKING) return
        if (!processing.compareAndSet(expectedValue = 0, newValue = 1)) return

        try {
            val pixelBuffer = CMSampleBufferGetImageBuffer(sampleBuffer) ?: return
            val timestampMs = currentTimeMillis()

            val currentThermalState = thermalMonitor.thermalState.value
            if (!frameThrottler.shouldProcess(timestampMs, currentThermalState)) return

            val imageWidth = CVPixelBufferGetWidth(pixelBuffer).toInt()
            val imageHeight = CVPixelBufferGetHeight(pixelBuffer).toInt()

            val observations = detectBodies3D(pixelBuffer, timestampMs) ?: return

            var bodies = observations.map { observation ->
                TrackedBody(landmarks = VisionBodyLandmarkMapper3D.mapLandmarks(observation))
            }

            val processedBodies = pipelineLock.withLock {
                if (released.load() != 0) return
                smoother?.let { bodies = it.smooth(bodies, timestampMs) }
                bodies
            }

            _trackingData.tryEmit(
                BodyTrackingData(
                    bodies = processedBodies,
                    sourceImageWidth = imageWidth,
                    sourceImageHeight = imageHeight,
                    timestampMs = timestampMs,
                    isTracking = true,
                    thermalState = currentThermalState,
                ),
            )
        } finally {
            processing.store(0)
        }
    }

    private fun detectBodies3D(
        pixelBuffer: platform.CoreVideo.CVPixelBufferRef,
        timestampMs: Long,
    ): List<VNHumanBodyPose3DObservation>? {
        // VNDetectHumanBodyPose3DRequest requires VNImageRequestHandler
        // (not VNSequenceRequestHandler) for 3D estimation
        val handler = VNImageRequestHandler(pixelBuffer, options = emptyMap<Any?, Any>())
        try {
            handler.performRequests(listOf(bodyPose3DRequest), error = null)
        } catch (_: Exception) {
            _trackingData.tryEmit(BodyTrackingData.notTracking(timestampMs))
            return null
        }

        @Suppress("UNCHECKED_CAST")
        val observations = bodyPose3DRequest.results as? List<VNHumanBodyPose3DObservation>

        if (observations.isNullOrEmpty()) {
            _trackingData.tryEmit(BodyTrackingData.notTracking(timestampMs))
            return null
        }

        return observations.take(config.maxBodies)
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
        private const val TAG = "VisionBodyTracker3D"
        private const val TARGET_FPS = 15
    }
}
