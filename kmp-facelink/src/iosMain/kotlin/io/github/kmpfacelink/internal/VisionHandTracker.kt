@file:OptIn(
    kotlinx.cinterop.ExperimentalForeignApi::class,
    kotlinx.cinterop.BetaInteropApi::class,
)

package io.github.kmpfacelink.internal

import io.github.kmpfacelink.api.HandTracker
import io.github.kmpfacelink.model.CameraFacing
import io.github.kmpfacelink.model.HandGesture
import io.github.kmpfacelink.model.HandPoseRevision
import io.github.kmpfacelink.model.HandTrackerConfig
import io.github.kmpfacelink.model.HandTrackingData
import io.github.kmpfacelink.model.SmoothingConfig
import io.github.kmpfacelink.model.TrackedHand
import io.github.kmpfacelink.util.FaceLinkLogger
import io.github.kmpfacelink.util.FrameThrottler
import io.github.kmpfacelink.util.GestureClassifier
import io.github.kmpfacelink.util.HandLandmarkSmoother
import io.github.kmpfacelink.util.createHandSmoother
import io.github.kmpfacelink.util.withLock
import kotlinx.cinterop.useContents
import platform.CoreMedia.CMSampleBufferGetImageBuffer
import platform.CoreMedia.CMSampleBufferRef
import platform.CoreVideo.CVPixelBufferGetHeight
import platform.CoreVideo.CVPixelBufferGetWidth
import platform.Foundation.NSProcessInfo
import platform.Vision.VNDetectHumanHandPoseRequest
import platform.Vision.VNHumanHandPoseObservation
import platform.Vision.VNSequenceRequestHandler
import kotlin.concurrent.atomics.ExperimentalAtomicApi

@OptIn(ExperimentalAtomicApi::class)
internal class VisionHandTracker(
    private val config: HandTrackerConfig,
    sharedVisionSession: SharedVisionCaptureSession? = null,
) : BaseVisionTracker<HandTrackingData>(
    sharedVisionSession = sharedVisionSession,
    thermalMonitor = IosThermalMonitor(),
    frameThrottler = FrameThrottler(config.thermalPolicy),
    initialCameraFacing = config.cameraFacing,
),
    HandTracker {

    private var smoother: HandLandmarkSmoother? = config.smoothingConfig.createHandSmoother()

    private val handPoseRequest = VNDetectHumanHandPoseRequest().apply {
        @Suppress("CAST_NEVER_SUCCEEDS")
        maximumHandCount = config.maxHands.toULong()
        revision = resolveHandPoseRevision(config.handPoseRevision)
    }
    private val sequenceHandler = VNSequenceRequestHandler()

    override val tag: String = TAG
    override val videoQueueLabel: String = "io.github.kmpfacelink.hand.video"

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
            smoother = config.createHandSmoother()
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

        val observations = detectHands(pixelBuffer, timestampMs) ?: return

        var hands = observations.map { observation -> mapObservation(observation) }

        // Apply smoothing (protected by lock)
        val processedHands = pipelineLock.withLock {
            if (isReleased()) return
            smoother?.let { hands = it.smooth(hands, timestampMs) }
            hands
        }

        mutableTrackingData.tryEmit(
            HandTrackingData(
                hands = processedHands,
                sourceImageWidth = imageWidth,
                sourceImageHeight = imageHeight,
                timestampMs = timestampMs,
                isTracking = true,
                thermalState = currentThermalState,
            ),
        )
    }

    private fun detectHands(
        pixelBuffer: platform.CoreVideo.CVPixelBufferRef,
        timestampMs: Long,
    ): List<VNHumanHandPoseObservation>? {
        // Use VNSequenceRequestHandler to avoid per-frame handler allocation overhead
        try {
            sequenceHandler.performRequests(listOf(handPoseRequest), onCVPixelBuffer = pixelBuffer, error = null)
        } catch (_: Exception) {
            mutableTrackingData.tryEmit(HandTrackingData.notTracking(timestampMs))
            return null
        }

        @Suppress("UNCHECKED_CAST")
        val observations = handPoseRequest.results as? List<VNHumanHandPoseObservation>

        if (observations.isNullOrEmpty()) {
            mutableTrackingData.tryEmit(HandTrackingData.notTracking(timestampMs))
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

    private companion object {
        private const val TAG = "VisionHandTracker"

        /** Original hand pose model (iOS 14+). */
        private const val REVISION_V1 = 1UL

        /** New hand pose model (iOS 26+): smaller, more accurate, lower latency. */
        private const val REVISION_V2 = 2UL

        /** iOS 26 is the first version to ship the revision 2 hand pose model. */
        private const val IOS_26_MAJOR_VERSION = 26L

        /**
         * Resolve the [HandPoseRevision] config to a concrete Vision framework revision number.
         *
         * - [HandPoseRevision.AUTO]: revision 2 on iOS 26+, revision 1 otherwise.
         * - [HandPoseRevision.V1]: always revision 1.
         * - [HandPoseRevision.V2]: revision 2 on iOS 26+, falls back to revision 1 on older iOS.
         */
        private fun resolveHandPoseRevision(preference: HandPoseRevision): ULong {
            val isIos26OrLater = NSProcessInfo.processInfo.operatingSystemVersion()
                .useContents { majorVersion >= IOS_26_MAJOR_VERSION }
            return when (preference) {
                HandPoseRevision.AUTO -> if (isIos26OrLater) REVISION_V2 else REVISION_V1
                HandPoseRevision.V1 -> REVISION_V1
                HandPoseRevision.V2 -> if (isIos26OrLater) {
                    REVISION_V2
                } else {
                    FaceLinkLogger.w(
                        TAG,
                        "Hand pose revision 2 requested but requires iOS 26+; falling back to revision 1",
                    )
                    REVISION_V1
                }
            }
        }
    }
}
