package io.github.kmpfacelink.internal

import android.os.SystemClock
import android.util.Log
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.core.Delegate
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarker
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarkerResult
import io.github.kmpfacelink.api.BodyTracker
import io.github.kmpfacelink.api.PlatformContext
import io.github.kmpfacelink.api.TrackingState
import io.github.kmpfacelink.model.BodyTrackerConfig
import io.github.kmpfacelink.model.BodyTrackingData
import io.github.kmpfacelink.model.CameraFacing
import io.github.kmpfacelink.model.SmoothingConfig
import io.github.kmpfacelink.model.TrackedBody
import io.github.kmpfacelink.util.BodyLandmarkSmoother
import io.github.kmpfacelink.util.FrameThrottler
import io.github.kmpfacelink.util.createBodySmoother
import io.github.kmpfacelink.util.withLock
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicReference
import kotlin.concurrent.atomics.ExperimentalAtomicApi

@OptIn(ExperimentalAtomicApi::class)
internal class MediaPipeBodyTracker(
    private val platformContext: PlatformContext,
    private val config: BodyTrackerConfig,
    private val sharedCameraSession: SharedCameraSession? = null,
) : BaseTracker<BodyTrackingData>(
    thermalMonitor = AndroidThermalMonitor(platformContext.context),
    frameThrottler = FrameThrottler(config.thermalPolicy),
),
    BodyTracker,
    PreviewableBodyTracker {

    override val trackingData: Flow<BodyTrackingData> = super.trackingData
    override val state: StateFlow<TrackingState> = super.state
    override val errorMessage: StateFlow<String?> = super.errorMessage

    private var smoother: BodyLandmarkSmoother? = config.smoothingConfig.createBodySmoother()

    @Volatile
    internal var currentCameraFacing: CameraFacing = config.cameraFacing
    private val isFrontCamera: Boolean
        get() = currentCameraFacing == CameraFacing.FRONT
    private val analysisExecutor = Executors.newSingleThreadExecutor()
    private val imageConverter = ImageProxyConverter()
    private val cameraManager = if (sharedCameraSession == null) CameraXManager(platformContext) else null

    private var poseLandmarker: PoseLandmarker? = null
    private var previewSurfaceProvider: Preview.SurfaceProvider? = null

    private val inFlightBitmap = AtomicReference<android.graphics.Bitmap?>(null)

    @Volatile
    private var lastImageWidth: Int = 0

    @Volatile
    private var lastImageHeight: Int = 0

    override fun setSurfaceProvider(surfaceProvider: Preview.SurfaceProvider?) {
        previewSurfaceProvider = surfaceProvider
    }

    override suspend fun start() {
        pipelineLock.withLock {
            if (!guardStart()) return
        }

        try {
            initPoseLandmarker()
            startCamera()
            pipelineLock.withLock { transitionToTracking() }
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            // MediaPipe model loading and CameraX binding can throw various RuntimeException
            // subclasses; catch broadly to set error state before rethrowing
            Log.e(TAG, "Failed to start body tracking", e)
            setError(e.message ?: e.toString())
            throw e
        }
    }

    override suspend fun stop() {
        pipelineLock.withLock {
            cameraManager?.unbindAll()
            poseLandmarker?.close()
            poseLandmarker = null
            mutableState.value = TrackingState.STOPPED
        }
    }

    override fun release() {
        pipelineLock.withLock {
            released.store(1)
            cameraManager?.unbindAll()
            poseLandmarker?.close()
            poseLandmarker = null
            imageConverter.release()
            thermalMonitor.release()
            analysisExecutor.shutdown()
            mutableState.value = TrackingState.RELEASED
        }
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
        pipelineLock.withLock { checkNotReleased("switch camera") }
        if (currentCameraFacing == facing) return
        val wasTracking = mutableState.value == TrackingState.TRACKING
        if (wasTracking) stop()
        currentCameraFacing = facing
        if (wasTracking) start()
    }

    private fun initPoseLandmarker() {
        // GPU delegate can fail with RuntimeException on devices lacking OpenGL ES 3.1+
        // or when the GPU driver rejects the model. Fall back to CPU in that case.
        poseLandmarker = try {
            createLandmarker(Delegate.GPU)
        } catch (@Suppress("TooGenericExceptionCaught") e: RuntimeException) {
            Log.w(TAG, "GPU delegate unavailable, falling back to CPU", e)
            createLandmarker(Delegate.CPU)
        }
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
                Log.e(TAG, "PoseLandmarker error: ${error.message}", error)
            }
            .build()

        return PoseLandmarker.createFromOptions(platformContext.context, options)
    }

    private suspend fun startCamera() {
        if (sharedCameraSession != null) {
            sharedCameraSession.addFrameHandler(::processSharedFrame)
        } else {
            val imageAnalysis = ImageAnalysis.Builder()
                .setResolutionSelector(
                    CameraXManager.buildAnalysisResolutionSelector(config.analysisWidth, config.analysisHeight),
                )
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
                .build()
                .also { it.setAnalyzer(analysisExecutor, ::processFrame) }

            cameraManager!!.startCamera(
                cameraFacing = currentCameraFacing,
                imageAnalysis = imageAnalysis,
                surfaceProvider = previewSurfaceProvider,
            )
        }
    }

    @Suppress("UNUSED_PARAMETER") // SharedFrameHandler signature
    private fun processSharedFrame(bitmap: android.graphics.Bitmap, width: Int, height: Int, timestampMs: Long) {
        val landmarker = poseLandmarker
        if (landmarker == null || mutableState.value != TrackingState.TRACKING) {
            sharedCameraSession?.returnInFlightBitmap(timestampMs)
            return
        }

        // Thermal throttling: skip frame if needed
        val currentThermalState = thermalMonitor.thermalState.value
        if (!frameThrottler.shouldProcess(timestampMs, currentThermalState)) {
            sharedCameraSession?.returnInFlightBitmap(timestampMs)
            return
        }

        lastImageWidth = width
        lastImageHeight = height

        val mpImage = BitmapImageBuilder(bitmap).build()
        try {
            landmarker.detectAsync(mpImage, timestampMs)
        } catch (e: Exception) {
            // MediaPipe may throw IllegalArgumentException (non-monotonic timestamp) or
            // MediaPipeException on internal errors; must not crash the frame processing loop
            Log.w(TAG, "detectAsync failed", e)
            sharedCameraSession?.returnInFlightBitmap(timestampMs)
        }
    }

    private fun processFrame(imageProxy: ImageProxy) {
        val landmarker = poseLandmarker
        if (landmarker == null || mutableState.value != TrackingState.TRACKING) {
            imageProxy.close()
            return
        }

        // Thermal throttling: skip frame if needed
        val currentThermalState = thermalMonitor.thermalState.value
        if (!frameThrottler.shouldProcess(SystemClock.elapsedRealtime(), currentThermalState)) {
            imageProxy.close()
            return
        }

        val bitmap = imageConverter.convert(imageProxy, mirrorHorizontally = isFrontCamera)
        if (bitmap == null) {
            imageProxy.close()
            return
        }

        lastImageWidth = bitmap.width
        lastImageHeight = bitmap.height

        val mpImage = BitmapImageBuilder(bitmap).build()
        val timestampMs = SystemClock.elapsedRealtime()

        inFlightBitmap.set(bitmap)

        try {
            landmarker.detectAsync(mpImage, timestampMs)
        } catch (e: Exception) {
            // MediaPipe may throw IllegalArgumentException (non-monotonic timestamp) or
            // MediaPipeException on internal errors; must not crash the frame processing loop
            Log.w(TAG, "detectAsync failed", e)
            if (inFlightBitmap.compareAndSet(bitmap, null)) {
                imageConverter.returnBitmap(bitmap)
            }
        }

        imageProxy.close()
    }

    private fun onPoseLandmarkerResult(
        result: PoseLandmarkerResult,
        @Suppress("UNUSED_PARAMETER") input: com.google.mediapipe.framework.image.MPImage,
    ) {
        if (sharedCameraSession != null) {
            sharedCameraSession.returnInFlightBitmap(result.timestampMs())
        } else {
            inFlightBitmap.getAndSet(null)?.let { imageConverter.returnBitmap(it) }
        }

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
            sourceImageWidth = lastImageWidth,
            sourceImageHeight = lastImageHeight,
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
