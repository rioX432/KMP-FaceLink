@file:Suppress("TooManyFunctions")

package io.github.kmpfacelink.internal.mediapipe

import io.github.kmpfacelink.api.BodyTracker
import io.github.kmpfacelink.api.TrackingState
import io.github.kmpfacelink.model.BodyTrackerConfig
import io.github.kmpfacelink.model.BodyTrackingData
import io.github.kmpfacelink.model.CameraFacing
import io.github.kmpfacelink.model.InferenceDelegate
import io.github.kmpfacelink.model.SmoothingConfig
import io.github.kmpfacelink.util.FaceLinkLogger
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
 * Body tracker for Wasm/JS that uses MediaPipe Tasks Vision PoseLandmarker.
 *
 * Acquires the browser camera via `getUserMedia()`, feeds video frames to
 * MediaPipe's PoseLandmarker running in the browser, and maps the results
 * to [BodyTrackingData] emitted via Kotlin Flow.
 *
 * The host HTML page must set up `globalThis.__faceLinkVision` with the
 * MediaPipe Tasks Vision module before calling [start].
 */
internal class MediaPipeWasmBodyTracker(
    private val config: BodyTrackerConfig,
) : BodyTracker {

    private val mutableTrackingData = MutableSharedFlow<BodyTrackingData>(
        replay = 1,
        extraBufferCapacity = 1,
    )
    override val trackingData: Flow<BodyTrackingData> = mutableTrackingData.asSharedFlow()

    private val _state = MutableStateFlow(TrackingState.IDLE)
    override val state: StateFlow<TrackingState> = _state.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    override val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private var landmarker: JsPoseLandmarker? = null
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
            FaceLinkLogger.e(TAG, "Failed to start body tracking", e)
            _errorMessage.value = e.message ?: "Unknown error starting body tracking"
            _state.value = TrackingState.ERROR
        }
    }

    private suspend fun initializeAndTrack() {
        val backend = resolveBackend()
        activeBackend = backend
        val delegate = WebGpuCapability.resolveDelegate(backend)
        FaceLinkLogger.d(TAG, "Initializing PoseLandmarker with delegate=$delegate (backend=$backend)")

        val lm = createLandmarkerWithFallback(delegate)
        landmarker = lm

        val stream = awaitJsPromise<JsMediaStream>(getUserMediaJs())
        mediaStream = stream

        val video = awaitJsPromise<JsVideoElement>(createVideoElementJs(stream))
        videoElement = video

        _state.value = TrackingState.TRACKING
        startTrackingLoop(lm, video)
    }

    private suspend fun resolveBackend(): GpuBackend = when (config.inferenceDelegate) {
        InferenceDelegate.CPU -> GpuBackend.NONE
        InferenceDelegate.GPU, InferenceDelegate.AUTO,
        InferenceDelegate.NPU,
        @Suppress("DEPRECATION") InferenceDelegate.NNAPI,
        -> WebGpuCapability.detect()
    }

    private suspend fun createLandmarkerWithFallback(delegate: String): JsPoseLandmarker =
        try {
            awaitJsPromise(
                createPoseLandmarkerJs(DEFAULT_MODEL_URL, config.maxBodies, delegate),
            )
        } catch (@Suppress("TooGenericExceptionCaught") e: Throwable) {
            if (delegate != CPU_DELEGATE) {
                FaceLinkLogger.w(TAG, "GPU delegate failed, falling back to CPU", e)
                activeBackend = GpuBackend.NONE
                awaitJsPromise(
                    createPoseLandmarkerJs(DEFAULT_MODEL_URL, config.maxBodies, CPU_DELEGATE),
                )
            } else {
                throw e
            }
        }

    private fun startTrackingLoop(lm: JsPoseLandmarker, video: JsVideoElement) {
        val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
        trackingScope = scope
        trackingJob = scope.launch {
            runTrackingLoop(lm, video)
        }
    }

    private suspend fun runTrackingLoop(lm: JsPoseLandmarker, video: JsVideoElement) {
        val scope = trackingScope ?: return
        while (scope.isActive && _state.value == TrackingState.TRACKING) {
            processFrame(lm, video)
            awaitAnimationFrame()
        }
    }

    private fun processFrame(lm: JsPoseLandmarker, video: JsVideoElement) {
        frameCounter++
        val timestamp = frameCounter
        val jsResult = detectPoseForVideoJs(lm, video, timestamp) ?: return

        @Suppress("UNCHECKED_CAST")
        val result = jsResult as JsPoseLandmarkerResult
        val bodyCount = getBodyCountJs(result)

        if (bodyCount == 0) {
            mutableTrackingData.tryEmit(BodyTrackingData.notTracking(timestamp.toLong()))
            return
        }

        emitTrackingData(result, bodyCount, video, timestamp)
    }

    private fun emitTrackingData(
        result: JsPoseLandmarkerResult,
        bodyCount: Int,
        video: JsVideoElement,
        timestamp: Int,
    ) {
        val bodies = List(bodyCount) { i -> WasmBodyResultMapper.buildTrackedBody(result, i) }
        val data = BodyTrackingData(
            bodies = bodies,
            sourceImageWidth = getVideoWidthJs(video),
            sourceImageHeight = getVideoHeightJs(video),
            timestampMs = timestamp.toLong(),
            isTracking = true,
        )
        mutableTrackingData.tryEmit(data)
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

        landmarker?.let { closePoseLandmarkerJs(it) }
        mediaStream?.let { stopMediaStreamJs(it) }
        videoElement?.let { removeVideoElementJs(it) }

        landmarker = null
        mediaStream = null
        videoElement = null
        trackingJob = null
        trackingScope = null

        _state.value = TrackingState.RELEASED
    }

    override fun resetSmoothing() {
        checkNotReleased("resetSmoothing")
        // No smoothing state in Wasm body tracker yet
    }

    override fun updateSmoothing(config: SmoothingConfig) {
        checkNotReleased("updateSmoothing")
        // Smoothing not yet implemented for Wasm body tracker
    }

    override suspend fun switchCamera(facing: CameraFacing) {
        checkNotReleased("switchCamera")
        FaceLinkLogger.w(TAG, "switchCamera not yet supported on Wasm")
    }

    private fun checkNotReleased(action: String) {
        check(!released) { "Cannot $action on a released tracker" }
    }

    private companion object {
        private const val TAG = "MediaPipeWasmBodyTracker"
        private const val CPU_DELEGATE = "CPU"
        private const val DEFAULT_MODEL_URL =
            "https://storage.googleapis.com/mediapipe-models/pose_landmarker/pose_landmarker_lite/float16/latest/pose_landmarker_lite.task"
    }
}
