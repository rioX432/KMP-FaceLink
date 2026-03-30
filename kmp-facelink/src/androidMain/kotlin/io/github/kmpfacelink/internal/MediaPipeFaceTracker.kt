package io.github.kmpfacelink.internal

import android.content.pm.PackageManager
import android.os.SystemClock
import android.util.Log
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
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
import io.github.kmpfacelink.model.TrackedFace
import io.github.kmpfacelink.util.BlendShapeEnhancer
import io.github.kmpfacelink.util.BlendShapeSmoother
import io.github.kmpfacelink.util.Calibrator
import io.github.kmpfacelink.util.FrameThrottler
import io.github.kmpfacelink.util.GazeEstimator
import io.github.kmpfacelink.util.PlatformLock
import io.github.kmpfacelink.util.PowerModeManager
import io.github.kmpfacelink.util.ThermalMonitor
import io.github.kmpfacelink.util.TransformUtils
import io.github.kmpfacelink.util.createSmoother
import io.github.kmpfacelink.util.withLock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.util.concurrent.ExecutionException
import java.util.concurrent.Executors
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import kotlin.concurrent.atomics.AtomicInt
import kotlin.concurrent.atomics.ExperimentalAtomicApi

// Lifecycle (start/stop/release/switchCamera), config (resetCalibration/updateSmoothing),
// camera pipeline (startCamera/processFrame/processSharedFrame), and result mapping
// (onFaceLandmarkerResult/processPrimaryPipeline/buildTrackedFaces) form a cohesive unit.
// Extracting these would split tightly coupled camera+MediaPipe state across classes.
@Suppress("TooManyFunctions")
@OptIn(ExperimentalAtomicApi::class)
internal class MediaPipeFaceTracker(
    private val platformContext: PlatformContext,
    private val config: FaceTrackerConfig,
    private val sharedCameraSession: SharedCameraSession? = null,
) : FaceTracker, PreviewableFaceTracker {

    private val _trackingData = MutableSharedFlow<FaceTrackingData>(
        replay = 1,
        extraBufferCapacity = 1,
    )
    override val trackingData: Flow<FaceTrackingData> = _trackingData.asSharedFlow()

    private val _state = MutableStateFlow(TrackingState.IDLE)
    override val state: StateFlow<TrackingState> = _state.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    override val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private val pipelineLock = PlatformLock()
    private val released = AtomicInt(0)

    private var smoother: BlendShapeSmoother? = config.smoothingConfig.createSmoother()
    private val enhancer: BlendShapeEnhancer? = BlendShapeEnhancer.create(config.enhancerConfig)
    private val calibrator = Calibrator(
        warmupFrames = config.calibrationConfig.warmupFrames,
        decayRate = config.calibrationConfig.rangeDecayRate,
    )
    private val thermalMonitor: ThermalMonitor = AndroidThermalMonitor(platformContext.context)
    private val batteryMonitor = AndroidBatteryMonitor(platformContext.context)
    private val frameThrottler = FrameThrottler(config.thermalPolicy)
    private val powerModeManager: PowerModeManager? = config.powerMode?.let { mode ->
        PowerModeManager(
            powerMode = mode,
            thermalMonitor = thermalMonitor,
            batteryMonitor = batteryMonitor,
            lowBatteryThreshold = config.lowBatteryThreshold,
            enableIdleDetection = config.enableIdleDetection,
        )
    }
    private val analysisExecutor = Executors.newSingleThreadExecutor()
    private val imageConverter = ImageProxyConverter()
    private val cameraManager = if (sharedCameraSession == null) CameraXManager(platformContext) else null

    @Volatile
    internal var currentCameraFacing: CameraFacing = config.cameraFacing
    private val isFrontCamera: Boolean
        get() = currentCameraFacing == CameraFacing.FRONT

    private var faceLandmarker: FaceLandmarker? = null
    private var previewSurfaceProvider: Preview.SurfaceProvider? = null

    /** Bitmap currently being processed by MediaPipe (independent camera path only). */
    private val inFlightBitmap = AtomicReference<android.graphics.Bitmap?>(null)

    @Volatile
    private var lastImageWidth: Int = 0

    @Volatile
    private var lastImageHeight: Int = 0

    override fun setSurfaceProvider(surfaceProvider: Preview.SurfaceProvider?) {
        previewSurfaceProvider = surfaceProvider
    }

    override suspend fun start() {
        val requiredFeature = when (currentCameraFacing) {
            CameraFacing.FRONT -> PackageManager.FEATURE_CAMERA_FRONT
            CameraFacing.BACK -> PackageManager.FEATURE_CAMERA
        }
        if (!platformContext.context.packageManager.hasSystemFeature(requiredFeature)) {
            throw UnsupportedOperationException("No ${currentCameraFacing.name.lowercase()} camera available")
        }

        pipelineLock.withLock {
            check(released.load() == 0) { "Cannot start a released tracker" }
            if (_state.value == TrackingState.TRACKING || _state.value == TrackingState.STARTING) return
            _state.value = TrackingState.STARTING
        }

        try {
            initFaceLandmarker()
            startCamera()
            pipelineLock.withLock {
                if (released.load() == 0) {
                    _state.value = TrackingState.TRACKING
                }
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            // MediaPipe model loading and CameraX binding can throw various RuntimeException
            // subclasses; catch broadly to set error state before rethrowing
            Log.e(TAG, "Failed to start face tracking", e)
            _errorMessage.value = e.message ?: e.toString()
            _state.value = TrackingState.ERROR
            throw e
        }
    }

    override suspend fun stop() {
        var landmarkerToClose: FaceLandmarker? = null
        pipelineLock.withLock {
            _state.value = TrackingState.STOPPED
            landmarkerToClose = faceLandmarker
            faceLandmarker = null
            cameraManager?.unbindAll()
        }
        // Drain any in-flight frame processing on the analysis executor
        if (cameraManager != null) {
            try {
                withContext(Dispatchers.IO) { analysisExecutor.submit {}.get() }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (_: RejectedExecutionException) {
                // Executor may already be shut down — safe to ignore
            } catch (_: ExecutionException) {
                // Task execution failed — safe to ignore during drain
            } catch (_: InterruptedException) {
                // Thread interrupted during drain — safe to ignore
            }
        }
        // Allow final async detection result from MediaPipe to be delivered
        if (landmarkerToClose != null) {
            delay(config.stopDrainDelayMs)
            landmarkerToClose.close()
        }
    }

    override fun release() {
        var landmarkerToClose: FaceLandmarker? = null
        pipelineLock.withLock {
            released.store(1)
            _state.value = TrackingState.RELEASED
            landmarkerToClose = faceLandmarker
            faceLandmarker = null
            cameraManager?.unbindAll()
            imageConverter.release()
            thermalMonitor.release()
            batteryMonitor.release()
            powerModeManager?.release()
        }
        analysisExecutor.shutdown()
        try {
            analysisExecutor.awaitTermination(config.releaseTimeoutMs, TimeUnit.MILLISECONDS)
        } catch (_: InterruptedException) {
            // Best-effort shutdown — safe to ignore
        }
        landmarkerToClose?.close()
    }

    override fun resetCalibration() {
        pipelineLock.withLock {
            check(released.load() == 0) { "Cannot reset calibration on a released tracker" }
            calibrator.reset()
            smoother?.reset()
        }
    }

    override fun updateSmoothing(config: SmoothingConfig) {
        pipelineLock.withLock {
            check(released.load() == 0) { "Cannot update smoothing on a released tracker" }
            smoother = config.createSmoother()
        }
    }

    override suspend fun switchCamera(facing: CameraFacing) {
        pipelineLock.withLock {
            check(released.load() == 0) { "Cannot switch camera on a released tracker" }
        }
        if (currentCameraFacing == facing) return
        val wasTracking = _state.value == TrackingState.TRACKING
        if (wasTracking) stop()
        currentCameraFacing = facing
        if (wasTracking) start()
    }

    private fun initFaceLandmarker() {
        // GPU delegate can fail with RuntimeException on devices lacking OpenGL ES 3.1+
        // or when the GPU driver rejects the model. Fall back to CPU in that case.
        faceLandmarker = try {
            createLandmarker(Delegate.GPU)
        } catch (@Suppress("TooGenericExceptionCaught") e: RuntimeException) {
            Log.w(TAG, "GPU delegate unavailable, falling back to CPU", e)
            createLandmarker(Delegate.CPU)
        }
    }

    private fun createLandmarker(delegate: Delegate): FaceLandmarker {
        val baseOptions = BaseOptions.builder()
            .setModelAssetPath(config.modelAssetPath)
            .setDelegate(delegate)
            .build()

        val options = FaceLandmarker.FaceLandmarkerOptions.builder()
            .setBaseOptions(baseOptions)
            .setRunningMode(RunningMode.LIVE_STREAM)
            .setNumFaces(config.maxFaces)
            .setMinFaceDetectionConfidence(config.minDetectionConfidence)
            .setMinTrackingConfidence(config.minTrackingConfidence)
            .setMinFacePresenceConfidence(config.minPresenceConfidence)
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
        val landmarker = faceLandmarker
        if (landmarker == null || _state.value != TrackingState.TRACKING) {
            sharedCameraSession?.returnInFlightBitmap(timestampMs)
            return
        }

        // Frame throttling: use PowerModeManager when available, else legacy FrameThrottler
        if (!shouldProcessFrame(timestampMs)) {
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
        val landmarker = faceLandmarker
        if (landmarker == null || _state.value != TrackingState.TRACKING) {
            imageProxy.close()
            return
        }

        // Frame throttling: use PowerModeManager when available, else legacy FrameThrottler
        if (!shouldProcessFrame(SystemClock.elapsedRealtime())) {
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
        val timestampMs = SystemClock.elapsedRealtime()

        // Track the bitmap — it will be returned in the result callback
        inFlightBitmap.set(bitmap)

        try {
            landmarker.detectAsync(mpImage, timestampMs)
        } catch (e: Exception) {
            // MediaPipe may throw IllegalArgumentException (non-monotonic timestamp) or
            // MediaPipeException on internal errors; must not crash the frame processing loop
            Log.w(TAG, "detectAsync failed", e)
            // No result callback will fire — return bitmap now if we still own it
            if (inFlightBitmap.compareAndSet(bitmap, null)) {
                imageConverter.returnBitmap(bitmap)
            }
        }

        imageProxy.close()
    }

    private fun onFaceLandmarkerResult(
        result: FaceLandmarkerResult,
        @Suppress("UNUSED_PARAMETER") input: com.google.mediapipe.framework.image.MPImage,
    ) {
        // Return the bitmap now that MediaPipe is done processing
        if (sharedCameraSession != null) {
            sharedCameraSession.returnInFlightBitmap(result.timestampMs())
        } else {
            inFlightBitmap.getAndSet(null)?.let { imageConverter.returnBitmap(it) }
        }

        val timestampMs = result.timestampMs()

        if (!result.faceBlendshapes().isPresent || result.faceBlendshapes().get().isEmpty()) {
            _trackingData.tryEmit(FaceTrackingData.notTracking(timestampMs))
            return
        }

        val primaryLandmarks = extractLandmarks(result, faceIndex = 0)
        val primaryBlendShapes = extractBlendShapes(result, faceIndex = 0)
        val processedPrimary = processPrimaryPipeline(primaryBlendShapes, primaryLandmarks, timestampMs) ?: return
        val primaryHeadTransform = extractHeadTransform(result, faceIndex = 0)

        val faces = buildTrackedFaces(result, processedPrimary, primaryLandmarks, primaryHeadTransform, timestampMs)

        val data = FaceTrackingData(
            blendShapes = processedPrimary,
            headTransform = primaryHeadTransform,
            gazeData = GazeEstimator.estimate(processedPrimary),
            landmarks = primaryLandmarks,
            sourceImageWidth = lastImageWidth,
            sourceImageHeight = lastImageHeight,
            timestampMs = timestampMs,
            isTracking = true,
            faces = faces,
            thermalState = thermalMonitor.thermalState.value,
        )

        _trackingData.tryEmit(data)
    }

    /** Whether a frame at the given timestamp should be processed. */
    private fun shouldProcessFrame(timestampMs: Long): Boolean {
        return if (powerModeManager != null) {
            powerModeManager.shouldProcess(timestampMs)
        } else {
            frameThrottler.shouldProcess(timestampMs, thermalMonitor.thermalState.value)
        }
    }

    /** Whether the blend shape enhancer should be disabled. */
    private fun shouldDisableEnhancer(): Boolean {
        return if (powerModeManager != null) {
            powerModeManager.shouldDisableEnhancer()
        } else {
            frameThrottler.shouldDisableEnhancer(thermalMonitor.thermalState.value)
        }
    }

    /** Runs enhance → calibrate → smooth on the primary face. Returns null if tracker is released. */
    private fun processPrimaryPipeline(
        blendShapes: BlendShapeData,
        landmarks: List<FaceLandmark>,
        timestampMs: Long,
    ): BlendShapeData? {
        var result = blendShapes
        return pipelineLock.withLock {
            if (released.load() != 0) return null

            if (!shouldDisableEnhancer()) {
                enhancer?.let { result = it.enhance(result, landmarks) }
            }

            if (config.calibrationConfig.enabled) {
                result = calibrator.calibrate(result)
            }

            smoother?.let { result = it.smooth(result, timestampMs) }

            // Notify power mode manager for idle detection
            powerModeManager?.onFrameProcessed(result, timestampMs)

            // Defensive copy: pipeline stages return reusable scratch maps.
            // The result is stored in FaceTrackingData (held by the SharedFlow replay buffer),
            // so we must snapshot it before the next frame overwrites the scratch map.
            HashMap(result)
        }
    }

    private fun buildTrackedFaces(
        result: FaceLandmarkerResult,
        processedPrimary: BlendShapeData,
        primaryLandmarks: List<FaceLandmark>,
        primaryHeadTransform: HeadTransform,
        timestampMs: Long,
    ): List<TrackedFace> {
        val faceCount = result.faceBlendshapes().get().size
        return List(faceCount) { index ->
            if (index == 0) {
                TrackedFace(
                    faceIndex = 0,
                    blendShapes = processedPrimary,
                    headTransform = primaryHeadTransform,
                    gazeData = GazeEstimator.estimate(processedPrimary),
                    landmarks = primaryLandmarks,
                )
            } else {
                val landmarks = extractLandmarks(result, index)
                val headTransform = extractHeadTransform(result, index)
                // Secondary faces: apply only smoothing (no per-face calibration/enhancement state)
                var blendShapes = extractBlendShapes(result, index)
                pipelineLock.withLock {
                    smoother?.let {
                        // Defensive copy: smoother returns a reusable scratch map.
                        blendShapes = HashMap(it.smooth(blendShapes, timestampMs))
                    }
                }
                TrackedFace(
                    faceIndex = index,
                    blendShapes = blendShapes,
                    headTransform = headTransform,
                    gazeData = GazeEstimator.estimate(blendShapes),
                    landmarks = landmarks,
                )
            }
        }
    }

    companion object {
        private const val TAG = "MediaPipeFaceTracker"

        private fun extractLandmarks(result: FaceLandmarkerResult, faceIndex: Int): List<FaceLandmark> {
            val allLandmarks = result.faceLandmarks()
            if (faceIndex >= allLandmarks.size) return emptyList()
            return allLandmarks[faceIndex].map { landmark ->
                FaceLandmark(
                    x = landmark.x(),
                    y = landmark.y(),
                    z = landmark.z(),
                    visibility = landmark.visibility().orElse(0f),
                    presence = landmark.presence().orElse(0f),
                )
            }
        }

        private fun extractBlendShapes(result: FaceLandmarkerResult, faceIndex: Int): BlendShapeData {
            val categories = result.faceBlendshapes().get()[faceIndex].map { category ->
                category.categoryName() to category.score()
            }
            return BlendShapeMapper.mapFromMediaPipe(categories)
        }

        private fun extractHeadTransform(result: FaceLandmarkerResult, faceIndex: Int): HeadTransform {
            if (!result.facialTransformationMatrixes().isPresent) return HeadTransform()
            val matrices = result.facialTransformationMatrixes().get()
            if (faceIndex >= matrices.size) return HeadTransform()
            // MediaPipe returns flat column-major float array (16 elements)
            return TransformUtils.fromMatrix(matrices[faceIndex])
        }
    }
}
