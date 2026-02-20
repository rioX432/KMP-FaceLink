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
import io.github.kmpfacelink.util.PlatformLock
import io.github.kmpfacelink.util.createBodySmoother
import io.github.kmpfacelink.util.withLock
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.Executors
import kotlin.concurrent.atomics.AtomicInt
import kotlin.concurrent.atomics.ExperimentalAtomicApi

@OptIn(ExperimentalAtomicApi::class)
internal class MediaPipeBodyTracker(
    private val platformContext: PlatformContext,
    private val config: BodyTrackerConfig,
) : BodyTracker, PreviewableBodyTracker {

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

    private var smoother: BodyLandmarkSmoother? = config.smoothingConfig.createBodySmoother()
    private val isFrontCamera = config.cameraFacing == CameraFacing.FRONT
    private val analysisExecutor = Executors.newSingleThreadExecutor()
    private val imageConverter = ImageProxyConverter()
    private val cameraManager = CameraXManager(platformContext)

    private var poseLandmarker: PoseLandmarker? = null
    private var previewSurfaceProvider: Preview.SurfaceProvider? = null

    @Volatile
    private var lastImageWidth: Int = 0

    @Volatile
    private var lastImageHeight: Int = 0

    override fun setSurfaceProvider(surfaceProvider: Preview.SurfaceProvider) {
        previewSurfaceProvider = surfaceProvider
    }

    override suspend fun start() {
        pipelineLock.withLock {
            check(released.load() == 0) { "Cannot start a released tracker" }
            if (_state.value == TrackingState.TRACKING || _state.value == TrackingState.STARTING) return
            _state.value = TrackingState.STARTING
        }

        try {
            initPoseLandmarker()
            startCamera()
            _state.value = TrackingState.TRACKING
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start body tracking", e)
            _errorMessage.value = e.message ?: e.toString()
            _state.value = TrackingState.ERROR
            throw e
        }
    }

    override suspend fun stop() {
        pipelineLock.withLock {
            cameraManager.unbindAll()
            poseLandmarker?.close()
            poseLandmarker = null
            _state.value = TrackingState.STOPPED
        }
    }

    override fun release() {
        released.store(1)
        pipelineLock.withLock {
            cameraManager.unbindAll()
            poseLandmarker?.close()
            poseLandmarker = null
            imageConverter.release()
            analysisExecutor.shutdown()
            _state.value = TrackingState.RELEASED
        }
    }

    override fun updateSmoothing(config: SmoothingConfig) {
        pipelineLock.withLock {
            check(released.load() == 0) { "Cannot update smoothing on a released tracker" }
            smoother = config.createBodySmoother()
        }
    }

    @Suppress("TooGenericExceptionCaught") // MediaPipe GPU delegate throws RuntimeException
    private fun initPoseLandmarker() {
        poseLandmarker = try {
            createLandmarker(Delegate.GPU)
        } catch (e: RuntimeException) {
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
            .setMinPoseDetectionConfidence(CONFIDENCE_THRESHOLD)
            .setMinTrackingConfidence(CONFIDENCE_THRESHOLD)
            .setMinPosePresenceConfidence(CONFIDENCE_THRESHOLD)
            .setResultListener(::onPoseLandmarkerResult)
            .setErrorListener { error ->
                Log.e(TAG, "PoseLandmarker error: ${error.message}", error)
            }
            .build()

        return PoseLandmarker.createFromOptions(platformContext.context, options)
    }

    private suspend fun startCamera() {
        val imageAnalysis = ImageAnalysis.Builder()
            .setResolutionSelector(CameraXManager.buildAnalysisResolutionSelector())
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
            .build()
            .also { it.setAnalyzer(analysisExecutor, ::processFrame) }

        cameraManager.startCamera(
            cameraFacing = config.cameraFacing,
            imageAnalysis = imageAnalysis,
            surfaceProvider = previewSurfaceProvider,
        )
    }

    private fun processFrame(imageProxy: ImageProxy) {
        val landmarker = poseLandmarker
        if (landmarker == null || _state.value != TrackingState.TRACKING) {
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

        try {
            landmarker.detectAsync(mpImage, timestampMs)
        } catch (e: Exception) {
            Log.w(TAG, "detectAsync failed", e)
        }

        // Return bitmap to pool after detectAsync (MediaPipe copies data internally)
        imageConverter.returnBitmap(bitmap)
        imageProxy.close()
    }

    @Suppress("UnusedParameter")
    private fun onPoseLandmarkerResult(
        result: PoseLandmarkerResult,
        input: com.google.mediapipe.framework.image.MPImage,
    ) {
        val timestampMs = SystemClock.elapsedRealtime()

        if (result.landmarks().isEmpty()) {
            _trackingData.tryEmit(BodyTrackingData.notTracking(timestampMs))
            return
        }

        var bodies = mutableListOf<TrackedBody>()

        for (i in result.landmarks().indices) {
            val landmarks = BodyLandmarkMapper.mapLandmarks(result.landmarks()[i])
            bodies.add(TrackedBody(landmarks = landmarks))
        }

        // Apply smoothing (protected by lock)
        val processedBodies = pipelineLock.withLock {
            if (released.load() != 0) return
            smoother?.let { bodies = it.smooth(bodies, timestampMs).toMutableList() }
            bodies.toList()
        }

        val data = BodyTrackingData(
            bodies = processedBodies,
            sourceImageWidth = lastImageWidth,
            sourceImageHeight = lastImageHeight,
            timestampMs = timestampMs,
            isTracking = true,
        )

        _trackingData.tryEmit(data)
    }

    companion object {
        private const val TAG = "MediaPipeBodyTracker"
        private const val MODEL_ASSET_PATH = "models/pose_landmarker_lite.task"
        private const val CONFIDENCE_THRESHOLD = 0.5f
    }
}
