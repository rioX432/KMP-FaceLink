package io.github.kmpfacelink.internal

import android.graphics.Bitmap
import android.graphics.Matrix
import android.util.Log
import androidx.camera.core.AspectRatio
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.core.BaseOptions
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
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.concurrent.Executors
import kotlin.concurrent.atomics.AtomicInt
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

@OptIn(ExperimentalAtomicApi::class)
internal class MediaPipeHandTracker(
    private val platformContext: PlatformContext,
    private val config: HandTrackerConfig,
) : HandTracker, PreviewableHandTracker {

    private val _trackingData = MutableSharedFlow<HandTrackingData>(
        replay = 1,
        extraBufferCapacity = 1,
    )
    override val trackingData: Flow<HandTrackingData> = _trackingData.asSharedFlow()

    private val _state = MutableStateFlow(TrackingState.IDLE)
    override val state: StateFlow<TrackingState> = _state.asStateFlow()

    private val pipelineLock = PlatformLock()
    private val released = AtomicInt(0)

    private var smoother: HandLandmarkSmoother? = config.smoothingConfig.createHandSmoother()
    private val isFrontCamera = config.cameraFacing == CameraFacing.FRONT
    private val analysisExecutor = Executors.newSingleThreadExecutor()

    private var handLandmarker: HandLandmarker? = null
    private var cameraProvider: ProcessCameraProvider? = null
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
            _state.value = TrackingState.TRACKING
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start hand tracking", e)
            _state.value = TrackingState.ERROR
            throw e
        }
    }

    override suspend fun stop() {
        cameraProvider?.unbindAll()
        _state.value = TrackingState.STOPPED
    }

    override fun release() {
        released.store(1)
        pipelineLock.withLock {
            cameraProvider?.unbindAll()
            handLandmarker?.close()
            handLandmarker = null
            analysisExecutor.shutdown()
            _state.value = TrackingState.STOPPED
        }
    }

    override fun updateSmoothing(config: SmoothingConfig) {
        pipelineLock.withLock {
            smoother = config.createHandSmoother()
        }
    }

    private fun initHandLandmarker() {
        val baseOptions = BaseOptions.builder()
            .setModelAssetPath(MODEL_ASSET_PATH)
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

        handLandmarker = HandLandmarker.createFromOptions(platformContext.context, options)
    }

    private suspend fun startCamera() {
        val provider = getCameraProvider()
        cameraProvider = provider

        val cameraSelector = when (config.cameraFacing) {
            CameraFacing.FRONT -> CameraSelector.DEFAULT_FRONT_CAMERA
            CameraFacing.BACK -> CameraSelector.DEFAULT_BACK_CAMERA
        }

        @Suppress("DEPRECATION")
        val imageAnalysis = ImageAnalysis.Builder()
            .setTargetResolution(android.util.Size(CAMERA_WIDTH, CAMERA_HEIGHT))
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
            .build()
            .also { analysis ->
                analysis.setAnalyzer(analysisExecutor) { imageProxy ->
                    processFrame(imageProxy)
                }
            }

        provider.unbindAll()

        val surfaceProvider = previewSurfaceProvider
        if (surfaceProvider != null) {
            @Suppress("DEPRECATION")
            val preview = Preview.Builder()
                .setTargetAspectRatio(AspectRatio.RATIO_4_3)
                .build().also {
                    it.surfaceProvider = surfaceProvider
                }
            provider.bindToLifecycle(
                platformContext.lifecycleOwner,
                cameraSelector,
                preview,
                imageAnalysis,
            )
        } else {
            provider.bindToLifecycle(
                platformContext.lifecycleOwner,
                cameraSelector,
                imageAnalysis,
            )
        }
    }

    private suspend fun getCameraProvider(): ProcessCameraProvider =
        suspendCancellableCoroutine { cont ->
            val future = ProcessCameraProvider.getInstance(platformContext.context)
            future.addListener(
                {
                    try {
                        cont.resume(future.get())
                    } catch (e: Exception) {
                        cont.resumeWithException(e)
                    }
                },
                ContextCompat.getMainExecutor(platformContext.context),
            )
        }

    private fun processFrame(imageProxy: ImageProxy) {
        val landmarker = handLandmarker
        if (landmarker == null || _state.value != TrackingState.TRACKING) {
            imageProxy.close()
            return
        }

        val bitmap = imageProxyToBitmap(imageProxy)
        if (bitmap == null) {
            imageProxy.close()
            return
        }

        lastImageWidth = bitmap.width
        lastImageHeight = bitmap.height

        val mpImage = BitmapImageBuilder(bitmap).build()
        val timestampMs = System.currentTimeMillis()

        try {
            landmarker.detectAsync(mpImage, timestampMs)
        } catch (e: Exception) {
            Log.w(TAG, "detectAsync failed", e)
        }

        imageProxy.close()
    }

    private fun imageProxyToBitmap(imageProxy: ImageProxy): Bitmap? {
        val plane = imageProxy.planes[0]
        val buffer = plane.buffer
        val width = imageProxy.width
        val height = imageProxy.height
        val rowStride = plane.rowStride
        val pixelStride = plane.pixelStride
        val expectedRowBytes = width * pixelStride

        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)

        if (rowStride == expectedRowBytes) {
            buffer.rewind()
            bitmap.copyPixelsFromBuffer(buffer)
        } else {
            val packed = java.nio.ByteBuffer.allocateDirect(expectedRowBytes * height)
            val rowData = ByteArray(rowStride)
            buffer.rewind()
            for (y in 0 until height) {
                val bytesToRead = if (y < height - 1) rowStride else expectedRowBytes
                buffer.get(rowData, 0, bytesToRead)
                packed.put(rowData, 0, expectedRowBytes)
            }
            packed.rewind()
            bitmap.copyPixelsFromBuffer(packed)
        }

        val rotationDegrees = imageProxy.imageInfo.rotationDegrees
        return if (rotationDegrees != 0) {
            val matrix = Matrix().apply { postRotate(rotationDegrees.toFloat()) }
            Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        } else {
            bitmap
        }
    }

    @Suppress("UnusedParameter")
    private fun onHandLandmarkerResult(
        result: HandLandmarkerResult,
        input: com.google.mediapipe.framework.image.MPImage,
    ) {
        val timestampMs = System.currentTimeMillis()

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
        private const val CAMERA_WIDTH = 320
        private const val CAMERA_HEIGHT = 240
    }
}
