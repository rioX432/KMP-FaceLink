@file:OptIn(
    kotlinx.cinterop.ExperimentalForeignApi::class,
    kotlinx.cinterop.BetaInteropApi::class,
)

package io.github.kmpfacelink.internal

import io.github.kmpfacelink.model.CameraFacing
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

/**
 * Shared AVCaptureSession that distributes video frames to multiple
 * Vision-based trackers (hand + body). Used by [CompositeHolisticTracker]
 * so that hand and body tracking share a single RGB camera session.
 */
internal class SharedVisionCaptureSession(
    private val cameraFacing: CameraFacing,
) {
    private val frameHandlers = mutableListOf<(CMSampleBufferRef?) -> Unit>()

    var captureSession: AVCaptureSession? = null
        private set

    private val videoOutputDelegate = SharedVideoOutputDelegate()
    private val videoQueue = dispatch_queue_create("io.github.kmpfacelink.holistic.video", null)

    fun addFrameHandler(handler: (CMSampleBufferRef?) -> Unit) {
        frameHandlers.add(handler)
    }

    @Suppress("LongMethod")
    fun start() {
        if (captureSession != null) {
            captureSession?.startRunning()
            return
        }

        val session = AVCaptureSession()
        session.sessionPreset = AVCaptureSessionPreset352x288

        val position = when (cameraFacing) {
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
        session.startRunning()
    }

    fun stop() {
        captureSession?.stopRunning()
    }

    fun release() {
        stop()
        captureSession = null
        frameHandlers.clear()
    }

    private fun configureCameraFrameRate(device: AVCaptureDevice) {
        @Suppress("SwallowedException")
        val locked = try {
            device.lockForConfiguration(null)
            true
        } catch (_: Exception) {
            false
        }
        if (!locked) return

        val frameDuration = CMTimeMake(value = 1, timescale = TARGET_FPS)
        device.setActiveVideoMinFrameDuration(frameDuration)
        device.setActiveVideoMaxFrameDuration(frameDuration)
        device.unlockForConfiguration()
    }

    private inner class SharedVideoOutputDelegate :
        NSObject(),
        AVCaptureVideoDataOutputSampleBufferDelegateProtocol {
        override fun captureOutput(
            output: AVCaptureOutput,
            didOutputSampleBuffer: CMSampleBufferRef?,
            fromConnection: AVCaptureConnection,
        ) {
            for (handler in frameHandlers) {
                handler(didOutputSampleBuffer)
            }
        }
    }

    private companion object {
        private const val TARGET_FPS = 15
    }
}
