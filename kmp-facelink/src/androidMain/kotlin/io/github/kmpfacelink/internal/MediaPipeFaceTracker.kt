package io.github.kmpfacelink.internal

import android.content.pm.PackageManager
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
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
import io.github.kmpfacelink.model.filterByActive
import io.github.kmpfacelink.util.BlendShapeEnhancer
import io.github.kmpfacelink.util.BlendShapeInterpolator
import io.github.kmpfacelink.util.BlendShapeSmoother
import io.github.kmpfacelink.util.Calibrator
import io.github.kmpfacelink.util.ConfidenceFrameStrategy
import io.github.kmpfacelink.util.FaceLinkLogger
import io.github.kmpfacelink.util.FrameDecision
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
import kotlin.concurrent.atomics.ExperimentalAtomicApi

// Lifecycle (start/stop/release/switchCamera), config (resetCalibration/updateSmoothing),
// camera pipeline (startCamera/processFrame/processSharedFrame), result assembly
// (onFaceLandmarkerResult/processPrimaryPipeline/buildTrackedFaces), and frame skip
// interpolation (emitInterpolatedFrame) form a cohesive unit.
// Raw result extraction is delegated to FaceLandmarkerResultMapper.
@Suppress("TooManyFunctions")
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
    private val frameSkipStrategy = ConfidenceFrameStrategy(config.frameSkipConfig)
    private val interpolator = BlendShapeInterpolator()
    private var lastConfidence: Float = 0f
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

    @Volatile
    internal var currentCameraFacing: CameraFacing = config.cameraFacing
    private val isFrontCamera: Boolean
        get() = currentCameraFacing == CameraFacing.FRONT

    private var faceLandmarker: FaceLandmarker? = null
    private var previewSurfaceProvider: Preview.SurfaceProvider? = null

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
            FaceLinkLogger.e(TAG, "Failed to start face tracking", e)
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
        val landmarker = faceLandmarker
        val callbacks = DetectionCallbacks(
            isLandmarkerReady = landmarker != null,
            shouldProcess = ::shouldProcessFrame,
            detectAsync = { mpImage, ts -> landmarker!!.detectAsync(mpImage, ts) },
        )
        cameraPipeline.processSharedFrame(
            bitmap = bitmap,
            width = width,
            height = height,
            timestampMs = timestampMs,
            context = SharedFrameContext(mutableState.value, callbacks),
        )
    }

    private fun processFrame(imageProxy: ImageProxy) {
        val landmarker = faceLandmarker
        val callbacks = DetectionCallbacks(
            isLandmarkerReady = landmarker != null,
            shouldProcess = ::shouldProcessFrame,
            detectAsync = { mpImage, ts -> landmarker!!.detectAsync(mpImage, ts) },
        )
        cameraPipeline.processFrame(
            imageProxy = imageProxy,
            trackingState = mutableState.value,
            isFrontCamera = isFrontCamera,
            callbacks = callbacks,
        )
    }

    private fun onFaceLandmarkerResult(
        result: FaceLandmarkerResult,
        @Suppress("UNUSED_PARAMETER") input: com.google.mediapipe.framework.image.MPImage,
    ) {
        // Return the bitmap now that MediaPipe is done processing
        cameraPipeline.returnBitmapAfterResult(result.timestampMs())

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

        // Update interpolator and confidence strategy with processed data
        val primaryConfidence = faces.firstOrNull()?.confidence ?: 1.0f
        lastConfidence = primaryConfidence
        interpolator.onFrameProcessed(processedPrimary, primaryHeadTransform, timestampMs)
        frameSkipStrategy.onFrameProcessed(processedPrimary, primaryConfidence)

        val data = FaceTrackingData(
            blendShapes = processedPrimary,
            headTransform = primaryHeadTransform,
            gazeData = GazeEstimator.estimate(processedPrimary),
            landmarks = primaryLandmarks,
            sourceImageWidth = cameraPipeline.lastImageWidth,
            sourceImageHeight = cameraPipeline.lastImageHeight,
            timestampMs = timestampMs,
            isTracking = true,
            faces = faces,
            thermalState = thermalMonitor.thermalState.value,
        )

        mutableTrackingData.tryEmit(data)
    }

    /** Whether a frame at the given timestamp should be processed. */
    private fun shouldProcessFrame(timestampMs: Long): Boolean {
        val throttleAllowed = if (powerModeManager != null) {
            powerModeManager.shouldProcess(timestampMs)
        } else {
            frameThrottler.shouldProcess(timestampMs, thermalMonitor.thermalState.value)
        }
        if (!throttleAllowed) return false

        // Confidence-guided skip: if conditions are stable, emit interpolated data
        val decision = frameSkipStrategy.evaluate(lastConfidence)
        if (decision == FrameDecision.SKIP) {
            emitInterpolatedFrame(timestampMs)
            return false
        }
        return true
    }

    private fun emitInterpolatedFrame(timestampMs: Long) {
        val blendShapes = interpolator.interpolateBlendShapes(timestampMs) ?: return
        val headTransform = interpolator.interpolateTransform(timestampMs) ?: return
        val filtered = blendShapes.filterByActive(config.activeBlendShapes)

        val data = FaceTrackingData(
            blendShapes = filtered,
            headTransform = headTransform,
            gazeData = GazeEstimator.estimate(filtered),
            sourceImageWidth = cameraPipeline.lastImageWidth,
            sourceImageHeight = cameraPipeline.lastImageHeight,
            timestampMs = timestampMs,
            isTracking = true,
            faces = listOf(
                TrackedFace(
                    faceIndex = 0,
                    blendShapes = filtered,
                    headTransform = headTransform,
                    gazeData = GazeEstimator.estimate(filtered),
                    confidence = lastConfidence,
                ),
            ),
            thermalState = thermalMonitor.thermalState.value,
        )
        mutableTrackingData.tryEmit(data)
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
            HashMap(result).filterByActive(config.activeBlendShapes)
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
                blendShapes = blendShapes.filterByActive(config.activeBlendShapes)
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
