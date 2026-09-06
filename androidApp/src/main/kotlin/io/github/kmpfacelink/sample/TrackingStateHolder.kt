package io.github.kmpfacelink.sample

import android.util.Log
import io.github.kmpfacelink.api.FaceTracker
import io.github.kmpfacelink.api.HandTracker
import io.github.kmpfacelink.api.HolisticTracker
import io.github.kmpfacelink.api.PlatformContext
import io.github.kmpfacelink.api.TrackingState
import io.github.kmpfacelink.api.createFaceTracker
import io.github.kmpfacelink.api.createHandTracker
import io.github.kmpfacelink.api.createHolisticTracker
import io.github.kmpfacelink.model.FaceTrackerConfig
import io.github.kmpfacelink.model.FaceTrackingData
import io.github.kmpfacelink.model.HandTrackerConfig
import io.github.kmpfacelink.model.HandTrackingData
import io.github.kmpfacelink.model.HolisticTrackerConfig
import io.github.kmpfacelink.model.HolisticTrackingData
import io.github.kmpfacelink.model.SmoothingConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

private const val TAG = "TrackingStateHolder"

/**
 * Manages tracker instances, state flows, and tracking lifecycle.
 * Extracted from MainActivity to separate tracking logic from UI.
 */
@Suppress("TooManyFunctions")
internal class TrackingStateHolder(
    platformContext: PlatformContext,
    private val scope: CoroutineScope,
) {
    val faceSmoothingConfig: SmoothingConfig = SmoothingConfig.OneEuro()

    val faceTracker: FaceTracker by lazy {
        createFaceTracker(
            platformContext = platformContext,
            config = FaceTrackerConfig(smoothingConfig = faceSmoothingConfig),
        )
    }

    /**
     * A deliberately unprocessed lane for conference/demo evidence.
     *
     * Keeping this tracker separate prevents the normal sample's smoothing choice from being
     * changed just to make a recording. The default enhancer and calibration configs are both
     * disabled, so the evidence screen shows the estimator output after name mapping only.
     */
    val faceEvidenceTracker: FaceTracker by lazy {
        createFaceTracker(
            platformContext = platformContext,
            config = FaceTrackerConfig(smoothingConfig = SmoothingConfig.None),
        )
    }

    val handTracker: HandTracker by lazy {
        createHandTracker(
            platformContext = platformContext,
            config = HandTrackerConfig(smoothingConfig = SmoothingConfig.OneEuro()),
        )
    }

    val holisticTracker: HolisticTracker by lazy {
        createHolisticTracker(
            platformContext = platformContext,
            config = HolisticTrackerConfig(
                faceConfig = FaceTrackerConfig(smoothingConfig = SmoothingConfig.OneEuro()),
                handConfig = HandTrackerConfig(smoothingConfig = SmoothingConfig.OneEuro()),
            ),
        )
    }

    val faceTrackingDataState = MutableStateFlow<FaceTrackingData?>(null)
    val faceEvidenceTrackingDataState = MutableStateFlow<FaceTrackingData?>(null)
    val handTrackingDataState = MutableStateFlow<HandTrackingData?>(null)
    val holisticTrackingDataState = MutableStateFlow<HolisticTrackingData?>(null)
    val trackingModeState = MutableStateFlow(TrackingMode.HOME)
    val errorState = MutableStateFlow<String?>(null)

    private var faceCollectorJob: Job? = null
    private var faceEvidenceCollectorJob: Job? = null
    private var handCollectorJob: Job? = null
    private var holisticCollectorJob: Job? = null

    fun switchMode(newMode: TrackingMode) {
        val currentMode = trackingModeState.value
        if (currentMode == newMode) return
        errorState.value = null

        if (isCurrentlyTracking(currentMode)) {
            scope.launch {
                stopCurrentTracker(currentMode)
                trackingModeState.value = newMode
                startTracking()
            }
        } else {
            trackingModeState.value = newMode
        }
    }

    fun startTracking() {
        errorState.value = null
        scope.launch {
            @Suppress("TooGenericExceptionCaught")
            try {
                startTrackingForMode(trackingModeState.value)
            } catch (e: Exception) {
                // Tracker start can fail with various exceptions from MediaPipe/CameraX
                errorState.value = e.message ?: "Failed to start tracking"
                Log.e(TAG, "Failed to start tracking", e)
            }
        }
    }

    fun stopTracking() {
        scope.launch { stopCurrentTracker(trackingModeState.value) }
    }

    fun stopIfTracking() {
        val mode = trackingModeState.value
        if (isCurrentlyTracking(mode)) {
            scope.launch { stopCurrentTracker(mode) }
        }
    }

    fun release() {
        faceTracker.release()
        faceEvidenceTracker.release()
        handTracker.release()
        holisticTracker.release()
    }

    private fun isCurrentlyTracking(mode: TrackingMode): Boolean = when (mode) {
        TrackingMode.HOME, TrackingMode.INTERACT -> false
        TrackingMode.FACE, TrackingMode.ACTIONS,
        TrackingMode.VOICE,
        -> faceTracker.state.value == TrackingState.TRACKING
        TrackingMode.FACE_EVIDENCE -> faceEvidenceTracker.state.value == TrackingState.TRACKING
        TrackingMode.HAND -> handTracker.state.value == TrackingState.TRACKING
        TrackingMode.HOLISTIC -> holisticTracker.state.value == TrackingState.TRACKING
    }

    @Suppress("TooGenericExceptionCaught")
    private suspend fun stopCurrentTracker(mode: TrackingMode) {
        try {
            when (mode) {
                TrackingMode.HOME, TrackingMode.INTERACT -> { /* no tracker to stop */ }
                TrackingMode.FACE -> stopFaceTracker()
                TrackingMode.FACE_EVIDENCE -> stopFaceEvidenceTracker()
                TrackingMode.HAND -> stopHandTracker()
                TrackingMode.HOLISTIC -> stopHolisticTracker()
                TrackingMode.ACTIONS, TrackingMode.VOICE -> stopFaceTracker()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Best-effort stop: tracker may fail during shutdown (e.g., already released)
            Log.w(TAG, "Error stopping tracker", e)
        }
    }

    private suspend fun stopFaceTracker() {
        faceCollectorJob?.cancel()
        faceTracker.stop()
    }

    private suspend fun stopFaceEvidenceTracker() {
        faceEvidenceCollectorJob?.cancel()
        faceEvidenceTracker.stop()
    }

    private suspend fun stopHandTracker() {
        handCollectorJob?.cancel()
        handTracker.stop()
    }

    private suspend fun stopHolisticTracker() {
        holisticCollectorJob?.cancel()
        holisticTracker.stop()
    }

    private suspend fun startTrackingForMode(mode: TrackingMode) {
        when (mode) {
            TrackingMode.HOME, TrackingMode.INTERACT -> { /* no tracking needed */ }
            TrackingMode.FACE -> startFaceTracking()
            TrackingMode.FACE_EVIDENCE -> startFaceEvidenceTracking()
            TrackingMode.HAND -> startHandTracking()
            TrackingMode.HOLISTIC -> startHolisticTracking()
            TrackingMode.ACTIONS, TrackingMode.VOICE -> startFaceTracking()
        }
    }

    private suspend fun startFaceTracking() {
        faceCollectorJob = launchTrackerCollection(faceTracker.trackingData, faceTrackingDataState)
        faceTracker.start()
    }

    private suspend fun startFaceEvidenceTracking() {
        faceEvidenceCollectorJob = launchTrackerCollection(
            faceEvidenceTracker.trackingData,
            faceEvidenceTrackingDataState,
        )
        faceEvidenceTracker.start()
    }

    private suspend fun startHandTracking() {
        handCollectorJob = launchTrackerCollection(handTracker.trackingData, handTrackingDataState)
        handTracker.start()
    }

    private suspend fun startHolisticTracking() {
        holisticCollectorJob = launchTrackerCollection(holisticTracker.trackingData, holisticTrackingDataState)
        holisticTracker.start()
    }

    private fun <T> launchTrackerCollection(
        trackingFlow: Flow<T>,
        state: MutableStateFlow<T?>,
    ): Job = scope.launch {
        trackingFlow.collect { state.value = it }
    }
}
