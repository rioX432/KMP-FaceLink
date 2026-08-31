@file:Suppress("TooManyFunctions")

package io.github.kmpfacelink.internal.mediapipe

import io.github.kmpfacelink.api.FaceTracker
import io.github.kmpfacelink.api.TrackingState
import io.github.kmpfacelink.model.CameraFacing
import io.github.kmpfacelink.model.FaceTrackerConfig
import io.github.kmpfacelink.model.FaceTrackingData
import io.github.kmpfacelink.model.InferenceDelegate
import io.github.kmpfacelink.model.SmoothingConfig
import io.github.kmpfacelink.model.TrackedFace
import io.github.kmpfacelink.model.emptyBlendShapeData
import io.github.kmpfacelink.model.filterByActive
import io.github.kmpfacelink.util.FaceLinkLogger
import io.github.kmpfacelink.util.GazeEstimator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Face tracker for Wasm/JS that uses MediaPipe Tasks Vision FaceLandmarker.
 *
 * Acquires the browser camera via `getUserMedia()`, feeds video frames to
 * MediaPipe's FaceLandmarker running in the browser, and maps the results
 * to [FaceTrackingData] emitted via Kotlin Flow.
 *
 * The host HTML page must set up `globalThis.__faceLinkVision` with the
 * MediaPipe Tasks Vision module before calling [start].
 */
internal class MediaPipeWasmFaceTracker(
    private val config: FaceTrackerConfig,
) : FaceTracker {

    private val mutableTrackingData = MutableSharedFlow<FaceTrackingData>(
        replay = 1,
        extraBufferCapacity = 1,
    )
    override val trackingData: Flow<FaceTrackingData> = mutableTrackingData.asSharedFlow()

    private val _state = MutableStateFlow(TrackingState.IDLE)
    override val state: StateFlow<TrackingState> = _state.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    override val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private var landmarker: JsFaceLandmarker? = null
    private var mediaStream: JsMediaStream? = null
    private var videoElement: JsVideoElement? = null
    private var trackingScope: CoroutineScope? = null
    private var trackingJob: Job? = null
    private var released = false
    private var frameCounter = 0
    private var activeBackend: GpuBackend = GpuBackend.NONE

    override suspend fun start() {
        checkNotReleased("start")
        if (_state.value == TrackingState.TRACKING || _state.value == TrackingState.STARTING) return

        _state.value = TrackingState.STARTING
        _errorMessage.value = null

        try {
            initializeAndTrack()
        } catch (@Suppress("TooGenericExceptionCaught") e: Throwable) {
            FaceLinkLogger.e(TAG, "Failed to start face tracking", e)
            _errorMessage.value = e.message ?: "Unknown error starting face tracking"
            _state.value = TrackingState.ERROR
        }
    }

    private suspend fun initializeAndTrack() {
        val backend = resolveBackend()
        activeBackend = backend
        val delegate = WebGpuCapability.resolveDelegate(backend)
        FaceLinkLogger.d(TAG, "Initializing FaceLandmarker with delegate=$delegate (backend=$backend)")

        val lm = createLandmarkerWithFallback(delegate)
        landmarker = lm

        val stream = awaitJsPromise<JsMediaStream>(getUserMediaJs())
        mediaStream = stream

        val video = awaitJsPromise<JsVideoElement>(createVideoElementJs(stream))
        videoElement = video

        _state.value = TrackingState.TRACKING
        startTrackingLoop(lm, video)
    }

    private suspend fun resolveBackend(): GpuBackend = when (config.performanceConfig.inferenceDelegate) {
        InferenceDelegate.CPU -> GpuBackend.NONE
        InferenceDelegate.GPU, InferenceDelegate.AUTO,
        InferenceDelegate.NPU,
        -> WebGpuCapability.detect()
    }

    private suspend fun createLandmarkerWithFallback(delegate: String): JsFaceLandmarker =
        try {
            awaitJsPromise(
                createFaceLandmarkerJs(
                    config.modelAssetPath,
                    config.detectionConfig.maxFaces,
                    delegate,
                ),
            )
        } catch (@Suppress("TooGenericExceptionCaught") e: Throwable) {
            if (delegate != CPU_DELEGATE) {
                FaceLinkLogger.w(TAG, "GPU delegate failed, falling back to CPU", e)
                activeBackend = GpuBackend.NONE
                awaitJsPromise(
                    createFaceLandmarkerJs(
                        config.modelAssetPath,
                        config.detectionConfig.maxFaces,
                        CPU_DELEGATE,
                    ),
                )
            } else {
                throw e
            }
        }

    private fun startTrackingLoop(lm: JsFaceLandmarker, video: JsVideoElement) {
        val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
        trackingScope = scope
        trackingJob = scope.launch {
            runTrackingLoop(lm, video)
        }
    }

    private suspend fun runTrackingLoop(
        lm: JsFaceLandmarker,
        video: JsVideoElement,
    ) {
        val scope = trackingScope ?: return
        while (scope.isActive && _state.value == TrackingState.TRACKING) {
            processFrame(lm, video)
            awaitAnimationFrame()
        }
    }

    private fun processFrame(lm: JsFaceLandmarker, video: JsVideoElement) {
        frameCounter++
        val timestamp = frameCounter
        val jsResult = detectForVideoJs(lm, video, timestamp) ?: return

        @Suppress("UNCHECKED_CAST", "UNCHECKED_CAST_TO_EXTERNAL_INTERFACE")
        val result = jsResult as JsFaceLandmarkerResult
        val faceCount = getResultFaceCountJs(result)

        if (faceCount == 0) {
            mutableTrackingData.tryEmit(FaceTrackingData.notTracking(timestamp.toLong()))
            return
        }

        emitTrackingData(result, faceCount, video, timestamp)
    }

    private fun emitTrackingData(
        result: JsFaceLandmarkerResult,
        faceCount: Int,
        video: JsVideoElement,
        timestamp: Int,
    ) {
        val rawPrimaryBlendShapes = WasmResultMapper.extractBlendShapes(result, 0)
        val primaryBlendShapes = rawPrimaryBlendShapes.filterByActive(config.activeBlendShapes)
        val primaryLandmarks = WasmResultMapper.extractLandmarks(result, 0)
        val primaryTransform = WasmResultMapper.extractHeadTransform(result, 0)
        val primaryGaze = GazeEstimator.estimate(rawPrimaryBlendShapes)

        val faces = buildTrackedFaces(result, faceCount)

        val data = FaceTrackingData(
            blendShapes = primaryBlendShapes,
            headTransform = primaryTransform,
            gazeData = primaryGaze,
            landmarks = primaryLandmarks,
            sourceImageWidth = getVideoWidthJs(video),
            sourceImageHeight = getVideoHeightJs(video),
            timestampMs = timestamp.toLong(),
            isTracking = true,
            faces = faces,
        )
        mutableTrackingData.tryEmit(data)
    }

    private fun buildTrackedFaces(
        result: JsFaceLandmarkerResult,
        faceCount: Int,
    ): List<TrackedFace> = List(faceCount) { i ->
        val rawBlendShapes = WasmResultMapper.extractBlendShapes(result, i)
        TrackedFace(
            faceIndex = i,
            blendShapes = rawBlendShapes.filterByActive(config.activeBlendShapes),
            headTransform = WasmResultMapper.extractHeadTransform(result, i),
            gazeData = GazeEstimator.estimate(rawBlendShapes),
            landmarks = WasmResultMapper.extractLandmarks(result, i),
        )
    }

    override suspend fun stop() {
        if (_state.value == TrackingState.STOPPED || _state.value == TrackingState.RELEASED) return
        trackingJob?.cancel()
        trackingJob = null
        trackingScope?.cancel()
        trackingScope = null
        _state.value = TrackingState.STOPPED
    }

    override fun release() {
        if (released) return
        released = true

        trackingJob?.cancel()
        trackingScope?.cancel()

        landmarker?.let { closeFaceLandmarkerJs(it) }
        mediaStream?.let { stopMediaStreamJs(it) }
        videoElement?.let { removeVideoElementJs(it) }

        landmarker = null
        mediaStream = null
        videoElement = null
        trackingJob = null
        trackingScope = null

        _state.value = TrackingState.RELEASED
    }

    override fun resetCalibration() {
        checkNotReleased("resetCalibration")
        // No calibration state in Wasm tracker yet
    }

    override fun resetSmoothing() {
        checkNotReleased("resetSmoothing")
        // No smoothing state in Wasm face tracker yet
    }

    override fun updateSmoothing(config: SmoothingConfig) {
        checkNotReleased("updateSmoothing")
        // Smoothing not yet implemented for Wasm tracker
    }

    override suspend fun switchCamera(facing: CameraFacing) {
        checkNotReleased("switchCamera")
        // Browser getUserMedia facing mode is set at start;
        // switching would require stop + restart with new constraints
        FaceLinkLogger.w(TAG, "switchCamera not yet supported on Wasm")
    }

    private fun checkNotReleased(action: String) {
        check(!released) { "Cannot $action on a released tracker" }
    }

    private companion object {
        private const val TAG = "MediaPipeWasmFaceTracker"
        private const val CPU_DELEGATE = "CPU"
    }
}
