package io.github.kmpfacelink.internal

import android.util.Log
import android.util.Size
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.core.resolutionselector.AspectRatioStrategy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.core.Delegate
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.facelandmarker.FaceLandmarker
import com.google.mediapipe.tasks.vision.facelandmarker.FaceLandmarkerResult
import io.github.kmpfacelink.api.FaceTracker
import io.github.kmpfacelink.api.PlatformContext
import io.github.kmpfacelink.api.TrackingState
import io.github.kmpfacelink.model.BlendShapeData
import io.github.kmpfacelink.model.CameraFacing
import io.github.kmpfacelink.model.FaceLandmark
import io.github.kmpfacelink.model.FaceTrackerConfig
import io.github.kmpfacelink.model.FaceTrackingData
import io.github.kmpfacelink.model.HeadTransform
import io.github.kmpfacelink.model.SmoothingConfig
import io.github.kmpfacelink.util.BlendShapeEnhancer
import io.github.kmpfacelink.util.BlendShapeSmoother
import io.github.kmpfacelink.util.Calibrator
import io.github.kmpfacelink.util.PlatformLock
import io.github.kmpfacelink.util.TransformUtils
import io.github.kmpfacelink.util.createSmoother
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

    private val pipelineLock = PlatformLock()
    private val released = AtomicInt(0)

    private var smoother: BlendShapeSmoother? = config.smoothingConfig.createSmoother()
    private val enhancer: BlendShapeEnhancer? = BlendShapeEnhancer.create(config.enhancerConfig)
    private val calibrator = Calibrator()
    private val analysisExecutor = Executors.newSingleThreadExecutor()
    private val imageConverter = ImageProxyConverter()
    private val isFrontCamera = config.cameraFacing == CameraFacing.FRONT

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
        pipelineLock.withLock {
            check(released.load() == 0) { "Cannot start a released tracker" }
            if (_state.value == TrackingState.TRACKING || _state.value == TrackingState.STARTING) return
            _state.value = TrackingState.STARTING
        }

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
        released.store(1)
        pipelineLock.withLock {
            cameraProvider?.unbindAll()
            faceLandmarker?.close()
            faceLandmarker = null
            imageConverter.release()
            analysisExecutor.shutdown()
            _state.value = TrackingState.STOPPED
        }
    }

    override fun resetCalibration() {
        pipelineLock.withLock {
            calibrator.reset()
            smoother?.reset()
        }
    }

    override fun updateSmoothing(config: SmoothingConfig) {
        pipelineLock.withLock {
            smoother = config.createSmoother()
        }
    }

    @Suppress("TooGenericExceptionCaught") // MediaPipe GPU delegate throws RuntimeException
    private fun initFaceLandmarker() {
        // Try GPU delegate first, fall back to CPU on failure
        faceLandmarker = try {
            createLandmarker(Delegate.GPU)
        } catch (e: RuntimeException) {
            Log.w(TAG, "GPU delegate unavailable, falling back to CPU", e)
            createLandmarker(Delegate.CPU)
        }
    }

    private fun createLandmarker(delegate: Delegate): FaceLandmarker {
        val baseOptions = BaseOptions.builder()
            .setModelAssetPath(MODEL_ASSET_PATH)
            .setDelegate(delegate)
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

        return FaceLandmarker.createFromOptions(platformContext.context, options)
    }

    private suspend fun startCamera() {
        val provider = getCameraProvider()
        cameraProvider = provider

        val cameraSelector = when (config.cameraFacing) {
            CameraFacing.FRONT -> CameraSelector.DEFAULT_FRONT_CAMERA
            CameraFacing.BACK -> CameraSelector.DEFAULT_BACK_CAMERA
        }

        val imageAnalysis = ImageAnalysis.Builder()
            .setResolutionSelector(buildAnalysisResolutionSelector())
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
            .build()
            .also { it.setAnalyzer(analysisExecutor, ::processFrame) }

        provider.unbindAll()

        val surfaceProvider = previewSurfaceProvider
        if (surfaceProvider != null) {
            val preview = Preview.Builder()
                .setResolutionSelector(buildPreviewResolutionSelector())
                .build().also { it.surfaceProvider = surfaceProvider }
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

    private fun buildAnalysisResolutionSelector(): ResolutionSelector =
        ResolutionSelector.Builder()
            .setAspectRatioStrategy(
                AspectRatioStrategy(
                    androidx.camera.core.AspectRatio.RATIO_4_3,
                    AspectRatioStrategy.FALLBACK_RULE_AUTO,
                ),
            )
            .setResolutionStrategy(
                ResolutionStrategy(
                    Size(CAMERA_WIDTH, CAMERA_HEIGHT),
                    ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER,
                ),
            )
            .build()

    private fun buildPreviewResolutionSelector(): ResolutionSelector =
        ResolutionSelector.Builder()
            .setAspectRatioStrategy(
                AspectRatioStrategy(
                    androidx.camera.core.AspectRatio.RATIO_4_3,
                    AspectRatioStrategy.FALLBACK_RULE_AUTO,
                ),
            )
            .build()

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

        val bitmap = imageConverter.convert(imageProxy, mirrorHorizontally = isFrontCamera)
        if (bitmap == null) {
            imageProxy.close()
            return
        }

        // Store rotated image dimensions for landmark coordinate mapping
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

    @Suppress("UnusedParameter")
    private fun onFaceLandmarkerResult(
        result: FaceLandmarkerResult,
        input: com.google.mediapipe.framework.image.MPImage,
    ) {
        val timestampMs = System.currentTimeMillis()

        if (!result.faceBlendshapes().isPresent || result.faceBlendshapes().get().isEmpty()) {
            _trackingData.tryEmit(FaceTrackingData.notTracking(timestampMs))
            return
        }

        // Extract landmarks (478 normalized points) — needed for enhancer
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

        // Extract blend shapes
        val categories = result.faceBlendshapes().get()[0].map { category ->
            category.categoryName() to category.score()
        }
        var blendShapes: BlendShapeData = BlendShapeMapper.mapFromMediaPipe(categories)

        // Pipeline: enhance → calibrate → smooth (protected by lock)
        val processedBlendShapes = pipelineLock.withLock {
            if (released.load() != 0) return

            // Enhance low-accuracy blend shapes using geometric landmark calculations
            enhancer?.let { blendShapes = it.enhance(blendShapes, landmarks) }

            // Apply calibration
            if (config.enableCalibration) {
                blendShapes = calibrator.calibrate(blendShapes)
            }

            // Apply smoothing
            smoother?.let { blendShapes = it.smooth(blendShapes, timestampMs) }

            blendShapes
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

        val data = FaceTrackingData(
            blendShapes = processedBlendShapes,
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
