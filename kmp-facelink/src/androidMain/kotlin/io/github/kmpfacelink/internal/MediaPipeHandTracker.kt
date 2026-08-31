package io.github.kmpfacelink.internal

import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.core.Delegate
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarker
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarkerResult
import io.github.kmpfacelink.api.HandTracker
import io.github.kmpfacelink.api.PlatformContext
import io.github.kmpfacelink.internal.depth.HandDepthEnhancer
import io.github.kmpfacelink.model.CameraFacing
import io.github.kmpfacelink.model.HandGesture
import io.github.kmpfacelink.model.HandLandmarkPoint
import io.github.kmpfacelink.model.HandTrackerConfig
import io.github.kmpfacelink.model.HandTrackingData
import io.github.kmpfacelink.model.Handedness
import io.github.kmpfacelink.model.SmoothingConfig
import io.github.kmpfacelink.model.TrackedHand
import io.github.kmpfacelink.util.DepthMap
import io.github.kmpfacelink.util.FaceLinkLogger
import io.github.kmpfacelink.util.FrameThrottler
import io.github.kmpfacelink.util.GestureClassifier
import io.github.kmpfacelink.util.HandLandmarkSmoother
import io.github.kmpfacelink.util.PowerModeManager
import io.github.kmpfacelink.util.createHandSmoother
import io.github.kmpfacelink.util.createSmoother
import io.github.kmpfacelink.util.withLock
import kotlin.concurrent.atomics.ExperimentalAtomicApi

/**
 * Hand tracker implementation using MediaPipe tasks-vision SDK (v0.10.32).
 * Version pinned for documentation parity with [VisionHandTracker.HAND_POSE_REVISION] on iOS.
 * Update when upgrading the mediapipe dependency in libs.versions.toml.
 */
@Suppress("TooManyFunctions")
@OptIn(ExperimentalAtomicApi::class)
internal class MediaPipeHandTracker(
    platformContext: PlatformContext,
    private val config: HandTrackerConfig,
    sharedCameraSession: SharedCameraSession? = null,
) : BaseMediaPipeTracker<HandTrackingData>(
    params = MediaPipeTrackerParams(
        platformContext = platformContext,
        sharedCameraSession = sharedCameraSession,
        analysisWidth = config.analysisWidth,
        analysisHeight = config.analysisHeight,
        initialCameraFacing = config.cameraFacing,
    ),
    thermalMonitor = AndroidThermalMonitor(platformContext.context),
    frameThrottler = FrameThrottler(config.thermalPolicy),
),
    HandTracker,
    PreviewableHandTracker {

    private var smoother: HandLandmarkSmoother? = config.createSmoother()
    private var handLandmarker: HandLandmarker? = null

    // Android-only depth Z-axis correction (#899). Created once on first start when opt-in via
    // config.depthEnhancement and a depth model is available; null keeps tracking unchanged.
    // Volatile: read on the MediaPipe result-callback thread, written on start/release.
    @Volatile
    private var depthEnhancer: HandDepthEnhancer? = null
    private var depthEnhancerInitialized = false

    private val batteryMonitor = AndroidBatteryMonitor(platformContext.context)
    private val powerModeManager: PowerModeManager? = config.powerMode?.let { mode ->
        PowerModeManager(
            powerMode = mode,
            thermalMonitor = thermalMonitor,
            batteryMonitor = batteryMonitor,
        )
    }

    override val tag: String = TAG

    override fun setSurfaceProvider(surfaceProvider: Preview.SurfaceProvider?) {
        super.setSurfaceProvider(surfaceProvider)
    }

    override suspend fun start() {
        performStart()
    }

    override suspend fun stop() {
        performStop()
    }

    override fun release() {
        performRelease()
    }

    override fun updateSmoothing(config: SmoothingConfig) {
        pipelineLock.withLock {
            checkNotReleased("update smoothing")
            smoother = config.createHandSmoother()
        }
    }

    override fun resetSmoothing() {
        pipelineLock.withLock {
            checkNotReleased("reset smoothing")
            smoother?.reset()
        }
    }

    override suspend fun switchCamera(facing: CameraFacing) {
        performSwitchCamera(facing)
    }

    override fun initLandmarker() {
        handLandmarker = DelegateResolver.createWithFallback(config.inferenceDelegate) { delegate ->
            createLandmarker(delegate)
        }
        initDepthEnhancerIfNeeded()
    }

    // Created lazily once (not per camera switch), and only when depth enhancement is opted in.
    private fun initDepthEnhancerIfNeeded() {
        if (!config.depthEnhancement || depthEnhancerInitialized) return
        depthEnhancerInitialized = true
        val enhancer = HandDepthEnhancer.create(platformContext.context)
        depthEnhancer = enhancer
        when {
            enhancer == null ->
                FaceLinkLogger.w(TAG, "depthEnhancement requested but no depth model available; disabled")
            // initLandmarker runs outside pipelineLock; if release() won the race, release the
            // just-created estimator here so its native worker thread cannot leak.
            isReleased() -> {
                enhancer.release()
                depthEnhancer = null
            }
        }
    }

    override fun closeLandmarker() {
        handLandmarker?.close()
        handLandmarker = null
    }

    override fun performExtraRelease() {
        batteryMonitor.release()
        powerModeManager?.release()
        depthEnhancer?.release()
        depthEnhancer = null
    }

    override fun buildDetectionCallbacks(): DetectionCallbacks =
        detectionCallbacks(
            landmarker = handLandmarker,
            shouldProcess = { ts ->
                if (powerModeManager != null) {
                    powerModeManager.shouldProcess(ts)
                } else {
                    frameThrottler.shouldProcess(ts, thermalMonitor.thermalState.value)
                }
            },
        ) { mpImage, ts -> detectAsync(mpImage, ts) }

    @Suppress("UNUSED_PARAMETER") // SharedFrameHandler signature
    override fun onSharedFrameReceived(
        bitmap: android.graphics.Bitmap,
        width: Int,
        height: Int,
        timestampMs: Long,
    ) {
        delegateSharedFrame(bitmap, width, height, timestampMs)
    }

    override fun onImageProxyReceived(imageProxy: ImageProxy) {
        delegateImageProxy(imageProxy)
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
            .setMinHandDetectionConfidence(config.minDetectionConfidence)
            .setMinTrackingConfidence(config.minTrackingConfidence)
            .setMinHandPresenceConfidence(config.minPresenceConfidence)
            .setResultListener(::onHandLandmarkerResult)
            .setErrorListener { error ->
                FaceLinkLogger.e(TAG, "HandLandmarker error: ${error.message}", error)
            }
            .build()

        return HandLandmarker.createFromOptions(platformContext.context, options)
    }

    private fun onHandLandmarkerResult(
        result: HandLandmarkerResult,
        @Suppress("UNUSED_PARAMETER") input: com.google.mediapipe.framework.image.MPImage,
    ) {
        val timestampMs = result.timestampMs()

        // Sample the depth map while the frame bitmap is still in-flight, then release it.
        val depthMap = estimateDepthForResult(timestampMs)
        cameraPipeline.returnBitmapAfterResult(timestampMs)

        if (result.landmarks().isEmpty()) {
            mutableTrackingData.tryEmit(HandTrackingData.notTracking(timestampMs))
            return
        }

        var hands = buildTrackedHands(result, depthMap)

        // Apply smoothing (protected by lock)
        val processedHands = pipelineLock.withLock {
            if (isReleased()) return
            smoother?.let { hands = it.smooth(hands, timestampMs).toMutableList() }
            hands.toList()
        }

        val data = HandTrackingData(
            hands = processedHands,
            sourceImageWidth = cameraPipeline.lastImageWidth,
            sourceImageHeight = cameraPipeline.lastImageHeight,
            timestampMs = timestampMs,
            isTracking = true,
            thermalState = thermalMonitor.thermalState.value,
        )

        mutableTrackingData.tryEmit(data)
    }

    /**
     * Runs depth inference for the current frame, or returns null when depth enhancement is
     * disabled/unavailable or the frame bitmap is no longer in-flight. Must run before
     * [CameraPipelineHelper.returnBitmapAfterResult] so the bitmap is still valid.
     */
    private fun estimateDepthForResult(timestampMs: Long): DepthMap? {
        val enhancer = depthEnhancer ?: return null
        val bitmap = cameraPipeline.peekInFlightBitmap(timestampMs) ?: return null
        return enhancer.estimateDepth(bitmap)
    }

    private fun buildTrackedHands(
        result: HandLandmarkerResult,
        depthMap: DepthMap?,
    ): MutableList<TrackedHand> {
        val hands = mutableListOf<TrackedHand>()
        for (i in result.landmarks().indices) {
            val landmarks = HandLandmarkMapper.mapLandmarks(result.landmarks()[i])
            // Classify gesture on the original z BEFORE depth correction, so enhanced z cannot
            // change gesture results (acceptance: gesture recognition unaffected).
            val (gesture, confidence) = classifyGesture(landmarks)
            val corrected = depthMap?.let { depthEnhancer?.enhance(landmarks, it) } ?: landmarks

            hands.add(
                TrackedHand(
                    handedness = resolveHandedness(result, i),
                    landmarks = corrected,
                    gesture = gesture,
                    gestureConfidence = confidence,
                ),
            )
        }
        return hands
    }

    private fun classifyGesture(
        landmarks: List<HandLandmarkPoint>,
    ): Pair<HandGesture, Float> = if (config.enableGestureRecognition) {
        GestureClassifier.classify(landmarks)
    } else {
        HandGesture.NONE to 0f
    }

    private fun resolveHandedness(result: HandLandmarkerResult, index: Int): Handedness {
        if (result.handedness().isEmpty() || index >= result.handedness().size) {
            return Handedness.UNKNOWN
        }
        val category = result.handedness()[index]
        return if (category.isNotEmpty()) {
            HandLandmarkMapper.mapHandedness(category[0].categoryName())
        } else {
            Handedness.UNKNOWN
        }
    }

    companion object {
        private const val TAG = "MediaPipeHandTracker"
        private const val MODEL_ASSET_PATH = "models/hand_landmarker.task"
    }
}
