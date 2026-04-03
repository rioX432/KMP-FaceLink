package io.github.kmpfacelink.internal

import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.core.Delegate
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarker
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarkerResult
import io.github.kmpfacelink.api.HandTracker
import io.github.kmpfacelink.api.PlatformContext
import io.github.kmpfacelink.model.CameraFacing
import io.github.kmpfacelink.model.HandTrackerConfig
import io.github.kmpfacelink.model.HandTrackingData
import io.github.kmpfacelink.model.SmoothingConfig
import io.github.kmpfacelink.model.TrackedHand
import io.github.kmpfacelink.util.FaceLinkLogger
import io.github.kmpfacelink.util.FrameThrottler
import io.github.kmpfacelink.util.GestureClassifier
import io.github.kmpfacelink.util.HandLandmarkSmoother
import io.github.kmpfacelink.util.createHandSmoother
import io.github.kmpfacelink.util.createSmoother
import io.github.kmpfacelink.util.withLock
import kotlin.concurrent.atomics.ExperimentalAtomicApi

/**
 * Hand tracker implementation using MediaPipe tasks-vision SDK (v0.10.32).
 * Version pinned for documentation parity with [VisionHandTracker.HAND_POSE_REVISION] on iOS.
 * Update when upgrading the mediapipe dependency in libs.versions.toml.
 */
@Suppress("TooManyFunctions")
@OptIn(ExperimentalAtomicApi::class)
internal class MediaPipeHandTracker(
    platformContext: PlatformContext,
    private val config: HandTrackerConfig,
    sharedCameraSession: SharedCameraSession? = null,
) : BaseMediaPipeTracker<HandTrackingData>(
    params = MediaPipeTrackerParams(
        platformContext = platformContext,
        sharedCameraSession = sharedCameraSession,
        analysisWidth = config.analysisWidth,
        analysisHeight = config.analysisHeight,
        initialCameraFacing = config.cameraFacing,
    ),
    thermalMonitor = AndroidThermalMonitor(platformContext.context),
    frameThrottler = FrameThrottler(config.thermalPolicy),
),
    HandTracker,
    PreviewableHandTracker {

    private var smoother: HandLandmarkSmoother? = config.createSmoother()
    private var handLandmarker: HandLandmarker? = null

    override val tag: String = TAG

    override fun setSurfaceProvider(surfaceProvider: Preview.SurfaceProvider?) {
        super.setSurfaceProvider(surfaceProvider)
    }

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

    override fun initLandmarker() {
        handLandmarker = DelegateResolver.createWithFallback(config.inferenceDelegate) { delegate ->
            createLandmarker(delegate)
        }
    }

    override fun closeLandmarker() {
        handLandmarker?.close()
        handLandmarker = null
    }

    override fun buildDetectionCallbacks(): DetectionCallbacks {
        val landmarker = handLandmarker
        return DetectionCallbacks(
            isLandmarkerReady = landmarker != null,
            shouldProcess = { ts -> frameThrottler.shouldProcess(ts, thermalMonitor.thermalState.value) },
            detectAsync = { mpImage, ts -> landmarker!!.detectAsync(mpImage, ts) },
        )
    }

    @Suppress("UNUSED_PARAMETER") // SharedFrameHandler signature
    override fun onSharedFrameReceived(
        bitmap: android.graphics.Bitmap,
        width: Int,
        height: Int,
        timestampMs: Long,
    ) {
        delegateSharedFrame(bitmap, width, height, timestampMs)
    }

    override fun onImageProxyReceived(imageProxy: ImageProxy) {
        delegateImageProxy(imageProxy)
    }

    private fun createLandmarker(delegate: Delegate): HandLandmarker {
        val baseOptions = BaseOptions.builder()
            .setModelAssetPath(MODEL_ASSET_PATH)
            .setDelegate(delegate)
            .build()

        val options = HandLandmarker.HandLandmarkerOptions.builder()
            .setBaseOptions(baseOptions)
            .setRunningMode(RunningMode.LIVE_STREAM)
            .setNumHands(config.maxHands)
            .setMinHandDetectionConfidence(config.minDetectionConfidence)
            .setMinTrackingConfidence(config.minTrackingConfidence)
            .setMinHandPresenceConfidence(config.minPresenceConfidence)
            .setResultListener(::onHandLandmarkerResult)
            .setErrorListener { error ->
                FaceLinkLogger.e(TAG, "HandLandmarker error: ${error.message}", error)
            }
            .build()

        return HandLandmarker.createFromOptions(platformContext.context, options)
    }

    private fun onHandLandmarkerResult(
        result: HandLandmarkerResult,
        @Suppress("UNUSED_PARAMETER") input: com.google.mediapipe.framework.image.MPImage,
    ) {
        cameraPipeline.returnBitmapAfterResult(result.timestampMs())

        val timestampMs = result.timestampMs()

        if (result.landmarks().isEmpty()) {
            mutableTrackingData.tryEmit(HandTrackingData.notTracking(timestampMs))
            return
        }

        var hands = buildTrackedHands(result)

        // Apply smoothing (protected by lock)
        val processedHands = pipelineLock.withLock {
            if (isReleased()) return
            smoother?.let { hands = it.smooth(hands, timestampMs).toMutableList() }
            hands.toList()
        }

        val data = HandTrackingData(
            hands = processedHands,
            sourceImageWidth = cameraPipeline.lastImageWidth,
            sourceImageHeight = cameraPipeline.lastImageHeight,
            timestampMs = timestampMs,
            isTracking = true,
            thermalState = thermalMonitor.thermalState.value,
        )

        mutableTrackingData.tryEmit(data)
    }

    private fun buildTrackedHands(result: HandLandmarkerResult): MutableList<TrackedHand> {
        val hands = mutableListOf<TrackedHand>()
        for (i in result.landmarks().indices) {
            val landmarks = HandLandmarkMapper.mapLandmarks(result.landmarks()[i])

            val handedness = if (result.handednesses().isNotEmpty() && i < result.handednesses().size) {
                val category = result.handednesses()[i]
                if (category.isNotEmpty()) {
                    HandLandmarkMapper.mapHandedness(category[0].categoryName(), isFrontCamera)
                } else {
                    io.github.kmpfacelink.model.Handedness.UNKNOWN
                }
            } else {
                io.github.kmpfacelink.model.Handedness.UNKNOWN
            }

            val (gesture, confidence) = if (config.enableGestureRecognition) {
                GestureClassifier.classify(landmarks)
            } else {
                io.github.kmpfacelink.model.HandGesture.NONE to 0f
            }

            hands.add(
                TrackedHand(
                    handedness = handedness,
                    landmarks = landmarks,
                    gesture = gesture,
                    gestureConfidence = confidence,
                ),
            )
        }
        return hands
    }

    companion object {
        private const val TAG = "MediaPipeHandTracker"
        private const val MODEL_ASSET_PATH = "models/hand_landmarker.task"
    }
}
