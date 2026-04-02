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
import io.github.kmpfacelink.util.FrameThrottler
import io.github.kmpfacelink.util.createBodySmoother
import io.github.kmpfacelink.util.withLock
import platform.CoreMedia.CMSampleBufferGetImageBuffer
import platform.CoreMedia.CMSampleBufferRef
import platform.CoreVideo.CVPixelBufferGetHeight
import platform.CoreVideo.CVPixelBufferGetWidth
import platform.Vision.VNDetectHumanBodyPoseRequest
import platform.Vision.VNHumanBodyPoseObservation
import platform.Vision.VNSequenceRequestHandler
import kotlin.concurrent.atomics.ExperimentalAtomicApi

@OptIn(ExperimentalAtomicApi::class)
internal class VisionBodyTracker(
    private val config: BodyTrackerConfig,
    sharedVisionSession: SharedVisionCaptureSession? = null,
) : BaseVisionTracker<BodyTrackingData>(
    sharedVisionSession = sharedVisionSession,
    thermalMonitor = IosThermalMonitor(),
    frameThrottler = FrameThrottler(config.thermalPolicy),
    initialCameraFacing = config.cameraFacing,
),
    BodyTracker {

    private var smoother: BodyLandmarkSmoother? = config.smoothingConfig.createBodySmoother()
    private val bodyPoseRequest = VNDetectHumanBodyPoseRequest()
    private val sequenceHandler = VNSequenceRequestHandler()

    override val tag: String = TAG
    override val videoQueueLabel: String = "io.github.kmpfacelink.body.video"

    override suspend fun start() {
        performStart()
    }

    override suspend fun stop() {
        performStop()
    }

    override fun release() {
        performRelease()
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

        // Thermal throttling: skip frame if needed
        val currentThermalState = thermalMonitor.thermalState.value
        if (!frameThrottler.shouldProcess(timestampMs, currentThermalState)) return

        // Get source image dimensions from pixel buffer
        val imageWidth = CVPixelBufferGetWidth(pixelBuffer).toInt()
        val imageHeight = CVPixelBufferGetHeight(pixelBuffer).toInt()

        val observations = detectBodies(pixelBuffer, timestampMs) ?: return

        var bodies = observations.map { observation ->
            TrackedBody(landmarks = VisionBodyLandmarkMapper.mapLandmarks(observation))
        }

        // Apply smoothing (protected by lock)
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

    private fun detectBodies(
        pixelBuffer: platform.CoreVideo.CVPixelBufferRef,
        timestampMs: Long,
    ): List<VNHumanBodyPoseObservation>? {
        try {
            sequenceHandler.performRequests(
                listOf(bodyPoseRequest),
                onCVPixelBuffer = pixelBuffer,
                error = null,
            )
        } catch (_: Exception) {
            mutableTrackingData.tryEmit(BodyTrackingData.notTracking(timestampMs))
            return null
        }

        @Suppress("UNCHECKED_CAST")
        val observations = bodyPoseRequest.results as? List<VNHumanBodyPoseObservation>

        if (observations.isNullOrEmpty()) {
            mutableTrackingData.tryEmit(BodyTrackingData.notTracking(timestampMs))
            return null
        }

        // Limit to maxBodies
        return observations.take(config.maxBodies)
    }

    private companion object {
        private const val TAG = "VisionBodyTracker"
    }
}
