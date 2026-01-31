package io.github.kmpfacelink.internal

import android.graphics.Bitmap
import android.graphics.Matrix
import android.util.Log
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.facelandmarker.FaceLandmarker
import com.google.mediapipe.tasks.vision.facelandmarker.FaceLandmarkerResult
import io.github.kmpfacelink.api.FaceTracker
import io.github.kmpfacelink.api.PlatformContext
import io.github.kmpfacelink.api.TrackingState
import io.github.kmpfacelink.model.BlendShapeData
import io.github.kmpfacelink.model.FaceLandmark
import io.github.kmpfacelink.model.FaceTrackerConfig
import io.github.kmpfacelink.model.FaceTrackingData
import io.github.kmpfacelink.model.CameraFacing
import io.github.kmpfacelink.model.HeadTransform
import io.github.kmpfacelink.util.Calibrator
import io.github.kmpfacelink.util.ExponentialMovingAverage
import io.github.kmpfacelink.util.TransformUtils
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.concurrent.Executors
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

internal class MediaPipeFaceTracker(
    private val platformContext: PlatformContext,
    private val config: FaceTrackerConfig,
) : FaceTracker, PreviewableFaceTracker {

    private val _trackingData = MutableSharedFlow<FaceTrackingData>(
        replay = 1,
        extraBufferCapacity = 1,
    )
    override val trackingData: Flow<FaceTrackingData> = _trackingData.asSharedFlow()

    private val _state = MutableStateFlow(TrackingState.IDLE)
    override val state: StateFlow<TrackingState> = _state.asStateFlow()

    private val smoother = ExponentialMovingAverage(config.smoothingFactor)
    private val calibrator = Calibrator()
    private val analysisExecutor = Executors.newSingleThreadExecutor()

    private var faceLandmarker: FaceLandmarker? = null
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
        if (_state.value == TrackingState.TRACKING || _state.value == TrackingState.STARTING) return
        _state.value = TrackingState.STARTING

        try {
            initFaceLandmarker()
            startCamera()
            _state.value = TrackingState.TRACKING
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start face tracking", e)
            _state.value = TrackingState.ERROR
            throw e
        }
    }

    override suspend fun stop() {
        cameraProvider?.unbindAll()
        _state.value = TrackingState.STOPPED
    }

    override fun release() {
        cameraProvider?.unbindAll()
        faceLandmarker?.close()
        faceLandmarker = null
        analysisExecutor.shutdown()
        _state.value = TrackingState.STOPPED
    }

    override fun resetCalibration() {
        calibrator.reset()
        smoother.reset()
    }

    private fun initFaceLandmarker() {
        val baseOptions = BaseOptions.builder()
            .setModelAssetPath(MODEL_ASSET_PATH)
            .build()

        val options = FaceLandmarker.FaceLandmarkerOptions.builder()
            .setBaseOptions(baseOptions)
            .setRunningMode(RunningMode.LIVE_STREAM)
            .setNumFaces(1)
            .setMinFaceDetectionConfidence(0.5f)
            .setMinTrackingConfidence(0.5f)
            .setMinFacePresenceConfidence(0.5f)
            .setOutputFaceBlendshapes(true)
            .setOutputFacialTransformationMatrixes(true)
            .setResultListener(::onFaceLandmarkerResult)
            .setErrorListener { error ->
                Log.e(TAG, "FaceLandmarker error: ${error.message}", error)
            }
            .build()

        faceLandmarker = FaceLandmarker.createFromOptions(platformContext.context, options)
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
            val preview = Preview.Builder().build().also {
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
        val landmarker = faceLandmarker
        if (landmarker == null || _state.value != TrackingState.TRACKING) {
            imageProxy.close()
            return
        }

        val bitmap = imageProxyToBitmap(imageProxy)
        if (bitmap == null) {
            imageProxy.close()
            return
        }

        // Store rotated image dimensions for landmark coordinate mapping
        lastImageWidth = bitmap.width
        lastImageHeight = bitmap.height

        val mpImage = BitmapImageBuilder(bitmap).build()
        val frameTimeMs = imageProxy.imageInfo.timestamp / 1_000 // ns → µs, but MediaPipe wants ms
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
        val pixelStride = plane.pixelStride // 4 for RGBA_8888
        val expectedRowBytes = width * pixelStride

        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)

        if (rowStride == expectedRowBytes) {
            // No row padding — copy directly
            buffer.rewind()
            bitmap.copyPixelsFromBuffer(buffer)
        } else {
            // Row padding present — repack without padding.
            // Buffer layout: rows 0..N-2 have rowStride bytes each,
            // last row has only expectedRowBytes (no trailing padding).
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

        // Apply rotation
        val rotationDegrees = imageProxy.imageInfo.rotationDegrees
        return if (rotationDegrees != 0) {
            val matrix = Matrix().apply { postRotate(rotationDegrees.toFloat()) }
            Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        } else {
            bitmap
        }
    }

    private fun onFaceLandmarkerResult(result: FaceLandmarkerResult, input: com.google.mediapipe.framework.image.MPImage) {
        val timestampMs = System.currentTimeMillis()

        if (!result.faceBlendshapes().isPresent || result.faceBlendshapes().get().isEmpty()) {
            _trackingData.tryEmit(FaceTrackingData.notTracking(timestampMs))
            return
        }

        // Extract blend shapes
        val categories = result.faceBlendshapes().get()[0].map { category ->
            category.categoryName() to category.score()
        }
        var blendShapes: BlendShapeData = BlendShapeMapper.mapFromMediaPipe(categories)

        // Apply calibration
        if (config.enableCalibration) {
            blendShapes = calibrator.calibrate(blendShapes)
        }

        // Apply smoothing
        if (config.enableSmoothing) {
            blendShapes = smoother.smooth(blendShapes)
        }

        // Extract head transform
        val headTransform = if (result.facialTransformationMatrixes().isPresent &&
            result.facialTransformationMatrixes().get().isNotEmpty()
        ) {
            // MediaPipe returns flat column-major float array (16 elements)
            val flatMatrix = result.facialTransformationMatrixes().get()[0]
            TransformUtils.fromMatrix(flatMatrix)
        } else {
            HeadTransform()
        }

        // Extract landmarks (478 normalized points)
        val landmarks = if (result.faceLandmarks().isNotEmpty()) {
            result.faceLandmarks()[0].map { landmark ->
                FaceLandmark(
                    x = landmark.x(),
                    y = landmark.y(),
                    z = landmark.z(),
                )
            }
        } else {
            emptyList()
        }

        val data = FaceTrackingData(
            blendShapes = blendShapes,
            headTransform = headTransform,
            landmarks = landmarks,
            sourceImageWidth = lastImageWidth,
            sourceImageHeight = lastImageHeight,
            timestampMs = timestampMs,
            isTracking = true,
        )

        _trackingData.tryEmit(data)
    }

    companion object {
        private const val TAG = "MediaPipeFaceTracker"
        private const val MODEL_ASSET_PATH = "models/face_landmarker_v2_with_blendshapes.task"
        private const val CAMERA_WIDTH = 320
        private const val CAMERA_HEIGHT = 240
    }
}
