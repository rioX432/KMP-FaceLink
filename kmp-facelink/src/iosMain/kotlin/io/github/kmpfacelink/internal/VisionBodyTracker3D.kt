@file:OptIn(
    kotlinx.cinterop.ExperimentalForeignApi::class,
    kotlinx.cinterop.BetaInteropApi::class,
)

package io.github.kmpfacelink.internal

import io.github.kmpfacelink.api.BodyTracker
import io.github.kmpfacelink.model.BodyTrackerConfig
import io.github.kmpfacelink.model.BodyTrackingData
import io.github.kmpfacelink.model.CameraFacing
import io.github.kmpfacelink.model.SmoothingConfig
import io.github.kmpfacelink.model.TrackedBody
import io.github.kmpfacelink.util.BodyLandmarkSmoother
import io.github.kmpfacelink.util.FaceLinkLogger
import io.github.kmpfacelink.util.FrameThrottler
import io.github.kmpfacelink.util.PowerModeManager
import io.github.kmpfacelink.util.createBodySmoother
import io.github.kmpfacelink.util.createSmoother
import io.github.kmpfacelink.util.withLock
import platform.AVFoundation.AVCaptureSessionPreset640x480
import platform.CoreMedia.CMSampleBufferGetImageBuffer
import platform.CoreMedia.CMSampleBufferRef
import platform.CoreVideo.CVPixelBufferGetHeight
import platform.CoreVideo.CVPixelBufferGetWidth
import platform.Vision.VNDetectHumanBodyPose3DRequest
import platform.Vision.VNHumanBodyPose3DObservation
import platform.Vision.VNImageRequestHandler
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
    sharedVisionSession: SharedVisionCaptureSession? = null,
) : BaseVisionTracker<BodyTrackingData>(
    sharedVisionSession = sharedVisionSession,
    thermalMonitor = IosThermalMonitor(),
    frameThrottler = FrameThrottler(config.thermalPolicy),
    initialCameraFacing = config.cameraFacing,
    // 3D pose needs higher resolution than 2D for depth estimation
    sessionPreset = AVCaptureSessionPreset640x480,
),
    BodyTracker {

    private var smoother: BodyLandmarkSmoother? = config.createSmoother()
    private val bodyPose3DRequest = VNDetectHumanBodyPose3DRequest()

    private val batteryMonitor = IosBatteryMonitor()
    private val powerModeManager: PowerModeManager? = config.powerMode?.let { mode ->
        PowerModeManager(
            powerMode = mode,
            thermalMonitor = thermalMonitor,
            batteryMonitor = batteryMonitor,
        )
    }

    override val tag: String = TAG
    override val videoQueueLabel: String = "io.github.kmpfacelink.body3d.video"

    override suspend fun start() {
        performStart()
    }

    override suspend fun stop() {
        performStop()
    }

    override fun release() {
        performRelease()
    }

    override fun performExtraRelease() {
        batteryMonitor.release()
        powerModeManager?.release()
    }

    override fun updateSmoothing(config: SmoothingConfig) {
        pipelineLock.withLock {
            checkNotReleased("update smoothing")
            smoother = config.createBodySmoother()
        }
    }

    override fun resetSmoothing() {
        pipelineLock.withLock {
            checkNotReleased("reset smoothing")
            smoother?.reset()
        }
    }

    override suspend fun switchCamera(facing: CameraFacing) {
        performSwitchCamera(facing)
    }

    override fun processFrameUnsafe(sampleBuffer: CMSampleBufferRef) {
        val pixelBuffer = CMSampleBufferGetImageBuffer(sampleBuffer) ?: return
        val timestampMs = currentTimeMillis()

        // Power mode or thermal throttling: skip frame if needed
        val currentThermalState = thermalMonitor.thermalState.value
        val shouldProcess = if (powerModeManager != null) {
            powerModeManager.shouldProcess(timestampMs)
        } else {
            frameThrottler.shouldProcess(timestampMs, currentThermalState)
        }
        if (!shouldProcess) return

        val imageWidth = CVPixelBufferGetWidth(pixelBuffer).toInt()
        val imageHeight = CVPixelBufferGetHeight(pixelBuffer).toInt()

        val observations = detectBodies3D(pixelBuffer, timestampMs) ?: return

        var bodies = observations.map { observation ->
            TrackedBody(landmarks = VisionBodyLandmarkMapper3D.mapLandmarks(observation))
        }

        val processedBodies = pipelineLock.withLock {
            if (isReleased()) return
            smoother?.let { bodies = it.smooth(bodies, timestampMs) }
            bodies
        }

        mutableTrackingData.tryEmit(
            BodyTrackingData(
                bodies = processedBodies,
                sourceImageWidth = imageWidth,
                sourceImageHeight = imageHeight,
                timestampMs = timestampMs,
                isTracking = true,
                thermalState = currentThermalState,
            ),
        )
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
        } catch (e: Exception) {
            FaceLinkLogger.w(TAG, "Body pose 3D detection failed", e)
            mutableTrackingData.tryEmit(BodyTrackingData.notTracking(timestampMs))
            return null
        }

        @Suppress("UNCHECKED_CAST")
        val observations = bodyPose3DRequest.results as? List<VNHumanBodyPose3DObservation>

        if (observations.isNullOrEmpty()) {
            mutableTrackingData.tryEmit(BodyTrackingData.notTracking(timestampMs))
            return null
        }

        return observations.take(config.maxBodies)
    }

    private companion object {
        private const val TAG = "VisionBodyTracker3D"
    }
}
