package io.github.kmpfacelink.internal

import android.content.pm.PackageManager
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import com.google.mediapipe.framework.image.MPImage
import com.google.mediapipe.tasks.core.Delegate
import com.google.mediapipe.tasks.vision.facelandmarker.FaceLandmarker
import com.google.mediapipe.tasks.vision.facelandmarker.FaceLandmarkerResult
import io.github.kmpfacelink.api.FaceTracker
import io.github.kmpfacelink.api.PlatformContext
import io.github.kmpfacelink.api.TrackingState
import io.github.kmpfacelink.model.CameraFacing
import io.github.kmpfacelink.model.FaceTrackerConfig
import io.github.kmpfacelink.model.FaceTrackingData
import io.github.kmpfacelink.model.InferenceRunningMode
import io.github.kmpfacelink.model.QualityPreset
import io.github.kmpfacelink.model.SessionInstanceId
import io.github.kmpfacelink.model.SmoothingConfig
import io.github.kmpfacelink.model.TrackingContextEpoch
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
    private val sessionInstanceId: SessionInstanceId = newSecureSessionInstanceId(),
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

    private var resultProcessor = FaceResultProcessor(
        config = config,
        pipelineLock = pipelineLock,
        isReleased = ::isReleased,
        isEnhancerDisabled = ::isEnhancerDisabled,
    )
    private val interpolationHandler = FrameInterpolationHandler(config)
    private var currentSmoothingConfig: SmoothingConfig = config.smoothingConfig
    private var nextEpochId: ULong = 1u
    private var currentEpoch: TrackingContextEpoch? = null
    private var resolvedDelegate: Delegate? = null
    private var modelSha256: String? = null
    private val submittedFrameContexts = SubmittedFrameContextStore()

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
            // The live camera path receives results through a callback, which MediaPipe only
            // provides in LIVE_STREAM. IMAGE and VIDEO exist for re-running recorded frames
            // offline, where the caller drives detection itself; picking one here would fail
            // deeper inside MediaPipe with a much less obvious message.
            require(
                config.performanceConfig.runningMode == InferenceRunningMode.LIVE_STREAM,
            ) {
                "Live camera tracking requires runningMode = LIVE_STREAM, but " +
                    "${config.performanceConfig.runningMode} was configured. IMAGE and VIDEO " +
                    "are for offline re-inference over recorded frames."
            }
            val modelHash = modelAssetSha256(platformContext, config.modelAssetPath)
            val created = FaceLandmarkerFactory.create(platformContext, config, ::onFaceLandmarkerResult)
            pipelineLock.withLock {
                faceLandmarker = created.landmarker
                resolvedDelegate = created.resolvedDelegate
                modelSha256 = modelHash
                rotateEpochLocked(currentAnalysisWidth, currentAnalysisHeight, isFrontCamera)
            }
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
        pipelineLock.withLock {
            submittedFrameContexts.clear()
            currentEpoch = null
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
            submittedFrameContexts.clear()
            currentEpoch = null
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
            resultProcessor = resultProcessor.forkResetCalibration()
            forceRotateEpochLocked()
        }
    }

    override fun resetSmoothing() {
        pipelineLock.withLock {
            checkNotReleased("reset smoothing")
            resultProcessor = resultProcessor.forkResetSmoothing()
            forceRotateEpochLocked()
        }
    }

    override fun updateSmoothing(config: SmoothingConfig) {
        pipelineLock.withLock {
            checkNotReleased("update smoothing")
            resultProcessor = resultProcessor.forkWithSmoothing(config)
            currentSmoothingConfig = config
            forceRotateEpochLocked()
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
            submitWithContext(this, mpImage, ts)
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
            submitWithContext(this, mpImage, ts)
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
        val submittedContext = submittedFrameContexts.take(timestampMs)
        if (submittedContext == null) {
            FaceLinkLogger.w(TAG, "Dropping result without submission-time context: timestamp=$timestampMs")
            return
        }
        val epoch = submittedContext.epoch
        val submittedProcessor = submittedContext.resultProcessor

        if (!result.faceBlendshapes().isPresent || result.faceBlendshapes().get().isEmpty()) {
            mutableTrackingData.tryEmit(FaceTrackingData.notTracking(timestampMs))
            return
        }

        val primaryLandmarks = FaceLandmarkerResultMapper.extractLandmarks(result, faceIndex = 0)
        val primaryBlendShapes = FaceLandmarkerResultMapper.extractBlendShapes(result, faceIndex = 0)
        val processedPrimary = submittedProcessor.processPrimary(
            primaryBlendShapes,
            primaryLandmarks,
            timestampMs,
            epoch,
        )
            ?: return
        val primaryHeadTransform = FaceLandmarkerResultMapper.extractHeadTransform(result, faceIndex = 0)

        val faces = submittedProcessor.buildTrackedFaces(
            result,
            processedPrimary,
            primaryLandmarks,
            primaryHeadTransform,
            timestampMs,
            epoch,
        )

        val primaryConfidence = faces.firstOrNull()?.confidence ?: 1.0f
        updateCurrentRuntimeState(
            processedPrimary,
            primaryHeadTransform,
            timestampMs,
            primaryConfidence,
            epoch,
        )

        val data = FaceTrackingData(
            blendShapes = processedPrimary.blendShapes,
            headTransform = primaryHeadTransform,
            gazeData = GazeEstimator.estimate(processedPrimary.blendShapes),
            landmarks = primaryLandmarks,
            sourceImageWidth = cameraPipeline.lastImageWidth,
            sourceImageHeight = cameraPipeline.lastImageHeight,
            timestampMs = timestampMs,
            isTracking = true,
            faces = faces,
            thermalState = thermalMonitor.thermalState.value,
            contextEpoch = epoch,
        )

        mutableTrackingData.tryEmit(data)
    }

    @Suppress("LongParameterList")
    private fun updateCurrentRuntimeState(
        processedPrimary: FaceResultProcessor.ProcessedPrimary,
        primaryHeadTransform: io.github.kmpfacelink.model.HeadTransform,
        timestampMs: Long,
        primaryConfidence: Float,
        epoch: TrackingContextEpoch,
    ) {
        val isCurrentEpoch = pipelineLock.withLock {
            currentEpoch?.epochFingerprint == epoch.epochFingerprint
        }
        if (!isCurrentEpoch) return
        interpolationHandler.onFrameProcessed(
            processedPrimary.blendShapes,
            primaryHeadTransform,
            timestampMs,
            primaryConfidence,
            epoch,
            processedPrimary.provenance,
        )
        powerModeManager?.onFrameProcessed(processedPrimary.blendShapes, timestampMs)
    }

    /** Whether a frame at the given timestamp should be processed. */
    private fun shouldProcessFrame(timestampMs: Long): Boolean {
        val throttleAllowed = if (powerModeManager != null) {
            powerModeManager.shouldProcess(timestampMs)
        } else {
            frameThrottler.shouldProcess(timestampMs, thermalMonitor.thermalState.value)
        }
        if (!throttleAllowed) return false

        val epoch = pipelineLock.withLock { currentEpoch } ?: return true
        if (interpolationHandler.shouldSkip(epoch)) {
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
            pipelineLock.withLock {
                cameraManager?.unbindAll()
                rotateEpochLocked(newWidth, newHeight, isFrontCamera)
            }
            startCamera()
        }
    }

    @Suppress("TooGenericExceptionCaught")
    private fun submitWithContext(
        landmarker: FaceLandmarker,
        image: MPImage,
        timestampMs: Long,
    ) {
        pipelineLock.withLock {
            val epoch = ensureSubmissionEpochLocked(
                inputWidth = cameraPipeline.lastImageWidth.takeIf { it > 0 } ?: currentAnalysisWidth,
                inputHeight = cameraPipeline.lastImageHeight.takeIf { it > 0 } ?: currentAnalysisHeight,
                mirroredBeforeInference = isFrontCamera,
            )
            submittedFrameContexts.record(
                timestampMs,
                SubmittedFrameContext(epoch = epoch, resultProcessor = resultProcessor),
            )
        }
        try {
            landmarker.detectAsync(image, timestampMs)
        } catch (error: RuntimeException) {
            submittedFrameContexts.discard(timestampMs)
            throw error
        }
    }

    private fun ensureSubmissionEpochLocked(
        inputWidth: Int,
        inputHeight: Int,
        mirroredBeforeInference: Boolean,
    ): TrackingContextEpoch {
        val delegate = checkNotNull(resolvedDelegate) { "Resolved delegate is unavailable" }
        val modelHash = checkNotNull(modelSha256) { "Model fingerprint is unavailable" }
        val existing = currentEpoch
        val candidate = buildAndroidTrackingEpoch(
            epochId = existing?.epochId ?: nextEpochId,
            sessionInstanceId = sessionInstanceId,
            config = effectiveConfig(),
            resolvedDelegate = delegate,
            modelSha256 = modelHash,
            inputWidth = inputWidth,
            inputHeight = inputHeight,
            mirroredBeforeInference = mirroredBeforeInference,
        )
        if (existing != null && candidate.configurationFingerprint == existing.configurationFingerprint) {
            return existing
        }
        return rotateEpochLocked(inputWidth, inputHeight, mirroredBeforeInference)
    }

    private fun rotateEpochLocked(
        inputWidth: Int,
        inputHeight: Int,
        mirroredBeforeInference: Boolean,
    ): TrackingContextEpoch {
        val epoch = buildAndroidTrackingEpoch(
            epochId = nextEpochId++,
            sessionInstanceId = sessionInstanceId,
            config = effectiveConfig(),
            resolvedDelegate = checkNotNull(resolvedDelegate) { "Resolved delegate is unavailable" },
            modelSha256 = checkNotNull(modelSha256) { "Model fingerprint is unavailable" },
            inputWidth = inputWidth,
            inputHeight = inputHeight,
            mirroredBeforeInference = mirroredBeforeInference,
        )
        currentEpoch = epoch
        interpolationHandler.rotateTo(epoch)
        return epoch
    }

    private fun forceRotateEpochLocked() {
        if (resolvedDelegate == null || modelSha256 == null) return
        rotateEpochLocked(
            inputWidth = cameraPipeline.lastImageWidth.takeIf { it > 0 } ?: currentAnalysisWidth,
            inputHeight = cameraPipeline.lastImageHeight.takeIf { it > 0 } ?: currentAnalysisHeight,
            mirroredBeforeInference = isFrontCamera,
        )
    }

    private fun effectiveConfig(): FaceTrackerConfig = config.copy(smoothingConfig = currentSmoothingConfig)

    companion object {
        private const val TAG = "MediaPipeFaceTracker"
    }
}
