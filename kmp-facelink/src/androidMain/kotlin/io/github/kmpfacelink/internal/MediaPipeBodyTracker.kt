package io.github.kmpfacelink.internal

import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.core.Delegate
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarker
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarkerResult
import io.github.kmpfacelink.api.BodyTracker
import io.github.kmpfacelink.api.PlatformContext
import io.github.kmpfacelink.model.BodyTrackerConfig
import io.github.kmpfacelink.model.BodyTrackingData
import io.github.kmpfacelink.model.CameraFacing
import io.github.kmpfacelink.model.SmoothingConfig
import io.github.kmpfacelink.model.TrackedBody
import io.github.kmpfacelink.util.BodyLandmarkSmoother
import io.github.kmpfacelink.util.FaceLinkLogger
import io.github.kmpfacelink.util.FrameThrottler
import io.github.kmpfacelink.util.createBodySmoother
import io.github.kmpfacelink.util.withLock
import kotlin.concurrent.atomics.ExperimentalAtomicApi

@OptIn(ExperimentalAtomicApi::class)
internal class MediaPipeBodyTracker(
    platformContext: PlatformContext,
    private val config: BodyTrackerConfig,
    sharedCameraSession: SharedCameraSession? = null,
) : BaseMediaPipeTracker<BodyTrackingData>(
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
    BodyTracker,
    PreviewableBodyTracker {

    private var smoother: BodyLandmarkSmoother? = config.smoothingConfig.createBodySmoother()
    private var poseLandmarker: PoseLandmarker? = null

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

    override fun initLandmarker() {
        poseLandmarker = DelegateResolver.createWithFallback(config.inferenceDelegate) { delegate ->
            createLandmarker(delegate)
        }
    }

    override fun closeLandmarker() {
        poseLandmarker?.close()
        poseLandmarker = null
    }

    override fun buildDetectionCallbacks(): DetectionCallbacks {
        val landmarker = poseLandmarker
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

    private fun createLandmarker(delegate: Delegate): PoseLandmarker {
        val baseOptions = BaseOptions.builder()
            .setModelAssetPath(MODEL_ASSET_PATH)
            .setDelegate(delegate)
            .build()

        val options = PoseLandmarker.PoseLandmarkerOptions.builder()
            .setBaseOptions(baseOptions)
            .setRunningMode(RunningMode.LIVE_STREAM)
            .setNumPoses(config.maxBodies)
            .setMinPoseDetectionConfidence(config.minDetectionConfidence)
            .setMinTrackingConfidence(config.minTrackingConfidence)
            .setMinPosePresenceConfidence(config.minPresenceConfidence)
            .setResultListener(::onPoseLandmarkerResult)
            .setErrorListener { error ->
                FaceLinkLogger.e(TAG, "PoseLandmarker error: ${error.message}", error)
            }
            .build()

        return PoseLandmarker.createFromOptions(platformContext.context, options)
    }

    private fun onPoseLandmarkerResult(
        result: PoseLandmarkerResult,
        @Suppress("UNUSED_PARAMETER") input: com.google.mediapipe.framework.image.MPImage,
    ) {
        cameraPipeline.returnBitmapAfterResult(result.timestampMs())

        val timestampMs = result.timestampMs()

        if (result.landmarks().isEmpty()) {
            mutableTrackingData.tryEmit(BodyTrackingData.notTracking(timestampMs))
            return
        }

        var bodies = mutableListOf<TrackedBody>()

        for (i in result.landmarks().indices) {
            val landmarks = BodyLandmarkMapper.mapLandmarks(result.landmarks()[i])
            bodies.add(TrackedBody(landmarks = landmarks))
        }

        // Apply smoothing (protected by lock)
        val processedBodies = pipelineLock.withLock {
            if (isReleased()) return
            smoother?.let { bodies = it.smooth(bodies, timestampMs).toMutableList() }
            bodies.toList()
        }

        val data = BodyTrackingData(
            bodies = processedBodies,
            sourceImageWidth = cameraPipeline.lastImageWidth,
            sourceImageHeight = cameraPipeline.lastImageHeight,
            timestampMs = timestampMs,
            isTracking = true,
            thermalState = thermalMonitor.thermalState.value,
        )

        mutableTrackingData.tryEmit(data)
    }

    companion object {
        private const val TAG = "MediaPipeBodyTracker"
        private const val MODEL_ASSET_PATH = "models/pose_landmarker_lite.task"
    }
}
