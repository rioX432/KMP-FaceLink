package io.github.kmpfacelink.internal

import android.content.pm.PackageManager
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import com.google.mediapipe.tasks.vision.facelandmarker.FaceLandmarker
import com.google.mediapipe.tasks.vision.facelandmarker.FaceLandmarkerResult
import io.github.kmpfacelink.api.FaceTracker
import io.github.kmpfacelink.api.PlatformContext
import io.github.kmpfacelink.api.TrackingState
import io.github.kmpfacelink.model.CameraFacing
import io.github.kmpfacelink.model.FaceTrackerConfig
import io.github.kmpfacelink.model.FaceTrackingData
import io.github.kmpfacelink.model.QualityPreset
import io.github.kmpfacelink.model.SmoothingConfig
import io.github.kmpfacelink.util.FaceLinkLogger
import io.github.kmpfacelink.util.FrameThrottler
import io.github.kmpfacelink.util.GazeEstimator
import io.github.kmpfacelink.util.PowerModeManager
import io.github.kmpfacelink.util.withLock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.withContext
import java.util.concurrent.ExecutionException
import java.util.concurrent.Executors
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.TimeUnit
import kotlin.concurrent.atomics.ExperimentalAtomicApi

@Suppress("TooManyFunctions") // Interface overrides + thermal quality policy helpers
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

    private var qualityScope: CoroutineScope? = null
    private var qualityCollectJob: Job? = null

    @Volatile
    internal var currentAnalysisWidth: Int = config.analysisWidth

    @Volatile
    internal var currentAnalysisHeight: Int = config.analysisHeight

    private val resultProcessor = FaceResultProcessor(
        config = config,
        pipelineLock = pipelineLock,
        isReleased = ::isReleased,
        isEnhancerDisabled = ::isEnhancerDisabled,
    )
    private val interpolationHandler = FrameInterpolationHandler(config)

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
            startQualityPolicyCollection()
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
        stopQualityPolicyCollection()
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
        stopQualityPolicyCollection()
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
            resultProcessor.resetCalibration()
        }
    }

    override fun resetSmoothing() {
        pipelineLock.withLock {
            checkNotReleased("reset smoothing")
            resultProcessor.resetSmoothing()
        }
    }

    override fun updateSmoothing(config: SmoothingConfig) {
        pipelineLock.withLock {
            checkNotReleased("update smoothing")
            resultProcessor.updateSmoothing(config)
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
                analysisWidth = currentAnalysisWidth,
                analysisHeight = currentAnalysisHeight,
                surfaceProvider = previewSurfaceProvider,
            ),
            onSharedFrame = ::processSharedFrame,
            onImageProxy = ::processFrame,
        )
    }

    @Suppress("UNUSED_PARAMETER") // SharedFrameHandler signature
    private fun processSharedFrame(bitmap: android.graphics.Bitmap, width: Int, height: Int, timestampMs: Long) {
        val callbacks = detectionCallbacks(faceLandmarker, ::shouldProcessFrame) { mpImage, ts ->
            detectAsync(mpImage, ts)
        }
        cameraPipeline.processSharedFrame(
            bitmap = bitmap,
            width = width,
            height = height,
            timestampMs = timestampMs,
            context = SharedFrameContext(mutableState.value, callbacks),
        )
    }

    private fun processFrame(imageProxy: ImageProxy) {
        val callbacks = detectionCallbacks(faceLandmarker, ::shouldProcessFrame) { mpImage, ts ->
            detectAsync(mpImage, ts)
        }
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
        cameraPipeline.returnBitmapAfterResult(result.timestampMs())

        val timestampMs = result.timestampMs()

        if (!result.faceBlendshapes().isPresent || result.faceBlendshapes().get().isEmpty()) {
            mutableTrackingData.tryEmit(FaceTrackingData.notTracking(timestampMs))
            return
        }

        val primaryLandmarks = FaceLandmarkerResultMapper.extractLandmarks(result, faceIndex = 0)
        val primaryBlendShapes = FaceLandmarkerResultMapper.extractBlendShapes(result, faceIndex = 0)
        val processedPrimary = resultProcessor.processPrimary(primaryBlendShapes, primaryLandmarks, timestampMs)
            ?: return
        val primaryHeadTransform = FaceLandmarkerResultMapper.extractHeadTransform(result, faceIndex = 0)

        val faces = resultProcessor.buildTrackedFaces(
            result,
            processedPrimary,
            primaryLandmarks,
            primaryHeadTransform,
            timestampMs,
        )

        val primaryConfidence = faces.firstOrNull()?.confidence ?: 1.0f
        interpolationHandler.onFrameProcessed(processedPrimary, primaryHeadTransform, timestampMs, primaryConfidence)
        powerModeManager?.onFrameProcessed(processedPrimary, timestampMs)

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

        if (interpolationHandler.shouldSkip()) {
            interpolationHandler.buildInterpolatedData(
                timestampMs = timestampMs,
                imageWidth = cameraPipeline.lastImageWidth,
                imageHeight = cameraPipeline.lastImageHeight,
                thermalState = thermalMonitor.thermalState.value,
            )?.let { mutableTrackingData.tryEmit(it) }
            return false
        }
        return true
    }

    private fun isEnhancerDisabled(): Boolean = if (powerModeManager != null) {
        powerModeManager.shouldDisableEnhancer()
    } else {
        frameThrottler.shouldDisableEnhancer(thermalMonitor.thermalState.value)
    }

    private fun startQualityPolicyCollection() {
        if (powerModeManager == null) return
        val scope = CoroutineScope(Dispatchers.Main.immediate + SupervisorJob())
        qualityScope = scope

        val initialPreset = resolveInitialPreset()
        powerModeManager.startQualityPolicy(initialPreset, scope)

        qualityCollectJob = powerModeManager.recommendedPreset
            ?.drop(1) // skip initial value — already applied
            ?.distinctUntilChanged()
            ?.onEach { preset -> applyPresetChange(preset) }
            ?.launchIn(scope)
    }

    private fun stopQualityPolicyCollection() {
        qualityCollectJob?.cancel()
        qualityCollectJob = null
        qualityScope?.cancel()
        qualityScope = null
    }

    @Suppress("MagicNumber")
    private fun resolveInitialPreset(): QualityPreset = when {
        config.analysisWidth <= QualityPreset.Low.analysisWidth &&
            config.analysisHeight <= QualityPreset.Low.analysisHeight -> QualityPreset.Low
        config.analysisWidth <= QualityPreset.Medium.analysisWidth &&
            config.analysisHeight <= QualityPreset.Medium.analysisHeight -> QualityPreset.Medium
        else -> QualityPreset.High
    }

    private suspend fun applyPresetChange(preset: QualityPreset) {
        val newWidth = preset.analysisWidth
        val newHeight = preset.analysisHeight
        if (newWidth == currentAnalysisWidth && newHeight == currentAnalysisHeight) return

        FaceLinkLogger.d(
            TAG,
            "Thermal quality change: ${currentAnalysisWidth}x$currentAnalysisHeight -> ${newWidth}x$newHeight",
        )
        currentAnalysisWidth = newWidth
        currentAnalysisHeight = newHeight

        // Restart camera with new analysis resolution
        if (mutableState.value == TrackingState.TRACKING) {
            pipelineLock.withLock { cameraManager?.unbindAll() }
            startCamera()
        }
    }

    companion object {
        private const val TAG = "MediaPipeFaceTracker"
    }
}
