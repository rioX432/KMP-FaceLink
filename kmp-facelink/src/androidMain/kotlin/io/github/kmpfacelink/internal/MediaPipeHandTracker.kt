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
import io.github.kmpfacelink.api.TrackingState
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
import io.github.kmpfacelink.util.withLock
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import java.util.concurrent.Executors
import kotlin.concurrent.atomics.ExperimentalAtomicApi

/**
 * Hand tracker implementation using MediaPipe tasks-vision SDK (v0.10.32).
 * Version pinned for documentation parity with [VisionHandTracker.HAND_POSE_REVISION] on iOS.
 * Update when upgrading the mediapipe dependency in libs.versions.toml.
 */
@OptIn(ExperimentalAtomicApi::class)
internal class MediaPipeHandTracker(
    private val platformContext: PlatformContext,
    private val config: HandTrackerConfig,
    private val sharedCameraSession: SharedCameraSession? = null,
) : BaseTracker<HandTrackingData>(
    thermalMonitor = AndroidThermalMonitor(platformContext.context),
    frameThrottler = FrameThrottler(config.thermalPolicy),
),
    HandTracker,
    PreviewableHandTracker {

    override val trackingData: Flow<HandTrackingData> = super.trackingData
    override val state: StateFlow<TrackingState> = super.state
    override val errorMessage: StateFlow<String?> = super.errorMessage

    private var smoother: HandLandmarkSmoother? = config.smoothingConfig.createHandSmoother()

    @Volatile
    internal var currentCameraFacing: CameraFacing = config.cameraFacing
    private val isFrontCamera: Boolean
        get() = currentCameraFacing == CameraFacing.FRONT
    private val analysisExecutor = Executors.newSingleThreadExecutor()
    private val imageConverter = ImageProxyConverter()
    private val cameraManager = if (sharedCameraSession == null) CameraXManager(platformContext) else null
    private val cameraPipeline = CameraPipelineHelper(
        tag = TAG,
        sharedCameraSession = sharedCameraSession,
        cameraManager = cameraManager,
        imageConverter = imageConverter,
        analysisExecutor = analysisExecutor,
    )

    private var handLandmarker: HandLandmarker? = null
    private var previewSurfaceProvider: Preview.SurfaceProvider? = null

    override fun setSurfaceProvider(surfaceProvider: Preview.SurfaceProvider?) {
        previewSurfaceProvider = surfaceProvider
    }

    override suspend fun start() {
        pipelineLock.withLock {
            if (!guardStart()) return
        }

        try {
            initHandLandmarker()
            startCamera()
            pipelineLock.withLock { transitionToTracking() }
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            // MediaPipe model loading and CameraX binding can throw various RuntimeException
            // subclasses; catch broadly to set error state before rethrowing
            FaceLinkLogger.e(TAG, "Failed to start hand tracking", e)
            setError(e.message ?: e.toString())
            throw e
        }
    }

    override suspend fun stop() {
        pipelineLock.withLock {
            cameraManager?.unbindAll()
            handLandmarker?.close()
            handLandmarker = null
            mutableState.value = TrackingState.STOPPED
        }
    }

    override fun release() {
        pipelineLock.withLock {
            released.store(1)
            cameraManager?.unbindAll()
            handLandmarker?.close()
            handLandmarker = null
            imageConverter.release()
            thermalMonitor.release()
            analysisExecutor.shutdown()
            mutableState.value = TrackingState.RELEASED
        }
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
        pipelineLock.withLock { checkNotReleased("switch camera") }
        if (currentCameraFacing == facing) return
        val wasTracking = mutableState.value == TrackingState.TRACKING
        if (wasTracking) stop()
        currentCameraFacing = facing
        if (wasTracking) start()
    }

    private fun initHandLandmarker() {
        handLandmarker = DelegateResolver.createWithFallback(config.inferenceDelegate) { delegate ->
            createLandmarker(delegate)
        }
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

    private suspend fun startCamera() {
        cameraPipeline.startCamera(
            config = CameraStartConfig(
                cameraFacing = currentCameraFacing,
                analysisWidth = config.analysisWidth,
                analysisHeight = config.analysisHeight,
                surfaceProvider = previewSurfaceProvider,
            ),
            onSharedFrame = ::processSharedFrame,
            onImageProxy = ::processFrame,
        )
    }

    @Suppress("UNUSED_PARAMETER") // SharedFrameHandler signature
    private fun processSharedFrame(bitmap: android.graphics.Bitmap, width: Int, height: Int, timestampMs: Long) {
        val landmarker = handLandmarker
        cameraPipeline.processSharedFrame(
            bitmap = bitmap,
            width = width,
            height = height,
            timestampMs = timestampMs,
            context = SharedFrameContext(mutableState.value, buildDetectionCallbacks(landmarker)),
        )
    }

    private fun processFrame(imageProxy: ImageProxy) {
        val landmarker = handLandmarker
        cameraPipeline.processFrame(
            imageProxy = imageProxy,
            trackingState = mutableState.value,
            isFrontCamera = isFrontCamera,
            callbacks = buildDetectionCallbacks(landmarker),
        )
    }

    private fun buildDetectionCallbacks(landmarker: HandLandmarker?): DetectionCallbacks =
        DetectionCallbacks(
            isLandmarkerReady = landmarker != null,
            shouldProcess = { ts -> frameThrottler.shouldProcess(ts, thermalMonitor.thermalState.value) },
            detectAsync = { mpImage, ts -> landmarker!!.detectAsync(mpImage, ts) },
        )

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

        var hands = mutableListOf<TrackedHand>()

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

    companion object {
        private const val TAG = "MediaPipeHandTracker"
        private const val MODEL_ASSET_PATH = "models/hand_landmarker.task"
    }
}
