package io.github.kmpfacelink.internal

import android.content.pm.PackageManager
import android.os.SystemClock
import android.util.Log
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import com.google.mediapipe.framework.image.BitmapImageBuilder
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
import io.github.kmpfacelink.util.PowerModeManager
import io.github.kmpfacelink.util.createSmoother
import io.github.kmpfacelink.util.withLock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import java.util.concurrent.ExecutionException
import java.util.concurrent.Executors
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import kotlin.concurrent.atomics.ExperimentalAtomicApi

// Lifecycle (start/stop/release/switchCamera), config (resetCalibration/updateSmoothing),
// camera pipeline (startCamera/processFrame/processSharedFrame), and result assembly
// (onFaceLandmarkerResult/processPrimaryPipeline/buildTrackedFaces) form a cohesive unit.
// Raw result extraction is delegated to FaceLandmarkerResultMapper.
@OptIn(ExperimentalAtomicApi::class)
internal class MediaPipeFaceTracker(
    private val platformContext: PlatformContext,
    private val config: FaceTrackerConfig,
    private val sharedCameraSession: SharedCameraSession? = null,
) : BaseTracker<FaceTrackingData>(
    thermalMonitor = AndroidThermalMonitor(platformContext.context),
    frameThrottler = FrameThrottler(config.thermalPolicy),
),
    FaceTracker,
    PreviewableFaceTracker {

    override val trackingData: Flow<FaceTrackingData> = super.trackingData
    override val state: StateFlow<TrackingState> = super.state
    override val errorMessage: StateFlow<String?> = super.errorMessage

    private var smoother: BlendShapeSmoother? = config.smoothingConfig.createSmoother()
    private val enhancer: BlendShapeEnhancer? = BlendShapeEnhancer.create(config.enhancerConfig)
    private val calibrator = Calibrator(
        warmupFrames = config.calibrationConfig.warmupFrames,
        decayRate = config.calibrationConfig.rangeDecayRate,
    )
    private val batteryMonitor = AndroidBatteryMonitor(platformContext.context)
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
            if (!guardStart()) return
        }

        try {
            faceLandmarker = FaceLandmarkerFactory.create(platformContext, config, ::onFaceLandmarkerResult)
            startCamera()
            pipelineLock.withLock { transitionToTracking() }
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            // MediaPipe model loading and CameraX binding can throw various RuntimeException
            // subclasses; catch broadly to set error state before rethrowing
            Log.e(TAG, "Failed to start face tracking", e)
            setError(e.message ?: e.toString())
            throw e
        }
    }

    override suspend fun stop() {
        var landmarkerToClose: FaceLandmarker? = null
        pipelineLock.withLock {
            mutableState.value = TrackingState.STOPPED
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
            mutableState.value = TrackingState.RELEASED
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
            checkNotReleased("reset calibration")
            calibrator.reset()
            smoother?.reset()
        }
    }

    override fun updateSmoothing(config: SmoothingConfig) {
        pipelineLock.withLock {
            checkNotReleased("update smoothing")
            smoother = config.createSmoother()
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
        if (landmarker == null || mutableState.value != TrackingState.TRACKING) {
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
        if (landmarker == null || mutableState.value != TrackingState.TRACKING) {
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
            mutableTrackingData.tryEmit(FaceTrackingData.notTracking(timestampMs))
            return
        }

        val primaryLandmarks = FaceLandmarkerResultMapper.extractLandmarks(result, faceIndex = 0)
        val primaryBlendShapes = FaceLandmarkerResultMapper.extractBlendShapes(result, faceIndex = 0)
        val processedPrimary = processPrimaryPipeline(primaryBlendShapes, primaryLandmarks, timestampMs) ?: return
        val primaryHeadTransform = FaceLandmarkerResultMapper.extractHeadTransform(result, faceIndex = 0)

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

        mutableTrackingData.tryEmit(data)
    }

    /** Whether a frame at the given timestamp should be processed. */
    private fun shouldProcessFrame(timestampMs: Long): Boolean {
        return if (powerModeManager != null) {
            powerModeManager.shouldProcess(timestampMs)
        } else {
            frameThrottler.shouldProcess(timestampMs, thermalMonitor.thermalState.value)
        }
    }

    /** Runs enhance -> calibrate -> smooth on the primary face. Returns null if tracker is released. */
    private fun processPrimaryPipeline(
        blendShapes: BlendShapeData,
        landmarks: List<FaceLandmark>,
        timestampMs: Long,
    ): BlendShapeData? {
        var result = blendShapes
        return pipelineLock.withLock {
            if (isReleased()) return null

            val enhancerDisabled = if (powerModeManager != null) {
                powerModeManager.shouldDisableEnhancer()
            } else {
                frameThrottler.shouldDisableEnhancer(thermalMonitor.thermalState.value)
            }
            if (!enhancerDisabled) {
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
                val landmarks = FaceLandmarkerResultMapper.extractLandmarks(result, index)
                val headTransform = FaceLandmarkerResultMapper.extractHeadTransform(result, index)
                // Secondary faces: apply only smoothing (no per-face calibration/enhancement state)
                var blendShapes = FaceLandmarkerResultMapper.extractBlendShapes(result, index)
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
    }
}
