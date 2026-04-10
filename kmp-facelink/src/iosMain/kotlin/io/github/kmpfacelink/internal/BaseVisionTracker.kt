@file:OptIn(
    kotlinx.cinterop.ExperimentalForeignApi::class,
    kotlinx.cinterop.BetaInteropApi::class,
)

package io.github.kmpfacelink.internal

import io.github.kmpfacelink.api.TrackingState
import io.github.kmpfacelink.model.CameraFacing
import io.github.kmpfacelink.util.FaceLinkLogger
import io.github.kmpfacelink.util.FrameThrottler
import io.github.kmpfacelink.util.withLock
import platform.AVFoundation.AVCaptureConnection
import platform.AVFoundation.AVCaptureDevice
import platform.AVFoundation.AVCaptureDeviceInput
import platform.AVFoundation.AVCaptureDevicePositionBack
import platform.AVFoundation.AVCaptureDevicePositionFront
import platform.AVFoundation.AVCaptureOutput
import platform.AVFoundation.AVCaptureSession
import platform.AVFoundation.AVCaptureSessionPreset352x288
import platform.AVFoundation.AVCaptureVideoDataOutput
import platform.AVFoundation.AVCaptureVideoDataOutputSampleBufferDelegateProtocol
import platform.AVFoundation.AVCaptureVideoOrientationPortrait
import platform.AVFoundation.AVMediaTypeVideo
import platform.AVFoundation.position
import platform.CoreMedia.CMSampleBufferRef
import platform.CoreMedia.CMTimeMake
import platform.CoreVideo.kCVPixelBufferPixelFormatTypeKey
import platform.CoreVideo.kCVPixelFormatType_420YpCbCr8BiPlanarFullRange
import platform.Foundation.NSNumber
import platform.darwin.NSObject
import platform.darwin.dispatch_queue_create
import kotlin.concurrent.atomics.AtomicInt
import kotlin.concurrent.atomics.ExperimentalAtomicApi

/**
 * Base class for Vision framework-based iOS trackers that share AVCaptureSession
 * infrastructure, frame processing guards, and lifecycle patterns.
 *
 * Subclasses must implement:
 * - [processFrameUnsafe] to run Vision detection on each frame
 * - [performExtraRelease] for tracker-specific cleanup
 * - [tag] for logging
 * - [videoQueueLabel] for the dispatch queue name
 *
 * @param T The tracking data type
 */
@OptIn(ExperimentalAtomicApi::class)
internal abstract class BaseVisionTracker<T>(
    private val sharedVisionSession: SharedVisionCaptureSession?,
    thermalMonitor: IosThermalMonitor,
    frameThrottler: FrameThrottler,
    initialCameraFacing: CameraFacing,
    private val targetFps: Int = DEFAULT_TARGET_FPS,
    private val sessionPreset: String = requireNotNull(AVCaptureSessionPreset352x288) {
        "AVCaptureSessionPreset352x288 unavailable"
    },
) : BaseTracker<T>(
    thermalMonitor = thermalMonitor,
    frameThrottler = frameThrottler,
) {

    protected val processing = AtomicInt(0)
    protected var currentCameraFacing: CameraFacing = initialCameraFacing

    /**
     * The underlying AVCaptureSession, exposed for camera preview in the iOS sample app.
     * Only available after [start] has been called.
     */
    var captureSession: AVCaptureSession? = null
        private set

    // Lazy to avoid accessing abstract `videoQueueLabel` during superclass init
    private val videoOutputDelegate by lazy { VideoOutputDelegate() }
    private val videoQueue by lazy { dispatch_queue_create(videoQueueLabel, null) }

    /** Logging tag for this tracker. */
    protected abstract val tag: String

    /** Dispatch queue label, e.g. "io.github.kmpfacelink.hand.video". */
    protected abstract val videoQueueLabel: String

    /** Process a single video frame. Called inside the processing guard. */
    protected abstract fun processFrameUnsafe(sampleBuffer: CMSampleBufferRef)

    /** Perform tracker-specific cleanup during [release]. Called inside pipelineLock. */
    protected open fun performExtraRelease() {}

    internal suspend fun performStart() {
        pipelineLock.withLock {
            if (!guardStart()) return
        }

        try {
            startCaptureOrAttachSession()
            pipelineLock.withLock { transitionToTracking() }
        } catch (e: Exception) {
            setError(e.message ?: e.toString())
            throw e
        }
    }

    private fun startCaptureOrAttachSession() {
        if (sharedVisionSession != null) {
            sharedVisionSession.addFrameHandler(::processVideoFrame)
            return
        }
        if (captureSession == null) {
            setupCaptureSession()
        }
        captureSession?.startRunning()
    }

    internal suspend fun performStop() {
        pipelineLock.withLock {
            if (sharedVisionSession == null) captureSession?.stopRunning()
            mutableState.value = TrackingState.STOPPED
        }
    }

    internal fun performRelease() {
        pipelineLock.withLock {
            released.store(1)
            if (sharedVisionSession == null) {
                captureSession?.stopRunning()
                captureSession = null
            }
            thermalMonitor.release()
            performExtraRelease()
            mutableState.value = TrackingState.RELEASED
        }
    }

    internal suspend fun performSwitchCamera(facing: CameraFacing) {
        pipelineLock.withLock { checkNotReleased("switch camera") }
        if (currentCameraFacing == facing) return
        val wasTracking = mutableState.value == TrackingState.TRACKING
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

    private fun processVideoFrame(sampleBuffer: CMSampleBufferRef?) {
        if (sampleBuffer == null || mutableState.value != TrackingState.TRACKING) return

        // Skip frame if previous one is still being processed
        if (!processing.compareAndSet(expectedValue = 0, newValue = 1)) return

        try {
            processFrameUnsafe(sampleBuffer)
        } finally {
            processing.store(0)
        }
    }

    private fun setupCaptureSession() {
        val session = AVCaptureSession()
        session.sessionPreset = sessionPreset

        val device = findCameraDevice(currentCameraFacing)
        configureCameraFrameRate(device)
        addCameraInput(session, device)
        addVideoOutput(session)

        captureSession = session
    }

    private fun findCameraDevice(facing: CameraFacing): AVCaptureDevice {
        val position = when (facing) {
            CameraFacing.FRONT -> AVCaptureDevicePositionFront
            CameraFacing.BACK -> AVCaptureDevicePositionBack
        }
        return AVCaptureDevice.devicesWithMediaType(AVMediaTypeVideo)
            .filterIsInstance<AVCaptureDevice>()
            .firstOrNull { it.position == position }
            ?: AVCaptureDevice.defaultDeviceWithMediaType(AVMediaTypeVideo)
            ?: throw UnsupportedOperationException("No camera available")
    }

    private fun addCameraInput(session: AVCaptureSession, device: AVCaptureDevice) {
        val input = AVCaptureDeviceInput.deviceInputWithDevice(device, null)
            ?: throw UnsupportedOperationException("Cannot create camera input")
        if (session.canAddInput(input)) {
            session.addInput(input)
        }
    }

    private fun addVideoOutput(session: AVCaptureSession) {
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
    }

    private fun configureCameraFrameRate(device: AVCaptureDevice) {
        val locked = try {
            device.lockForConfiguration(null)
            true
        } catch (e: Exception) {
            FaceLinkLogger.w(tag, "failed to lock device for frame rate configuration — $e")
            false
        }
        if (!locked) return

        val frameDuration = CMTimeMake(value = 1, timescale = targetFps)
        device.setActiveVideoMinFrameDuration(frameDuration)
        device.setActiveVideoMaxFrameDuration(frameDuration)
        device.unlockForConfiguration()
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

    internal companion object {
        private const val DEFAULT_TARGET_FPS = 15
    }
}
