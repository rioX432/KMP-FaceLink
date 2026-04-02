package io.github.kmpfacelink.internal.onnx

import io.github.kmpfacelink.api.FaceTracker
import io.github.kmpfacelink.api.TrackingState
import io.github.kmpfacelink.internal.camera.JavaCvCameraManager
import io.github.kmpfacelink.model.CameraFacing
import io.github.kmpfacelink.model.FaceTrackerConfig
import io.github.kmpfacelink.model.FaceTrackingData
import io.github.kmpfacelink.model.SmoothingConfig
import io.github.kmpfacelink.util.FaceLinkLogger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * JVM/Desktop face tracker using ONNX Runtime for inference
 * and JavaCV for camera access.
 *
 * Uses OpenSeeFace ONNX models (face detection + 66 landmarks)
 * to produce ARKit-compatible blend shape data.
 */
internal class OnnxFaceTracker(
    private val config: FaceTrackerConfig,
) : FaceTracker {

    private val _trackingData = MutableSharedFlow<FaceTrackingData>(replay = 1)
    override val trackingData: Flow<FaceTrackingData> = _trackingData

    private val _state = MutableStateFlow(TrackingState.IDLE)
    override val state: StateFlow<TrackingState> = _state

    private val _errorMessage = MutableStateFlow<String?>(null)
    override val errorMessage: StateFlow<String?> = _errorMessage

    private var scope: CoroutineScope? = null
    private var trackingJob: Job? = null
    private var released = false

    private val modelLoader = OnnxModelLoader()
    private var cameraManager: JavaCvCameraManager? = null
    private var detector: FaceDetector? = null
    private var landmarkInference: LandmarkInference? = null

    override suspend fun start() {
        checkNotReleased()
        if (_state.value == TrackingState.TRACKING || _state.value == TrackingState.STARTING) return

        _state.value = TrackingState.STARTING
        _errorMessage.value = null

        try {
            initializeModels()
            initializeCamera()
            startTrackingLoop()
        } catch (expected: Exception) {
            if (expected is CancellationException) throw expected
            handleError("Failed to start tracking: ${expected.message}", expected)
        }
    }

    override suspend fun stop() {
        trackingJob?.cancel()
        trackingJob = null
        cameraManager?.stop()
        cameraManager = null
        if (_state.value != TrackingState.RELEASED) {
            _state.value = TrackingState.STOPPED
        }
    }

    override fun release() {
        released = true
        trackingJob?.cancel()
        trackingJob = null
        cameraManager?.stop()
        cameraManager = null
        modelLoader.release()
        detector = null
        landmarkInference = null
        scope?.cancel()
        scope = null
        _state.value = TrackingState.RELEASED
    }

    override fun resetCalibration() {
        checkNotReleased()
        // No calibration state in the ONNX tracker yet
    }

    override fun updateSmoothing(config: SmoothingConfig) {
        checkNotReleased()
        // Smoothing is applied via the common BlendShapeSmoother in a future enhancement
    }

    override suspend fun switchCamera(facing: CameraFacing) {
        checkNotReleased()
        // Desktop typically has only one camera; no-op for now
        FaceLinkLogger.w(TAG, "Camera switching not supported on JVM desktop")
    }

    private fun initializeModels() {
        modelLoader.loadModels()
        detector = FaceDetector(modelLoader.detection)
        landmarkInference = LandmarkInference(modelLoader.landmark)
    }

    private fun initializeCamera() {
        val cam = JavaCvCameraManager(
            requestedWidth = config.analysisWidth,
            requestedHeight = config.analysisHeight,
        )
        cam.start()
        cameraManager = cam
    }

    private fun startTrackingLoop() {
        val trackingScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        scope = trackingScope
        trackingJob = trackingScope.launch {
            _state.value = TrackingState.TRACKING
            runTrackingLoop()
        }
    }

    private fun handleError(message: String, cause: Exception? = null) {
        FaceLinkLogger.e(TAG, message, cause)
        _state.value = TrackingState.ERROR
        _errorMessage.value = message
    }

    // ── Accessors for the TrackingLoop extension function ──

    fun getCameraManager(): JavaCvCameraManager? = cameraManager
    fun getDetector(): FaceDetector? = detector
    fun getLandmarkInference(): LandmarkInference? = landmarkInference

    suspend fun emitTrackingData(data: FaceTrackingData) {
        _trackingData.emit(data)
    }

    private fun checkNotReleased() {
        check(!released) { "FaceTracker has been released" }
    }

    companion object {
        private const val TAG = "OnnxFaceTracker"
    }
}
