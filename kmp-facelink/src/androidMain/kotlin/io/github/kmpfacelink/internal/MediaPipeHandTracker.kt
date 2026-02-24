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
import io.github.kmpfacelink.util.GestureClassifier
import io.github.kmpfacelink.util.HandLandmarkSmoother
import io.github.kmpfacelink.util.PlatformLock
import io.github.kmpfacelink.util.createHandSmoother
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
internal class MediaPipeHandTracker(
    private val platformContext: PlatformContext,
    private val config: HandTrackerConfig,
    private val sharedCameraSession: SharedCameraSession? = null,
) : HandTracker, PreviewableHandTracker {

    private val _trackingData = MutableSharedFlow<HandTrackingData>(
        replay = 1,
        extraBufferCapacity = 1,
    )
    override val trackingData: Flow<HandTrackingData> = _trackingData.asSharedFlow()

    private val _state = MutableStateFlow(TrackingState.IDLE)
    override val state: StateFlow<TrackingState> = _state.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    override val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private val pipelineLock = PlatformLock()
    private val released = AtomicInt(0)

    private var smoother: HandLandmarkSmoother? = config.smoothingConfig.createHandSmoother()
    private val isFrontCamera = config.cameraFacing == CameraFacing.FRONT
    private val analysisExecutor = Executors.newSingleThreadExecutor()
    private val imageConverter = ImageProxyConverter()
    private val cameraManager = if (sharedCameraSession == null) CameraXManager(platformContext) else null

    private var handLandmarker: HandLandmarker? = null
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
            initHandLandmarker()
            startCamera()
            pipelineLock.withLock {
                if (released.load() == 0) {
                    _state.value = TrackingState.TRACKING
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start hand tracking", e)
            _errorMessage.value = e.message ?: e.toString()
            _state.value = TrackingState.ERROR
            throw e
        }
    }

    override suspend fun stop() {
        pipelineLock.withLock {
            cameraManager?.unbindAll()
            handLandmarker?.close()
            handLandmarker = null
            _state.value = TrackingState.STOPPED
        }
    }

    override fun release() {
        pipelineLock.withLock {
            released.store(1)
            cameraManager?.unbindAll()
            handLandmarker?.close()
            handLandmarker = null
            imageConverter.release()
            analysisExecutor.shutdown()
            _state.value = TrackingState.RELEASED
        }
    }

    override fun updateSmoothing(config: SmoothingConfig) {
        pipelineLock.withLock {
            check(released.load() == 0) { "Cannot update smoothing on a released tracker" }
            smoother = config.createHandSmoother()
        }
    }

    @Suppress("TooGenericExceptionCaught") // MediaPipe GPU delegate throws RuntimeException
    private fun initHandLandmarker() {
        // Try GPU delegate first, fall back to CPU on failure
        handLandmarker = try {
            createLandmarker(Delegate.GPU)
        } catch (e: RuntimeException) {
            Log.w(TAG, "GPU delegate unavailable, falling back to CPU", e)
            createLandmarker(Delegate.CPU)
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
            .setMinHandDetectionConfidence(0.5f)
            .setMinTrackingConfidence(0.5f)
            .setMinHandPresenceConfidence(0.5f)
            .setResultListener(::onHandLandmarkerResult)
            .setErrorListener { error ->
                Log.e(TAG, "HandLandmarker error: ${error.message}", error)
            }
            .build()

        return HandLandmarker.createFromOptions(platformContext.context, options)
    }

    private suspend fun startCamera() {
        if (sharedCameraSession != null) {
            sharedCameraSession.addFrameHandler(::processSharedFrame)
        } else {
            val imageAnalysis = ImageAnalysis.Builder()
                .setResolutionSelector(CameraXManager.buildAnalysisResolutionSelector())
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
                .build()
                .also { it.setAnalyzer(analysisExecutor, ::processFrame) }

            cameraManager!!.startCamera(
                cameraFacing = config.cameraFacing,
                imageAnalysis = imageAnalysis,
                surfaceProvider = previewSurfaceProvider,
            )
        }
    }

    @Suppress("UNUSED_PARAMETER") // SharedFrameHandler signature
    private fun processSharedFrame(bitmap: android.graphics.Bitmap, width: Int, height: Int, timestampMs: Long) {
        val landmarker = handLandmarker
        if (landmarker == null || _state.value != TrackingState.TRACKING) return

        lastImageWidth = width
        lastImageHeight = height

        val mpImage = BitmapImageBuilder(bitmap).build()
        try {
            landmarker.detectAsync(mpImage, timestampMs)
        } catch (e: Exception) {
            Log.w(TAG, "detectAsync failed", e)
        }
    }

    private fun processFrame(imageProxy: ImageProxy) {
        val landmarker = handLandmarker
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
    private fun onHandLandmarkerResult(
        result: HandLandmarkerResult,
        input: com.google.mediapipe.framework.image.MPImage,
    ) {
        val timestampMs = result.timestampMs()

        if (result.landmarks().isEmpty()) {
            _trackingData.tryEmit(HandTrackingData.notTracking(timestampMs))
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
            if (released.load() != 0) return
            smoother?.let { hands = it.smooth(hands, timestampMs).toMutableList() }
            hands.toList()
        }

        val data = HandTrackingData(
            hands = processedHands,
            sourceImageWidth = lastImageWidth,
            sourceImageHeight = lastImageHeight,
            timestampMs = timestampMs,
            isTracking = true,
        )

        _trackingData.tryEmit(data)
    }

    companion object {
        private const val TAG = "MediaPipeHandTracker"
        private const val MODEL_ASSET_PATH = "models/hand_landmarker.task"
    }
}
